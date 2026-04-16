# Guía de Integración iOS

[← Volver al README](README.md)

![Platform](https://img.shields.io/badge/platform-iOS-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple)

> Esta guía cubre **exclusivamente el SDK de dominio** y cómo usarlo desde un proyecto iOS (Swift).
> No cubre implementaciones de capa de datos (SQLDelight, CoreData, Ktor) ni vistas SwiftUI.

---

## Tabla de Contenidos

- [Exportar framework KMP](#paso-1--exportar-framework-kmp)
- [Contratos del SDK](#paso-2--contratos-del-sdk)
  - [Tipos de Use Case](#tipos-de-use-case)
  - [DomainResult](#domainresult)
  - [DomainError](#domainerror)
  - [Modelo de dominio](#modelo-de-dominio)
  - [Contratos de Repository y Gateway](#contratos-de-repository-y-gateway)
  - [Validadores](#validadores)
  - [Políticas de dominio](#políticas-de-dominio)
  - [Providers](#providers)
  - [DomainDependencies](#domaindependencies)
- [Cómo se exponen los tipos en Swift](#paso-3--cómo-se-exponen-los-tipos-en-swift)
- [Implementar un Use Case](#paso-4--implementar-un-use-case)
- [Validación de input](#paso-5--validación-de-input)
- [Políticas de negocio](#paso-6--políticas-de-negocio)
- [Definir un Repository contract](#paso-7--definir-un-repository-contract)
- [Cablear DomainDependencies para iOS](#paso-8--cablear-domaindependencies-para-ios)
- [Inyectar Use Cases en el ViewModel](#paso-9--inyectar-use-cases-en-el-viewmodel)
- [Testing de Use Cases](#paso-10--testing-de-use-cases)
- [FAQ](#faq)

---

## Paso 1 — Exportar framework KMP

Para que iOS pueda consumir el SDK, el módulo KMP debe exportar un framework:

```kotlin
// shared/build.gradle.kts
kotlin {
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "SharedDomain"
            isStatic = true
        }
    }
}
```

Esto genera un framework que Swift importa con `import SharedDomain`.

---

## Paso 2 — Contratos del SDK

### Tipos de Use Case

El SDK provee 5 contratos de caso de uso. Todos son `fun interface` (SAM):

| Contrato | Firma (Kotlin) | Cuándo usar |
|---|---|---|
| `PureUseCase<I, O>` | `(I) → DomainResult<O>` | Lógica síncrona y pura. Sin I/O |
| `SuspendUseCase<I, O>` | `suspend (I) → DomainResult<O>` | Operaciones async de resultado único |
| `FlowUseCase<I, O>` | `(I) → Flow<DomainResult<O>>` | Observar cambios en el tiempo |
| `NoParamsUseCase<O>` | `suspend () → DomainResult<O>` | Variante sin parámetros |
| `NoParamsFlowUseCase<O>` | `() → Flow<DomainResult<O>>` | Variante reactiva sin parámetros |

Todos retornan `DomainResult<T>`, nunca lanzan excepciones al consumidor.

### DomainResult

Tipo sealed que modela éxito o fallo **sin excepciones**:

```kotlin
sealed class DomainResult<out T> {
    data class Success<out T>(val value: T) : DomainResult<T>()
    data class Failure(val error: DomainError) : DomainResult<Nothing>()
}
```

**Constructores:**
```kotlin
val ok = myValue.asSuccess()               // → Success(myValue)
val fail = domainFailure(DomainError.NotFound(...))  // → Failure(...)
```

**Operadores funcionales:**
```kotlin
result
    .map { task -> task.title }              // transforma el valor si es Success
    .flatMap { title -> validateTitle(title) } // encadena otro DomainResult
    .mapError { error -> enrichError(error) } // transforma el error si es Failure
    .onSuccess { value -> log(value) }       // efecto lateral si Success
    .onFailure { error -> track(error) }     // efecto lateral si Failure
```

**Extracción:**
```kotlin
result.getOrNull()                    // T? — null si Failure
result.errorOrNull()                  // DomainError? — null si Success
result.getOrElse { error -> default } // T — usa default si Failure
```

**Combinación con `zip`:**
```kotlin
validatedName.zip(validatedEmail) { name, email ->
    CreateUserParams(name, email)
}
```

**Captura segura con `runDomainCatching`:**
```kotlin
suspend fun <T> runDomainCatching(
    errorMapper: (Throwable) -> DomainError = { DomainError.Unknown(cause = it) },
    block: suspend () -> T,
): DomainResult<T>
```
- Atrapa todo excepto `CancellationException` (preserva concurrencia estructurada)
- Convierte excepciones a `DomainError` usando el mapper

### DomainError

Jerarquía sealed con 6 subtipos concretos:

| Subtipo | Campos | Semántica |
|---|---|---|
| `Validation` | `field`, `detail` | Input no satisface invariantes de dominio |
| `NotFound` | `resourceType`, `id` | Recurso/agregado no existe |
| `Unauthorized` | `detail` | Operación no autorizada |
| `Conflict` | `detail` | Conflicto con estado actual (duplicado, transición inválida) |
| `Infrastructure` | `detail`, `cause?` | Fallo de dependencia externa |
| `Unknown` | `detail`, `cause?` | Catch-all para condiciones inesperadas |

Todos heredan `message: String`. Solo `Infrastructure` y `Unknown` exponen `cause: Throwable?`.

### Modelo de dominio

Tres interfaces marker para modelar DDD:

| Interfaz | Propósito |
|---|---|
| `Entity<ID>` | Objeto con identidad (`val id: ID`). Igualdad por `id` |
| `ValueObject` | Objeto inmutable sin identidad. Igualdad estructural |
| `AggregateRoot<ID>` | Entidad que es raíz de consistencia. Los repositories operan contra estos |

```kotlin
// Value Object
data class TaskId(val value: String) : ValueObject

// Aggregate Root
data class Task(
    override val id: TaskId,
    val title: String,
    val completed: Boolean,
    val createdAt: Long,
) : AggregateRoot<TaskId>
```

### Contratos de Repository y Gateway

**Repository** — abstracción sobre persistencia:

| Contrato | Métodos |
|---|---|
| `Repository` | Marker puro (sin métodos) |
| `ReadRepository<ID, T>` | `suspend findById(id): DomainResult<T?>` |
| `ReadCollectionRepository<T>` | `observeAll(): Flow<DomainResult<List<T>>>` |
| `WriteRepository<T>` | `suspend save(entity): DomainResult<Unit>`, `suspend delete(entity): DomainResult<Unit>` |

**Gateway** — abstracción sobre capacidades externas (no persistencia):

| Contrato | Métodos |
|---|---|
| `Gateway` | Marker puro |
| `SuspendGateway<I, O>` | `suspend execute(input): DomainResult<O>` |
| `CommandGateway<I>` | `suspend dispatch(input): DomainResult<Unit>` |

> **El dominio define la interfaz; la capa de datos la implementa.**
> El SDK nunca contiene implementaciones de repositories o gateways.

### Validadores

`Validator<T>` es un `fun interface` para validación síncrona y pura:

```kotlin
fun interface Validator<in T> {
    fun validate(value: T): DomainResult<Unit>
}
```

**Validadores primitivos incluidos:**
- `notBlankValidator(field)` — String no vacío
- `maxLengthValidator(field, max)` — longitud máxima
- `minLengthValidator(field, min)` — longitud mínima
- `rangeValidator(field, min, max)` — rango para Comparable

**Composición:**
```kotlin
// Fail-fast
val titleValidator = notBlankValidator("title")
    .andThen(maxLengthValidator("title", 200))

// Acumula todos los errores
val errors: List<DomainError> = collectValidationErrors(title, validators)
```

### Políticas de dominio

`DomainPolicy<C>` modela **reglas de negocio semánticas**:

```kotlin
fun interface DomainPolicy<in C> {
    fun evaluate(context: C): DomainResult<Unit>
}

fun interface SuspendDomainPolicy<in C> {
    suspend fun evaluate(context: C): DomainResult<Unit>
}
```

Combinadores: `and`, `or`, `negate`.

### Providers

| Provider | Método | Propósito |
|---|---|---|
| `ClockProvider` | `nowMillis(): Long` | Obtener tiempo actual (epoch ms) |
| `IdProvider` | `next(): String` | Generar ID único |

El dominio nunca llama a APIs de plataforma directamente.

### DomainDependencies

Contenedor inmutable para providers cross-cutting:

```kotlin
data class DomainDependencies(
    val clock: ClockProvider,
    val idProvider: IdProvider,
)
```

---

## Paso 3 — Cómo se exponen los tipos en Swift

Kotlin/Native genera clases Swift para cada tipo del SDK. Las convenciones de nombrado son:

| Kotlin | Swift |
|---|---|
| `DomainResult.Success<T>` | `DomainResultSuccess<T>` |
| `DomainResult.Failure` | `DomainResultFailure` |
| `DomainError.Validation` | `DomainErrorValidation` |
| `DomainError.NotFound` | `DomainErrorNotFound` |
| `DomainError.Unauthorized` | `DomainErrorUnauthorized` |
| `DomainError.Conflict` | `DomainErrorConflict` |
| `DomainError.Infrastructure` | `DomainErrorInfrastructure` |
| `DomainError.Unknown` | `DomainErrorUnknown` |
| `suspend fun` | `async func` (Kotlin 2.0+) |
| `Flow<T>` | `Kotlinx_coroutines_coreFlow` (usar SKIE o KMP-NativeCoroutines) |

**Distinguir éxito de fallo en Swift:**
```swift
let result = try? await useCase.invoke(params: params)

if let success = result as? DomainResultSuccess<Task> {
    let task = success.value  // acceso al valor
} else if let failure = result as? DomainResultFailure {
    let error = failure.error // acceso al DomainError
    if let validation = error as? DomainErrorValidation {
        print(validation.field, validation.detail)
    }
}
```

**Manejo exhaustivo de errores:**
```swift
func handleError(_ error: DomainError) {
    switch error {
    case let validation as DomainErrorValidation:
        showFieldError(validation.field, validation.detail)
    case let notFound as DomainErrorNotFound:
        showNotFound(notFound.resourceType)
    case is DomainErrorUnauthorized:
        navigateToLogin()
    case let conflict as DomainErrorConflict:
        showConflict(conflict.detail)
    case is DomainErrorInfrastructure:
        showRetry()
    default:
        showGenericError()
    }
}
```

---

## Paso 4 — Implementar un Use Case

Los use cases se implementan en Kotlin (en el módulo compartido), no en Swift:

```kotlin
class CreateTaskUseCase(
    private val deps: DomainDependencies,
    private val taskRepository: TaskRepository,
    private val titleValidator: Validator<String>,
) : SuspendUseCase<CreateTaskParams, Task> {

    override suspend fun invoke(params: CreateTaskParams): DomainResult<Task> {
        // 1. Validar input
        titleValidator.validate(params.title).let { result ->
            if (result.isFailure) return result as DomainResult.Failure
        }

        // 2. Construir agregado
        val task = Task(
            id = TaskId(deps.idProvider.next()),
            title = params.title.trim(),
            completed = false,
            createdAt = deps.clock.nowMillis(),
        )

        // 3. Persistir
        return taskRepository.save(task).map { task }
    }
}

data class CreateTaskParams(val title: String)
```

**Puntos clave:**
- El use case recibe `DomainDependencies` + contratos de repository por constructor
- Usa `Validator` para validación de input
- Usa `ClockProvider` e `IdProvider` — nunca APIs de plataforma
- Retorna `DomainResult<Task>` — nunca lanza excepciones

---

## Paso 5 — Validación de input

```kotlin
// Componer validadores con andThen (fail-fast)
val titleValidator = notBlankValidator("title")
    .andThen(maxLengthValidator("title", 200))

// Dentro del use case:
titleValidator.validate(params.title).let { result ->
    if (result.isFailure) return result as DomainResult.Failure
}

// O acumular todos los errores:
val errors = collectValidationErrors(params.title, listOf(
    notBlankValidator("title"),
    minLengthValidator("title", 3),
    maxLengthValidator("title", 200),
))
```

---

## Paso 6 — Políticas de negocio

```kotlin
val canCompleteTask = DomainPolicy<Task> { task ->
    if (!task.completed) Unit.asSuccess()
    else domainFailure(DomainError.Conflict(detail = "Tarea ya completada"))
}

// Componer:
val canModifyTask = isNotCompletedPolicy and isNotArchivedPolicy
```

---

## Paso 7 — Definir un Repository contract

```kotlin
// Dentro del dominio:
interface TaskRepository : ReadRepository<TaskId, Task>, WriteRepository<Task> {
    suspend fun findByStatus(completed: Boolean): DomainResult<List<Task>>
}

// La capa de datos implementa (fuera del SDK).
// El SDK nunca conoce SQLDelight, CoreData, ni ninguna tecnología concreta.
```

---

## Paso 8 — Cablear DomainDependencies para iOS

En el código `iosMain` de tu módulo KMP compartido:

```kotlin
// shared/src/iosMain/kotlin/PlatformProviders.kt
import com.domain.core.provider.ClockProvider
import com.domain.core.provider.IdProvider
import platform.Foundation.NSDate
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970

val iosClock: ClockProvider = ClockProvider {
    (NSDate().timeIntervalSince1970 * 1000).toLong()
}

val iosIdProvider: IdProvider = IdProvider {
    NSUUID().UUIDString
}
```

Luego crea un factory que Swift pueda invocar fácilmente:

```kotlin
// shared/src/iosMain/kotlin/ModuleFactory.kt
object SharedModuleFactory {

    private val domainDeps = DomainDependencies(
        clock = iosClock,
        idProvider = iosIdProvider,
    )

    fun createTaskModule(taskRepository: TaskRepository): TaskDomainModule =
        TaskDomainModuleImpl(deps = domainDeps, taskRepository = taskRepository)
}
```

> **¿Por qué un factory?** Swift no puede invocar directamente constructores Kotlin con
> genéricos complejos. El factory expone una API limpia para Swift.

---

## Paso 9 — Inyectar Use Cases en el ViewModel

El ViewModel de Swift recibe **solo los contratos del SDK**, nunca implementaciones concretas:

```swift
import SharedDomain

@MainActor
class TaskViewModel: ObservableObject {
    @Published var state: TaskState = .idle

    private let createTask: SuspendUseCase // tipado del SDK

    init(taskModule: TaskDomainModule) {
        self.createTask = taskModule.createTask
    }

    func onCreateTask(title: String) async {
        state = .loading

        let params = CreateTaskParams(title: title)
        let result = try? await createTask.invoke(params: params)

        if let success = result as? DomainResultSuccess<Task> {
            state = .success(success.value)
        } else if let failure = result as? DomainResultFailure {
            handleError(failure.error)
        }
    }

    private func handleError(_ error: DomainError) {
        if let validation = error as? DomainErrorValidation {
            state = .validationError(field: validation.field, detail: validation.detail)
        } else {
            state = .error(error.message)
        }
    }
}
```

El ViewModel **no sabe** cómo se implementan los use cases, ni qué base de datos usan.
Solo conoce los contratos del SDK.

---

## Paso 10 — Testing de Use Cases

Los tests se escriben en Kotlin (`commonTest`), no en Swift:

```kotlin
class CreateTaskUseCaseTest {

    private val fixedDeps = DomainDependencies(
        clock = ClockProvider { 1_700_000_000_000L },
        idProvider = IdProvider { "test-id-1" },
    )

    // Stub simple — sin framework de mocking
    private val fakeRepo = object : TaskRepository {
        val saved = mutableListOf<Task>()
        override suspend fun save(entity: Task) =
            entity.also { saved.add(it) }.let { Unit.asSuccess() }
        override suspend fun delete(entity: Task) = Unit.asSuccess()
        override suspend fun findById(id: TaskId) = saved.find { it.id == id }.asSuccess()
        override suspend fun findByStatus(completed: Boolean) =
            saved.filter { it.completed == completed }.asSuccess()
    }

    private val useCase = CreateTaskUseCase(
        deps = fixedDeps,
        taskRepository = fakeRepo,
        titleValidator = notBlankValidator("title")
            .andThen(maxLengthValidator("title", 200)),
    )

    @Test
    fun `crea tarea con título válido`() = runTest {
        val result = useCase(CreateTaskParams("Mi tarea"))

        assertTrue(result.isSuccess)
        assertEquals("test-id-1", result.getOrNull()!!.id.value)
    }

    @Test
    fun `falla con título en blanco`() = runTest {
        val result = useCase(CreateTaskParams("   "))

        assertTrue(result.isFailure)
        assertTrue(result.errorOrNull() is DomainError.Validation)
    }
}
```

**Principios de testing:**
- `DomainDependencies` con valores fijos → determinismo total
- Stubs manuales → sin framework de mocking
- `runTest` de kotlinx-coroutines-test para use cases suspend

---

## FAQ

**P: ¿Cómo maneja Kotlin/Native las funciones `suspend` en Swift?**
Kotlin 2.0+ genera funciones `async` de Swift. Las llamas con `await`. No se necesitan wrappers manuales.

**P: ¿Cómo se exponen las sealed classes en Swift?**
Se convierten en clases separadas. `DomainResult.Success` → `DomainResultSuccess`, etc.
Usa casts `as?` para distinguirlas.

**P: ¿Qué pasa con los `Flow` use cases?**
`Flow` se expone como `Kotlinx_coroutines_coreFlow`. Usa
[KMP-NativeCoroutines](https://github.com/nicklockwood/KMP-NativeCoroutines) o SKIE
para obtener wrappers nativos `AsyncSequence`.

**P: ¿Necesito preocuparme por el manejo de memoria?**
Kotlin/Native usa ARC para objetos expuestos a Swift. Sin manejo manual.
Evita retener objetos Kotlin en closures de larga vida.

**P: ¿El SDK soporta watchOS / tvOS / macOS?**
Sí. Agrega los targets en `build.gradle.kts`:
```kotlin
watchosArm64()
tvosArm64()
macosArm64()
```

**P: ¿El framework es grande?**
Muy pequeño — solo interfaces tipadas, sealed classes y funciones puras. Menos de 100KB típico.

**P: ¿El SDK contiene implementaciones de Repository?**
No. Solo contratos (interfaces). La capa de datos provee las implementaciones.

**P: ¿Cuál es la diferencia entre `Validator` y `DomainPolicy`?**
`Validator` = restricciones sintácticas (no blank, max length).
`DomainPolicy` = reglas de negocio semánticas (contexto de agregado/estado).

---

<details>
<summary><h2>🇺🇸 English Version</h2></summary>

> This guide covers **exclusively the domain SDK** and how to use it from an iOS (Swift) project.
> It does not cover data layer implementations (SQLDelight, CoreData, Ktor) or SwiftUI views.

---

## Step 1 — KMP framework export

```kotlin
// shared/build.gradle.kts
kotlin {
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "SharedDomain"
            isStatic = true
        }
    }
}
```

---

## Step 2 — SDK Contracts

### Use Case types

5 `fun interface` contracts: `PureUseCase`, `SuspendUseCase`, `FlowUseCase`, `NoParamsUseCase`, `NoParamsFlowUseCase`.

### DomainResult

Sealed type: `Success<T>` / `Failure(error: DomainError)`.
Operators: `map`, `flatMap`, `mapError`, `onSuccess`, `onFailure`, `getOrNull`, `getOrElse`, `zip`, `runDomainCatching`.

### DomainError

6 subtypes: `Validation`, `NotFound`, `Unauthorized`, `Conflict`, `Infrastructure`, `Unknown`.

### Domain Model

`Entity<ID>`, `ValueObject`, `AggregateRoot<ID>`.

### Repository & Gateway contracts

Repositories: `ReadRepository`, `ReadCollectionRepository`, `WriteRepository`.
Gateways: `SuspendGateway`, `CommandGateway`. SDK defines interfaces only.

### Validators & Policies

`Validator<T>` (syntactic). `DomainPolicy<C>` / `SuspendDomainPolicy<C>` (semantic).

### Providers & DomainDependencies

`ClockProvider`, `IdProvider`, grouped in `DomainDependencies`.

---

## Step 3 — How types are exposed in Swift

| Kotlin | Swift |
|---|---|
| `DomainResult.Success<T>` | `DomainResultSuccess<T>` |
| `DomainResult.Failure` | `DomainResultFailure` |
| `DomainError.Validation` | `DomainErrorValidation` |
| `suspend fun` | `async func` (Kotlin 2.0+) |
| `Flow<T>` | `Kotlinx_coroutines_coreFlow` (use SKIE or KMP-NativeCoroutines) |

---

## Step 4 — Implement a Use Case (in Kotlin)

```kotlin
class CreateTaskUseCase(
    private val deps: DomainDependencies,
    private val taskRepository: TaskRepository,
    private val titleValidator: Validator<String>,
) : SuspendUseCase<CreateTaskParams, Task> {
    override suspend fun invoke(params: CreateTaskParams): DomainResult<Task> {
        titleValidator.validate(params.title).let { if (it.isFailure) return it as DomainResult.Failure }
        val task = Task(
            id = TaskId(deps.idProvider.next()),
            title = params.title.trim(),
            completed = false,
            createdAt = deps.clock.nowMillis(),
        )
        return taskRepository.save(task).map { task }
    }
}
```

---

## Step 5 — Wire DomainDependencies for iOS

```kotlin
// shared/src/iosMain/kotlin/PlatformProviders.kt
val iosClock: ClockProvider = ClockProvider {
    (NSDate().timeIntervalSince1970 * 1000).toLong()
}
val iosIdProvider: IdProvider = IdProvider { NSUUID().UUIDString }
```

Factory for Swift consumption:

```kotlin
object SharedModuleFactory {
    private val domainDeps = DomainDependencies(clock = iosClock, idProvider = iosIdProvider)
    fun createTaskModule(taskRepository: TaskRepository): TaskDomainModule =
        TaskDomainModuleImpl(deps = domainDeps, taskRepository = taskRepository)
}
```

---

## Step 6 — Inject Use Cases into the ViewModel

```swift
@MainActor
class TaskViewModel: ObservableObject {
    private let createTask: SuspendUseCase

    init(taskModule: TaskDomainModule) {
        self.createTask = taskModule.createTask
    }

    func onCreateTask(title: String) async {
        let result = try? await createTask.invoke(params: CreateTaskParams(title: title))
        if let success = result as? DomainResultSuccess<Task> {
            // handle success
        } else if let failure = result as? DomainResultFailure {
            // handle error
        }
    }
}
```

---

## Step 7 — Testing (in Kotlin commonTest)

```kotlin
private val fixedDeps = DomainDependencies(
    clock = ClockProvider { 1_700_000_000_000L },
    idProvider = IdProvider { "test-id-1" },
)
// Manual stubs — no mocking framework needed
```

---

## FAQ

**Q: How does Kotlin/Native handle `suspend` in Swift?** Generates `async` functions. Use `await`.

**Q: How are sealed classes exposed?** As separate classes (e.g., `DomainResultSuccess`, `DomainErrorValidation`).

**Q: What about `Flow` use cases?** Use KMP-NativeCoroutines or SKIE for `AsyncSequence` wrappers.

**Q: Memory management?** ARC. No manual management needed.

**Q: Does the SDK contain Repository implementations?** No. Only contracts.

**Q: Supports watchOS / tvOS / macOS?** Yes. Add targets in `build.gradle.kts`.

</details>
