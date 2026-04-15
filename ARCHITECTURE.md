# Domain SDK — Architecture Reference

## 1. Module purpose

`coredomainplatform` is a **pure domain module** for Kotlin Multiplatform.  
It contains no infrastructure, no UI, no data-layer code.  
Everything in `commonMain` compiles and runs on all targets without platform-specific code.

---

## 2. Package structure

```
com.domain.core/
├── error/        ← typed domain error hierarchy
├── result/       ← DomainResult<T>: functional error container
├── model/        ← Entity, ValueObject, AggregateRoot, EntityId markers
├── usecase/      ← UseCase, SuspendUseCase, FlowUseCase contracts
├── repository/   ← Repository contracts (domain owns; data implements)
├── gateway/      ← Gateway contracts for non-persistence external deps
└── validation/   ← Validator<T> contract + primitive combinators
```

**Criterion for adding a new package:** only when a concept has a distinct technical
responsibility in the domain layer. Never add a package in anticipation of future use.

---

## 3. Naming conventions

| Concept            | Naming rule                              | Example                      |
|--------------------|------------------------------------------|------------------------------|
| Entity             | Noun, PascalCase                         | `Invoice`, `UserProfile`     |
| Value object       | Noun describing the concept              | `EmailAddress`, `Money`      |
| Aggregate root     | Same as entity; it IS the entity         | `Order`                      |
| Entity ID          | `<Entity>Id`                             | `OrderId`, `UserId`          |
| Use case interface | Verb phrase + use case type suffix       | `SubmitOrderUseCase`         |
| Repository iface   | `<Aggregate>Repository`                  | `OrderRepository`            |
| Gateway iface      | `<Capability>Gateway`                    | `NotificationGateway`        |
| Validator          | `<Field/Concept>Validator` (if named)    | `emailValidator`             |
| Domain error       | Use `DomainError.*` subclasses directly  | `DomainError.NotFound(...)`  |
| Params objects     | `<UseCase>Params`                        | `SubmitOrderParams`          |

---

## 4. Visibility rules

| Element                           | Visibility  | Rationale                                               |
|-----------------------------------|-------------|---------------------------------------------------------|
| All public contracts (interfaces) | `public`    | Consumed by data, DI, and presentation layers           |
| All public model types            | `public`    | Shared across module boundaries                         |
| SDK-internal helpers              | `internal`  | Implementation detail; not part of the public API       |
| Concrete use case implementations | `internal`  | Only the interface is public; impl is wired by DI       |
| Test doubles / fakes              | `internal`  | Scoped to `commonTest`; never leak into production code |

**Rule:** never expose a concrete class as `public` if an interface suffices.  
**Rule:** prefer `internal` over `private` for SDK-level implementation details that
may be shared between files in the same module.

---

## 5. Boundary rules — domain vs. data

The domain **defines** contracts. The data layer **implements** them.  
This is enforced at the dependency direction level:

```
[data module]  →  depends on  →  [domain module]
[domain module]  →  knows nothing about  →  [data module]
```

**Prohibited in domain:**
- Any `import` from a data or infrastructure module
- DTO types, network models, persistence models, mappers
- Any Android SDK, iOS SDK, or platform-specific type
- `@Serializable`, `@Entity`, `@PrimaryKey`, or any framework annotation
- Raw `Throwable` exposed as a domain return type (use `DomainResult` + `DomainError`)

**Allowed in domain:**
- `kotlinx.coroutines.flow.Flow` and `suspend` — KMP-native async primitives
- Kotlin stdlib types
- `DomainResult`, `DomainError` — defined in this module

---

## 6. Cohesion rules

- Each class or interface has **one reason to change**.
- Use cases contain **only orchestration logic** — no direct I/O, no formatting, no mapping.
- Validators are **pure functions** — no state, no I/O, deterministic.
- Repositories express **what the domain needs**, not what storage can provide.
- Gateways express **what the domain delegates**, not how infrastructure does it.

---

## 7. Performance guidelines

| Guideline                                                                 | Why                                              |
|---------------------------------------------------------------------------|--------------------------------------------------|
| Use `@JvmInline value class` for `EntityId` implementations               | Eliminates boxing on JVM/ART                     |
| Prefer `inline` on extension functions that accept lambdas                | Avoids lambda object allocation on hot paths     |
| Keep `data class` for all model types                                     | Structural equality, `copy()`, no mutable state  |
| Avoid `object` singletons in domain                                       | Prevents hidden shared state and test pollution  |
| Do not wrap `Flow` in `DomainResult` at the stream level                  | Avoids double-wrapping; emit `DomainResult<T>`   |
| Use `fun interface` for single-method contracts                           | SAM conversion avoids anonymous class overhead   |

---

## 8. Testing guidelines

- Every use case must be testable with **zero mocks** by implementing its repository/gateway
  dependencies as plain `fun interface` lambdas.
- `DomainResult` is a `sealed class` with `data class` subtypes — assert with `==`, not with
  framework matchers.
- Validators are pure: `validate(input)` → assert `DomainResult`. No coroutines needed.
- Use `kotlinx-coroutines-test` `runTest {}` for `suspend` and `Flow` use cases.
- Never use `Thread.sleep()` or real delays in tests — use `TestCoroutineScheduler`.

---

## 9. Dependency injection guidelines

- The domain layer **has no DI framework dependency**.
- All use case implementations receive their dependencies via **constructor injection**.
- The consumer (app module) wires dependencies using whatever DI framework it chooses
  (Koin, Hilt, manual, etc.).
- Domain contracts are interfaces → any DI framework can bind them.
- Never use `lateinit var` for domain dependencies — prefer `val` constructor params.

---

## 10. Anti-patterns to avoid

| Anti-pattern                          | Why it's harmful                                              |
|---------------------------------------|---------------------------------------------------------------|
| `BaseUseCase` abstract class          | Adds coupling without value; `fun interface` is sufficient    |
| `UseCaseExecutor` / `Interactor` base | Ceremony without benefit; use direct `invoke` operator        |
| Repository returning DTOs             | DTOs belong to data; repositories return domain models only   |
| `DomainManager` / `DomainService` god class | Violates SRP; split into focused use cases              |
| Mutable entity properties             | Breaks immutability; use `copy()` to produce new state        |
| Throwing exceptions across boundaries | Use `DomainResult`; exceptions escape type-safe control flow  |
| Feature packages in core domain       | Core is technical, not feature-oriented                       |
| Empty packages created "for later"    | Adds noise; create when the type is implemented               |
| Sharing `internal` impl across layers | `internal` is module-scoped; never reference from other modules|
| Global `object` holding domain state  | Singleton state kills testability and concurrency safety       |
