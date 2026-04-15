# Integration Guide — Domain SDK ↔ Data SDK

This document defines the exact rules for integrating this domain SDK with an
external data layer (a separate KMP module or SDK), without violating Clean
Architecture or coupling the domain to infrastructure.

---

## 1. Dependency direction

```
App / Presentation
       │
       ▼
  Domain SDK   ◄────────── this module
       ▲
       │  (implements contracts defined here)
       │
  Data SDK / infra module
```

**The domain SDK has zero knowledge of the data SDK.**
The data SDK depends on the domain SDK — never the reverse.

The domain defines:
- `Repository` sub-interfaces (contracts)
- `Gateway` sub-interfaces (contracts)
- `DomainError` hierarchy
- `DomainResult` as the return type of every boundary operation

The data SDK provides:
- Concrete implementations of those interfaces
- Mapping from infrastructure errors → `DomainError`
- Mapping from persistence/network types → domain model types

---

## 2. Constructor injection — wiring pattern

There is no DI framework in this SDK. Wiring is done explicitly in the app layer
(or a dedicated `:app` / `:wiring` module).

### Step-by-step

**1. Build `DomainDependencies` once, at app startup:**

```kotlin
// app module — e.g. AppModule.kt or Application.onCreate()
val domainDeps = DomainDependencies(
    clock = ClockProvider { System.currentTimeMillis() },
    idProvider = IdProvider { UUID.randomUUID().toString() },
)
```

**2. Build data-layer implementations of domain contracts:**

```kotlin
// data module — UserRepositoryImpl implements the domain contract
val db: AppDatabase = Room.databaseBuilder(...).build()
val userRepository: UserRepository = UserRepositoryImpl(db.userDao())
```

**3. Assemble the feature domain module:**

```kotlin
// app wiring — explicit constructor call, no magic
val userDomainModule: UserDomainModule = UserDomainModuleImpl(
    deps = domainDeps,
    userRepository = userRepository,
)
```

**4. Inject into ViewModels / Presenters by interface:**

```kotlin
class UserViewModel(
    private val registerUser: SuspendUseCase<RegisterUserParams, User>,
    private val getUser: SuspendUseCase<UserId, User>,
) : ViewModel()

// wiring
UserViewModel(
    registerUser = userDomainModule.registerUser,
    getUser = userDomainModule.getUser,
)
```

> ViewModels receive individual use cases, not the module.
> This minimises the surface each ViewModel depends on (ISP).

---

## 3. Error mapping at the data boundary

The domain must never receive raw infrastructure exceptions.
Every repository / gateway implementation must catch all throwable types and map
them to `DomainError` **before returning**.

```kotlin
// data module — UserRepositoryImpl
override suspend fun findById(id: UserId): DomainResult<User?> =
    runDomainCatching(
        errorMapper = { throwable ->
            when (throwable) {
                is SQLiteException -> DomainError.Infrastructure(
                    detail = "Database read failed",
                    cause = throwable,
                )
                else -> DomainError.Unknown(cause = throwable)
            }
        }
    ) {
        dao.findById(id.value)?.toDomain()
    }
```

`runDomainCatching` is provided by the domain SDK in `com.domain.core.result`.
The data layer imports it — the domain layer never needs to catch anything.

---

## 4. Model mapping — direction and ownership

| Concern | Owned by |
|---|---|
| Domain model (`User`, `Order`, …) | Domain SDK / feature domain |
| Persistence entity (`UserEntity`) | Data SDK |
| Network DTO (`UserDto`) | Data SDK |
| `UserEntity → User` mapper | Data SDK |
| `UserDto → User` mapper | Data SDK |
| `User → UserEntity` mapper | Data SDK |

**The domain model is never a data class that mirrors a network/DB schema.**
Mappers live entirely in the data layer and are the single point of translation.

---

## 5. What the data SDK must NOT do

| Forbidden | Reason |
|---|---|
| Import any UI / presentation class | Breaks layer isolation |
| Throw exceptions across the domain boundary | Use `runDomainCatching` or map explicitly |
| Add `@Serializable` / `@Entity` to domain models | Contaminates domain with framework annotations |
| Call `ClockProvider.nowMillis()` from the data layer | Time is a domain dependency; pass it in |
| Implement `DomainPolicy` or `Validator` | Business logic belongs in the domain |
| Return `null` where `DomainResult` is expected | Use `DomainResult.Success(null)` explicitly |

---

## 6. Test strategy at the integration boundary

Because all boundaries are interfaces, no real database or network is needed for
domain-layer tests.

```kotlin
// domain test — stub repository in 3 lines
val fakeRepo = object : UserRepository {
    override suspend fun findById(id: UserId) = fakeUser.asSuccess()
    override suspend fun save(entity: User) = Unit.asSuccess()
    override suspend fun delete(entity: User) = Unit.asSuccess()
}

val useCase = RegisterUserUseCase(
    deps = DomainDependencies(
        clock = ClockProvider { 1_700_000_000_000L },
        idProvider = IdProvider { "user-1" },
    ),
    userRepository = fakeRepo,
)
```

Data-layer integration tests (Room, Ktor) run against real infra in their own
module and never import domain test utilities.

---

## 7. Multi-platform wiring notes

| Platform | `ClockProvider` impl | `IdProvider` impl |
|---|---|---|
| JVM / Android | `System.currentTimeMillis()` | `java.util.UUID.randomUUID().toString()` |
| iOS (Kotlin/Native) | `NSDate().timeIntervalSince1970 * 1000` | `platform.Foundation.NSUUID().UUIDString` |
| JS | `Date.now()` | `crypto.randomUUID()` |

Provide these implementations in `expect`/`actual` declarations inside your
data/infra module, not in the domain SDK. The domain SDK remains platform-agnostic.

---

## 8. Versioning contract

This SDK follows semantic versioning:
- **PATCH**: bug fixes, doc updates — safe to update
- **MINOR**: new contracts added, no existing contracts changed — safe to update
- **MAJOR**: breaking changes to existing `public` interfaces or sealed hierarchies

Data-layer SDKs that implement domain contracts will receive compile-time errors
on MAJOR bumps — which is intentional and desirable.
