# Guía de Integración Android

[← Volver al README](README.md)

![Platform](https://img.shields.io/badge/platform-Android-green)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple)

> Esta guía cubre **exclusivamente el SDK de dominio** y cómo usarlo en un proyecto Android.
> No cubre implementaciones de capa de datos (Room, Ktor, Retrofit) ni UI (Compose, XML).

---

## Tabla de Contenidos

- [Dependencia de Gradle](#paso-1--dependencia-de-gradle)
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
- [Implementar un Use Case](#paso-3--implementar-un-use-case)
- [Validación de input](#paso-4--validación-de-input)
- [Políticas de negocio](#paso-5--políticas-de-negocio)
- [Definir un Repository contract](#paso-6--definir-un-repository-contract)
- [Cablear DomainDependencies](#paso-7--cablear-domaindependencies)
- [Inyectar Use Cases en el ViewModel](#paso-8--inyectar-use-cases-en-el-viewmodel)
- [Testing de Use Cases](#paso-9--testing-de-use-cases)
- [FAQ](#faq)

---

## Paso 1 — Dependencia de Gradle

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":coredomainplatform"))
    // Única dependencia transitiva del SDK:
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
```

El SDK es `commonMain` puro. No trae dependencias de Android, ni Room, ni Ktor, ni UI.

---

## Paso 2 — Contratos del SDK

### Tipos de Use Case

El SDK provee 5 contratos de caso de uso. Todos son `fun interface` (SAM):

| Contrato | Firma | Cuándo usar |
|---|---|---|
| `PureUseCase<I, O>` | `(I) → DomainResult<O>` | Lógica síncrona y pura. Sin I/O. Seguro desde main thread |
| `SuspendUseCase<I, O>` | `suspend (I) → DomainResult<O>` | Operaciones async de resultado único (crear, actualizar, buscar) |
| `FlowUseCase<I, O>` | `(I) → Flow<DomainResult<O>>` | Observar cambios en el tiempo (streams reactivos) |
| `NoParamsUseCase<O>` | `suspend () → DomainResult<O>` | Variante sin parámetros de `SuspendUseCase` |
| `NoParamsFlowUseCase<O>` | `() → Flow<DomainResult<O>>` | Variante sin parámetros de `FlowUseCase` |

Todos retornan `DomainResult<T>`, nunca lanzan excepciones al consumidor.

```kotlin
// Ejemplo: caso de uso síncrono puro
val calculateDiscount = PureUseCase<Order, Money> { order ->
    Money(order.total.amount * 0.1).asSuccess()
}

// Ejemplo: caso de uso suspendido
val createTask = SuspendUseCase<CreateTaskParams, Task> { params ->
    // validar → aplicar política → guardar → retornar
}
```

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
// Falla con el primer error encontrado si alguno es Failure
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

```kotlin
// Manejo exhaustivo
when (error) {
    is DomainError.Validation -> showFieldError(error.field, error.detail)
    is DomainError.NotFound -> showNotFound(error.resourceType)
    is DomainError.Unauthorized -> navigateToLogin()
    is DomainError.Conflict -> showConflict(error.detail)
    is DomainError.Infrastructure -> showRetry()
    is DomainError.Unknown -> showGenericError()
}
```

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

```kotlin
// El dominio define:
interface TaskRepository : ReadRepository<TaskId, Task>, WriteRepository<Task>

// La capa de datos implementa (fuera del SDK):
class TaskRepositoryImpl(...) : TaskRepository { ... }
```

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
// Fail-fast: se detiene en el primer error
val titleValidator = notBlankValidator("title")
    .andThen(maxLengthValidator("title", 200))

// Acumula todos los errores
val errors: List<DomainError> = collectValidationErrors(title, listOf(
    notBlankValidator("title"),
    maxLengthValidator("title", 200),
    minLengthValidator("title", 3),
))
```

### Políticas de dominio

`DomainPolicy<C>` modela **reglas de negocio semánticas** (a diferencia de validadores que son sintácticos):

```kotlin
fun interface DomainPolicy<in C> {
    fun evaluate(context: C): DomainResult<Unit>
}
```

**Variante async** para reglas que requieren I/O:
```kotlin
fun interface SuspendDomainPolicy<in C> {
    suspend fun evaluate(context: C): DomainResult<Unit>
}
```

**Combinadores:**
```kotlin
val canPromote = isActivePolicy and hasMinTenurePolicy    // ambas deben pasar
val canAccess = isAdminPolicy or isOwnerPolicy             // al menos una
val cannotBeArchived = isArchivedPolicy.negate {
    DomainError.Conflict(detail = "Ya está archivado")
}
```

### Providers

Dos abstracciones para side effects que el dominio necesita:

| Provider | Método | Propósito |
|---|---|---|
| `ClockProvider` | `nowMillis(): Long` | Obtener tiempo actual (epoch ms) |
| `IdProvider` | `next(): String` | Generar ID único |

Ambos son `fun interface`. El dominio nunca llama a `System.currentTimeMillis()` ni `UUID.randomUUID()` directamente.

### DomainDependencies

Contenedor inmutable para providers cross-cutting:

```kotlin
data class DomainDependencies(
    val clock: ClockProvider,
    val idProvider: IdProvider,
)
```

Se crea **una sola vez** al inicio de la app y se pasa a todos los módulos de dominio.

---

## Paso 3 — Implementar un Use Case

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

        // 3. Persistir (delega al contrato de repository)
        return taskRepository.save(task).map { task }
    }
}

data class CreateTaskParams(val title: String)
```

**Puntos clave:**
- El use case recibe `DomainDependencies` + contratos de repository/gateway por constructor
- Usa `Validator` para validación de input
- Usa `ClockProvider` e `IdProvider` del `DomainDependencies` — nunca APIs de plataforma
- Retorna `DomainResult<Task>` — nunca lanza excepciones

---

## Paso 4 — Validación de input

```kotlin
// Componer validadores con andThen (fail-fast)
val titleValidator = notBlankValidator("title")
    .andThen(maxLengthValidator("title", 200))

// Dentro del use case:
titleValidator.validate(params.title).let { result ->
    if (result.isFailure) return result as DomainResult.Failure
}

// O validar múltiples campos acumulando errores:
val errors = collectValidationErrors(params.title, listOf(
    notBlankValidator("title"),
    minLengthValidator("title", 3),
    maxLengthValidator("title", 200),
))
if (errors.isNotEmpty()) {
    return domainFailure(errors.first())
}
```

---

## Paso 5 — Políticas de negocio

```kotlin
// Política: no se puede completar una tarea ya completada
val canCompleteTask = DomainPolicy<Task> { task ->
    if (!task.completed) Unit.asSuccess()
    else domainFailure(DomainError.Conflict(detail = "Tarea ya completada"))
}

// Usar dentro de un use case:
canCompleteTask.evaluate(existingTask).let { result ->
    if (result.isFailure) return result as DomainResult.Failure
}

// Componer políticas:
val canModifyTask = isNotCompletedPolicy and isNotArchivedPolicy
```

---

## Paso 6 — Definir un Repository contract

```kotlin
// Dentro del dominio (este módulo o tu feature module):
interface TaskRepository : ReadRepository<TaskId, Task>, WriteRepository<Task> {
    suspend fun findByStatus(completed: Boolean): DomainResult<List<Task>>
}

// La capa de datos (fuera del SDK) implementa este contrato.
// El SDK nunca conoce Room, Ktor, ni ninguna tecnología concreta.
```

---

## Paso 7 — Cablear DomainDependencies

```kotlin
// En Application.onCreate() o tu entrypoint:
val domainDeps = DomainDependencies(
    clock = ClockProvider { System.currentTimeMillis() },
    idProvider = IdProvider { UUID.randomUUID().toString() },
)
```

Esto es lo **único** que necesitas del SDK a nivel de wiring. Los repositories son contratos
que tu capa de datos implementa e inyecta en los use cases.

---

## Paso 8 — Inyectar Use Cases en el ViewModel

El ViewModel recibe **solo los contratos** del SDK (interfaces `SuspendUseCase`, `FlowUseCase`, etc.),
nunca implementaciones concretas:

```kotlin
class TaskViewModel(
    private val createTask: SuspendUseCase<CreateTaskParams, Task>,
    private val observeTasks: NoParamsFlowUseCase<List<Task>>,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TaskUiState>(TaskUiState.Idle)
    val uiState: StateFlow<TaskUiState> = _uiState.asStateFlow()

    fun onCreateTask(title: String) {
        viewModelScope.launch {
            _uiState.value = TaskUiState.Loading

            createTask(CreateTaskParams(title))
                .onSuccess { task ->
                    _uiState.value = TaskUiState.Success(task)
                }
                .onFailure { error ->
                    _uiState.value = when (error) {
                        is DomainError.Validation ->
                            TaskUiState.ValidationError(error.field, error.detail)
                        else ->
                            TaskUiState.Error(error.message)
                    }
                }
        }
    }
}
```

El ViewModel **no sabe** cómo se implementan los use cases, ni qué base de datos usan,
ni de dónde vienen los datos. Solo conoce los contratos del SDK.

---

## Paso 9 — Testing de Use Cases

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
        assertEquals("Mi tarea", result.getOrNull()!!.title)
        assertEquals(1, fakeRepo.saved.size)
    }

    @Test
    fun `falla con título en blanco`() = runTest {
        val result = useCase(CreateTaskParams("   "))

        assertTrue(result.isFailure)
        val error = result.errorOrNull()
        assertTrue(error is DomainError.Validation)
        assertEquals("title", (error as DomainError.Validation).field)
    }
}
```

**Principios de testing:**
- `DomainDependencies` con valores fijos → determinismo total
- Stubs manuales para repositories → sin framework de mocking
- `runTest` de kotlinx-coroutines-test para use cases suspend

---

## FAQ

**P: ¿El SDK depende de alguna librería Android?**
No. Es Kotlin `commonMain` puro. Compila a bytecode JVM sin dependencias Android.

**P: ¿Puedo llamar `PureUseCase` desde el main thread?**
Sí. `PureUseCase` es síncrono y puro — sin I/O, sin bloqueo. Seguro desde cualquier hilo.

**P: ¿Debo usar `FlowUseCase` con `collectAsStateWithLifecycle()`?**
Sí. `FlowUseCase` retorna `Flow<DomainResult<T>>`. Colecta con collectors lifecycle-aware.

**P: ¿Puedo tener múltiples `DomainDependencies`?**
No. Crea una instancia al arranque y compártela entre todos los módulos. Es inmutable y thread-safe.

**P: ¿`runDomainCatching` atrapa `CancellationException`?**
No. `CancellationException` siempre se re-lanza para preservar la concurrencia estructurada.

**P: ¿Necesito reglas ProGuard/R8?**
No. El SDK no usa reflexión ni carga dinámica de clases.

**P: ¿Cuál es la diferencia entre `Validator` y `DomainPolicy`?**
`Validator` = restricciones sintácticas/estructurales (no blank, max length, range).
`DomainPolicy` = reglas de negocio semánticas (puede requerir contexto de agregado o estado).

**P: ¿El SDK contiene implementaciones de Repository?**
No. El SDK solo define contratos (interfaces). La capa de datos provee las implementaciones.

---

<details>
<summary><h2>🇺🇸 English Version</h2></summary>

> This guide covers **exclusively the domain SDK** and how to use it in an Android project.
> It does not cover data layer implementations (Room, Ktor, Retrofit) or UI (Compose, XML).

---

## Step 1 — Gradle dependency

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":coredomainplatform"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
```

The SDK is pure `commonMain`. No Android, Room, Ktor, or UI dependencies.

---

## Step 2 — SDK Contracts

### Use Case types

5 `fun interface` contracts:

| Contract | Signature | When to use |
|---|---|---|
| `PureUseCase<I, O>` | `(I) → DomainResult<O>` | Synchronous, pure logic. No I/O. Safe from main thread |
| `SuspendUseCase<I, O>` | `suspend (I) → DomainResult<O>` | Async single-result operations |
| `FlowUseCase<I, O>` | `(I) → Flow<DomainResult<O>>` | Observe changes over time |
| `NoParamsUseCase<O>` | `suspend () → DomainResult<O>` | No-params variant of SuspendUseCase |
| `NoParamsFlowUseCase<O>` | `() → Flow<DomainResult<O>>` | No-params variant of FlowUseCase |

### DomainResult

Sealed type modeling success or failure **without exceptions**:

```kotlin
sealed class DomainResult<out T> {
    data class Success<out T>(val value: T) : DomainResult<T>()
    data class Failure(val error: DomainError) : DomainResult<Nothing>()
}
```

Operators: `map`, `flatMap`, `mapError`, `onSuccess`, `onFailure`, `getOrNull`, `getOrElse`, `zip`, `runDomainCatching`.

### DomainError

Sealed hierarchy with 6 subtypes: `Validation`, `NotFound`, `Unauthorized`, `Conflict`, `Infrastructure`, `Unknown`.

### Domain Model

- `Entity<ID>` — identity-based objects
- `ValueObject` — immutable, structural equality
- `AggregateRoot<ID>` — consistency boundary, repositories operate on these

### Repository & Gateway contracts

Repositories: `ReadRepository<ID, T>`, `ReadCollectionRepository<T>`, `WriteRepository<T>`.
Gateways: `SuspendGateway<I, O>`, `CommandGateway<I>`.

The SDK defines interfaces only. The data layer provides implementations.

### Validators

`Validator<T>` with built-in primitives: `notBlankValidator`, `maxLengthValidator`, `minLengthValidator`, `rangeValidator`.
Compose with `andThen` (fail-fast) or `collectValidationErrors` (accumulate all).

### Domain Policies

`DomainPolicy<C>` for semantic business rules. `SuspendDomainPolicy<C>` for async rules.
Combinators: `and`, `or`, `negate`.

### Providers & DomainDependencies

`ClockProvider` (epoch ms) and `IdProvider` (unique string), grouped in immutable `DomainDependencies`.

---

## Step 3 — Implement a Use Case

```kotlin
class CreateTaskUseCase(
    private val deps: DomainDependencies,
    private val taskRepository: TaskRepository,
    private val titleValidator: Validator<String>,
) : SuspendUseCase<CreateTaskParams, Task> {

    override suspend fun invoke(params: CreateTaskParams): DomainResult<Task> {
        titleValidator.validate(params.title).let { result ->
            if (result.isFailure) return result as DomainResult.Failure
        }
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

## Step 4 — Inject Use Cases into the ViewModel

The ViewModel receives **only SDK contracts**, never concrete implementations:

```kotlin
class TaskViewModel(
    private val createTask: SuspendUseCase<CreateTaskParams, Task>,
    private val observeTasks: NoParamsFlowUseCase<List<Task>>,
) : ViewModel() {

    fun onCreateTask(title: String) {
        viewModelScope.launch {
            createTask(CreateTaskParams(title))
                .onSuccess { /* update UI state */ }
                .onFailure { error ->
                    when (error) {
                        is DomainError.Validation -> /* show field error */
                        else -> /* show generic error */
                    }
                }
        }
    }
}
```

---

## Step 5 — Testing

```kotlin
private val fixedDeps = DomainDependencies(
    clock = ClockProvider { 1_700_000_000_000L },
    idProvider = IdProvider { "test-id-1" },
)
// Manual stubs — no mocking framework needed
```

---

## FAQ

**Q: Does the SDK depend on any Android library?** No. Pure `commonMain` Kotlin.

**Q: Can I call `PureUseCase` from the main thread?** Yes. Synchronous, pure, no I/O.

**Q: Does `runDomainCatching` catch `CancellationException`?** No. Always rethrown.

**Q: Does the SDK contain Repository implementations?** No. Only contracts (interfaces).

**Q: What's the difference between `Validator` and `DomainPolicy`?**
Validator = syntactic constraints. DomainPolicy = semantic business rules.

</details>
