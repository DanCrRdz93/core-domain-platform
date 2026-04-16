# Core Domain Platform SDK

![Version](https://img.shields.io/badge/version-1.0.0-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple)
![Platform](https://img.shields.io/badge/platform-JVM%20%7C%20Android%20%7C%20iOS-green)

A **pure domain layer SDK** for Kotlin Multiplatform. Zero framework dependencies.
Zero infrastructure. Zero UI. Just typed contracts, functional error handling,
and Clean Architecture enforced at the compiler level.

```
Targets: JVM · Android · iOS (arm64, x64, simulator)
Language: Kotlin 2.0 · KMP
Dependencies: kotlinx-coroutines-core (only)
```

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Package Structure](#package-structure)
- [Core Components](#core-components)
- [Step-by-Step Implementation Guide](#step-by-step-implementation-guide)
- [Android Integration Guide](#android-integration-guide)
- [iOS Integration Guide](#ios-integration-guide)
- [Error Handling Reference](#error-handling-reference)
- [Testing Strategy](#testing-strategy)
- [Versioning](#versioning)

---

## Architecture Overview

```mermaid
graph TB
    subgraph "App Layer"
        VM["ViewModel / Presenter"]
        DI["Manual Wiring / DI Framework"]
    end

    subgraph "Domain SDK (this module)"
        UC["Use Cases<br/><i>PureUseCase · SuspendUseCase · FlowUseCase</i>"]
        POL["Policies<br/><i>DomainPolicy · SuspendDomainPolicy</i>"]
        VAL["Validators<br/><i>Validator · andThen · validateAll</i>"]
        MOD["Model<br/><i>Entity · ValueObject · AggregateRoot · EntityId</i>"]
        ERR["DomainError<br/><i>Validation · NotFound · Conflict · …</i>"]
        RES["DomainResult&lt;T&gt;<br/><i>Success · Failure · map · flatMap · zip</i>"]
        REPO["Repository Contracts<br/><i>ReadRepository · WriteRepository</i>"]
        GW["Gateway Contracts<br/><i>SuspendGateway · CommandGateway</i>"]
        PROV["Providers<br/><i>ClockProvider · IdProvider</i>"]
        DEPS["DomainDependencies<br/><i>clock + idProvider</i>"]
    end

    subgraph "Data Layer (external)"
        DB["Room / SQLDelight / CoreData"]
        NET["Ktor / Retrofit / URLSession"]
        IMPL["Repository & Gateway Implementations"]
    end

    VM --> UC
    DI --> UC
    DI --> DEPS
    UC --> POL
    UC --> VAL
    UC --> MOD
    UC --> RES
    UC --> ERR
    UC -.-> REPO
    UC -.-> GW
    UC --> PROV
    IMPL --> REPO
    IMPL --> GW
    IMPL --> DB
    IMPL --> NET

    style UC fill:#4CAF50,color:#fff
    style RES fill:#2196F3,color:#fff
    style ERR fill:#f44336,color:#fff
    style DEPS fill:#FF9800,color:#fff
```

### Dependency Rule

```mermaid
graph LR
    DATA["Data SDK"] -->|"depends on"| DOMAIN["Domain SDK"]
    APP["App Layer"] -->|"depends on"| DOMAIN
    APP -->|"depends on"| DATA
    DOMAIN -.->|"knows nothing about"| DATA
    DOMAIN -.->|"knows nothing about"| APP

    style DOMAIN fill:#4CAF50,color:#fff
    style DATA fill:#2196F3,color:#fff
    style APP fill:#FF9800,color:#fff
```

**The domain SDK defines contracts. External layers implement them.**
The domain never imports from data, UI, or any framework.

---

## Package Structure

```
com.domain.core/
├── di/            → DomainDependencies, DomainModule
├── error/         → DomainError (sealed hierarchy)
├── result/        → DomainResult<T> + operators (map, flatMap, zip, …)
├── model/         → Entity, ValueObject, AggregateRoot, EntityId
├── usecase/       → PureUseCase, SuspendUseCase, FlowUseCase, NoParams*
├── repository/    → Repository, ReadRepository, WriteRepository, ReadCollectionRepository
├── gateway/       → Gateway, SuspendGateway, CommandGateway
├── validation/    → Validator<T>, andThen, validateAll, collectValidationErrors
├── policy/        → DomainPolicy, SuspendDomainPolicy, and/or/negate
└── provider/      → ClockProvider, IdProvider
```

---

## Core Components

### DomainResult

```mermaid
graph LR
    A["DomainResult&lt;T&gt;"] --> B["Success(value: T)"]
    A --> C["Failure(error: DomainError)"]
    B --> D[".map { }"]
    B --> E[".flatMap { }"]
    B --> F[".zip(other) { }"]
    C --> G[".mapError { }"]
    B --> H[".onSuccess { }"]
    C --> I[".onFailure { }"]

    style B fill:#4CAF50,color:#fff
    style C fill:#f44336,color:#fff
```

### DomainError Hierarchy

```mermaid
graph TB
    DE["DomainError (sealed)"] --> V["Validation<br/>field + detail"]
    DE --> NF["NotFound<br/>resourceType + id"]
    DE --> UA["Unauthorized<br/>detail"]
    DE --> CO["Conflict<br/>detail"]
    DE --> IN["Infrastructure<br/>detail + cause?"]
    DE --> UN["Unknown<br/>detail + cause?"]

    style DE fill:#f44336,color:#fff
    style V fill:#ff7043,color:#fff
    style NF fill:#ff7043,color:#fff
    style UA fill:#ff7043,color:#fff
    style CO fill:#ff7043,color:#fff
    style IN fill:#ff7043,color:#fff
    style UN fill:#ff7043,color:#fff
```

### Use Case Contracts

```mermaid
graph LR
    P["PureUseCase&lt;I, O&gt;<br/><i>sync, no I/O</i>"] --> R["DomainResult&lt;O&gt;"]
    S["SuspendUseCase&lt;I, O&gt;<br/><i>async, single value</i>"] --> R
    F["FlowUseCase&lt;I, O&gt;<br/><i>reactive, multi-value</i>"] --> FR["Flow&lt;DomainResult&lt;O&gt;&gt;"]

    style P fill:#4CAF50,color:#fff
    style S fill:#2196F3,color:#fff
    style F fill:#9C27B0,color:#fff
```

### Composition Flow

```mermaid
sequenceDiagram
    participant App as App Layer
    participant Deps as DomainDependencies
    participant Mod as FeatureDomainModule
    participant UC as Use Case
    participant Repo as Repository (contract)
    participant Data as Data Layer (impl)

    App->>Deps: Create(clock, idProvider)
    App->>Data: Create RepoImpl(db)
    App->>Mod: Create ModuleImpl(deps, repo)
    App->>UC: module.createUser(params)
    UC->>Repo: repo.save(entity)
    Repo->>Data: Data layer handles persistence
    Data-->>Repo: DomainResult<Unit>
    Repo-->>UC: DomainResult<Unit>
    UC-->>App: DomainResult<User>
```

---

## Step-by-Step Implementation Guide

This guide walks you through integrating the SDK into a new or existing KMP project.
Follow each step in order.

### Step 1 — Add the SDK as a dependency

**Scenario:** You have a KMP project and want to use this SDK as your domain layer.

Add the SDK module to your project. If it's a local module:

```kotlin
// settings.gradle.kts
include(":coredomainplatform")
project(":coredomainplatform").projectDir = file("path/to/coredomainplatform")
```

Then in your feature or app module:

```kotlin
// build.gradle.kts of your app/feature module
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":coredomainplatform"))
            }
        }
    }
}
```

### Step 2 — Define your domain models

**Scenario:** You're building a task management feature and need a `Task` entity with a typed ID.

```kotlin
// In your feature's domain package (NOT in this SDK)
package com.myapp.feature.task.model

import com.domain.core.model.AggregateRoot
import com.domain.core.model.EntityId

@JvmInline
value class TaskId(override val value: String) : EntityId<String>

data class Task(
    override val id: TaskId,
    val title: String,
    val completed: Boolean,
    val createdAt: Long,
) : AggregateRoot<TaskId>
```

### Step 3 — Define your repository contract

**Scenario:** Your `Task` feature needs persistence — the domain defines what it needs, not how.

```kotlin
package com.myapp.feature.task.repository

import com.domain.core.repository.ReadRepository
import com.domain.core.repository.WriteRepository
import com.myapp.feature.task.model.Task
import com.myapp.feature.task.model.TaskId

interface TaskRepository : ReadRepository<TaskId, Task>, WriteRepository<Task>
```

### Step 4 — Create validators for your domain rules

**Scenario:** A task title must not be blank and must not exceed 200 characters.

```kotlin
package com.myapp.feature.task.validation

import com.domain.core.validation.notBlankValidator
import com.domain.core.validation.maxLengthValidator
import com.domain.core.validation.andThen

val taskTitleValidator = notBlankValidator("title")
    .andThen(maxLengthValidator("title", 200))
```

### Step 5 — Create policies for business rules

**Scenario:** A task can only be completed if it has a title (not blank). This is a semantic business rule, not just field validation.

```kotlin
package com.myapp.feature.task.policy

import com.domain.core.error.DomainError
import com.domain.core.policy.DomainPolicy
import com.domain.core.result.asSuccess
import com.domain.core.result.domainFailure
import com.myapp.feature.task.model.Task

val canCompleteTask = DomainPolicy<Task> { task ->
    if (task.title.isNotBlank()) Unit.asSuccess()
    else domainFailure(DomainError.Conflict(detail = "Cannot complete a task without a title"))
}
```

### Step 6 — Implement your use case

**Scenario:** Create a new task. The use case validates input, generates an ID, timestamps, and persists.

```kotlin
package com.myapp.feature.task.usecase

import com.domain.core.di.DomainDependencies
import com.domain.core.error.DomainError
import com.domain.core.result.DomainResult
import com.domain.core.result.asSuccess
import com.domain.core.result.domainFailure
import com.domain.core.result.flatMap
import com.domain.core.usecase.SuspendUseCase
import com.myapp.feature.task.model.Task
import com.myapp.feature.task.model.TaskId
import com.myapp.feature.task.repository.TaskRepository
import com.myapp.feature.task.validation.taskTitleValidator

data class CreateTaskParams(val title: String)

class CreateTaskUseCase(
    private val deps: DomainDependencies,
    private val repository: TaskRepository,
) : SuspendUseCase<CreateTaskParams, Task> {

    override suspend fun invoke(params: CreateTaskParams): DomainResult<Task> {
        // 1. Validate
        val validation = taskTitleValidator.validate(params.title)
        if (validation.isFailure) return validation as DomainResult<Task>

        // 2. Build entity
        val task = Task(
            id = TaskId(deps.idProvider.next()),
            title = params.title,
            completed = false,
            createdAt = deps.clock.nowMillis(),
        )

        // 3. Persist and return
        return repository.save(task).flatMap { task.asSuccess() }
    }
}
```

### Step 7 — Define your feature DomainModule

**Scenario:** Expose all use cases for the task feature through a single composable module.

```kotlin
package com.myapp.feature.task.di

import com.domain.core.di.DomainDependencies
import com.domain.core.di.DomainModule
import com.domain.core.usecase.SuspendUseCase
import com.myapp.feature.task.model.Task
import com.myapp.feature.task.repository.TaskRepository
import com.myapp.feature.task.usecase.CreateTaskParams
import com.myapp.feature.task.usecase.CreateTaskUseCase

interface TaskDomainModule : DomainModule {
    val createTask: SuspendUseCase<CreateTaskParams, Task>
}

class TaskDomainModuleImpl(
    deps: DomainDependencies,
    taskRepository: TaskRepository,
) : TaskDomainModule {
    override val createTask = CreateTaskUseCase(deps, taskRepository)
}
```

### Step 8 — Wire everything in the app layer

**Scenario:** Your app startup creates all dependencies and assembles all modules.

```kotlin
// App layer — wiring. This is the ONLY place where concrete types meet.
val domainDeps = DomainDependencies(
    clock = ClockProvider { System.currentTimeMillis() },
    idProvider = IdProvider { UUID.randomUUID().toString() },
)

val taskRepository: TaskRepository = TaskRepositoryImpl(database.taskDao())

val taskModule: TaskDomainModule = TaskDomainModuleImpl(
    deps = domainDeps,
    taskRepository = taskRepository,
)
```

### Step 9 — Test your use case

**Scenario:** Test that `CreateTaskUseCase` produces a task with deterministic ID and timestamp.

```kotlin
class CreateTaskUseCaseTest {

    private val testDeps = DomainDependencies(
        clock = ClockProvider { 1_700_000_000_000L },
        idProvider = IdProvider { "task-001" },
    )

    private val fakeRepo = object : TaskRepository {
        override suspend fun findById(id: TaskId) = null.asSuccess()
        override suspend fun save(entity: Task) = Unit.asSuccess()
        override suspend fun delete(entity: Task) = Unit.asSuccess()
    }

    private val useCase = CreateTaskUseCase(testDeps, fakeRepo)

    @Test
    fun `creates task with injected id and timestamp`() = runTest {
        val result = useCase(CreateTaskParams("Buy groceries"))
        val task = result.shouldBeSuccess()

        assertEquals("task-001", task.id.value)
        assertEquals(1_700_000_000_000L, task.createdAt)
        assertEquals("Buy groceries", task.title)
        assertFalse(task.completed)
    }

    @Test
    fun `rejects blank title`() = runTest {
        val result = useCase(CreateTaskParams("   "))
        result.shouldFailWith<DomainError.Validation>()
    }
}
```

---

## Android Integration Guide

### Architecture on Android

```mermaid
graph TB
    subgraph "Android App"
        ACT["Activity / Fragment"]
        VM["ViewModel"]
        HILT["Hilt / Koin / Manual DI"]
    end

    subgraph "Domain SDK"
        UC["SuspendUseCase"]
        DEPS["DomainDependencies"]
        MOD["FeatureDomainModule"]
    end

    subgraph "Data Layer"
        ROOM["Room Database"]
        KTOR["Ktor Client"]
        REPOIMPL["RepositoryImpl"]
    end

    ACT --> VM
    VM --> UC
    HILT --> MOD
    HILT --> DEPS
    MOD --> UC
    REPOIMPL --> ROOM
    REPOIMPL --> KTOR

    style UC fill:#4CAF50,color:#fff
    style VM fill:#FF9800,color:#fff
    style REPOIMPL fill:#2196F3,color:#fff
```

### Step A1 — Gradle setup

**Scenario:** Your Android app has a `:app` module and wants to use the SDK.

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":coredomainplatform"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
```

### Step A2 — Platform providers

**Scenario:** Provide Android-specific implementations for `ClockProvider` and `IdProvider`.

```kotlin
// data module — AndroidProviders.kt
import com.domain.core.provider.ClockProvider
import com.domain.core.provider.IdProvider
import java.util.UUID

val androidClock: ClockProvider = ClockProvider {
    System.currentTimeMillis()
}

val androidIdProvider: IdProvider = IdProvider {
    UUID.randomUUID().toString()
}
```

### Step A3 — Room repository implementation

**Scenario:** Implement `TaskRepository` backed by Room.

```kotlin
// data module — TaskRepositoryImpl.kt
class TaskRepositoryImpl(
    private val dao: TaskDao,
) : TaskRepository {

    override suspend fun findById(id: TaskId): DomainResult<Task?> =
        runDomainCatching(
            errorMapper = { DomainError.Infrastructure(detail = "DB read failed", cause = it) }
        ) {
            dao.findById(id.value)?.toDomain()
        }

    override suspend fun save(entity: Task): DomainResult<Unit> =
        runDomainCatching(
            errorMapper = { DomainError.Infrastructure(detail = "DB write failed", cause = it) }
        ) {
            dao.insertOrReplace(entity.toEntity())
        }

    override suspend fun delete(entity: Task): DomainResult<Unit> =
        runDomainCatching(
            errorMapper = { DomainError.Infrastructure(detail = "DB delete failed", cause = it) }
        ) {
            dao.delete(entity.id.value)
        }
}
```

### Step A4 — ViewModel integration

**Scenario:** A ViewModel calls a use case and maps the result to UI state.

```kotlin
class TaskViewModel(
    private val createTask: SuspendUseCase<CreateTaskParams, Task>,
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
                        is DomainError.Infrastructure ->
                            TaskUiState.Error("Something went wrong. Please try again.")
                        else ->
                            TaskUiState.Error(error.message)
                    }
                }
        }
    }
}

sealed interface TaskUiState {
    data object Idle : TaskUiState
    data object Loading : TaskUiState
    data class Success(val task: Task) : TaskUiState
    data class ValidationError(val field: String, val detail: String) : TaskUiState
    data class Error(val message: String) : TaskUiState
}
```

### Step A5 — Manual wiring (no DI framework)

**Scenario:** Wire everything without Koin or Hilt.

```kotlin
// AppModule.kt — create once at Application.onCreate()
class AppModule(context: Context) {

    private val database = Room.databaseBuilder(
        context, AppDatabase::class.java, "app.db"
    ).build()

    private val domainDeps = DomainDependencies(
        clock = androidClock,
        idProvider = androidIdProvider,
    )

    private val taskRepository: TaskRepository = TaskRepositoryImpl(database.taskDao())

    val taskModule: TaskDomainModule = TaskDomainModuleImpl(
        deps = domainDeps,
        taskRepository = taskRepository,
    )
}

// In your Activity or Fragment:
val appModule = (application as MyApp).appModule
val viewModel = TaskViewModel(createTask = appModule.taskModule.createTask)
```

### Step A6 — Wiring with Hilt (optional)

**Scenario:** You prefer Hilt for DI on Android.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides @Singleton
    fun provideDomainDeps(): DomainDependencies = DomainDependencies(
        clock = androidClock,
        idProvider = androidIdProvider,
    )

    @Provides @Singleton
    fun provideTaskModule(
        deps: DomainDependencies,
        taskRepository: TaskRepository,
    ): TaskDomainModule = TaskDomainModuleImpl(deps, taskRepository)

    @Provides
    fun provideCreateTask(module: TaskDomainModule): SuspendUseCase<CreateTaskParams, Task> =
        module.createTask
}
```

### Step A7 — Wiring with Koin (optional)

**Scenario:** You prefer Koin for DI on Android.

```kotlin
val domainModule = module {
    single {
        DomainDependencies(
            clock = androidClock,
            idProvider = androidIdProvider,
        )
    }

    single<TaskDomainModule> {
        TaskDomainModuleImpl(
            deps = get(),
            taskRepository = get(),
        )
    }

    factory { get<TaskDomainModule>().createTask }
}
```

### Android FAQ

**Q: Does the SDK depend on any Android library?**
No. The SDK is pure `commonMain` Kotlin. It compiles to JVM bytecode on Android
without any Android-specific dependency.

**Q: Can I use this SDK in a Compose Multiplatform project?**
Yes. The SDK has no UI dependency. Your Compose layer calls use cases exactly
like any ViewModel would.

**Q: What if my repository throws a checked exception (e.g., `SQLiteException`)?**
Use `runDomainCatching` in your repository implementation. It catches all
non-cancellation throwables and maps them to `DomainError`. Cancellation
exceptions are always rethrown to preserve structured concurrency.

**Q: Should I wrap `Flow` use cases in `collectAsStateWithLifecycle()`?**
Yes. `FlowUseCase` returns `Flow<DomainResult<T>>`. Collect it in your ViewModel
or Compose UI using the standard lifecycle-aware collectors.

**Q: Can I call `PureUseCase` from the UI thread?**
Yes. `PureUseCase` is synchronous and pure — no I/O, no blocking. It is
explicitly safe to call from any thread including the main thread.

**Q: How do I handle `DomainError.Unauthorized` on Android?**
Map it in your ViewModel or a shared error handler to trigger a sign-out flow
or navigation to the login screen:
```kotlin
.onFailure { error ->
    if (error is DomainError.Unauthorized) {
        navigator.navigateTo(LoginScreen)
    }
}
```

**Q: What about ProGuard/R8 rules?**
The SDK uses no reflection, no serialization annotations, and no dynamic class
loading. No ProGuard rules are needed.

**Q: Can I have multiple `DomainDependencies` instances?**
No. Create one instance at app startup and share it across all feature modules.
It is immutable and concurrency-safe.

---

## iOS Integration Guide

### Architecture on iOS

```mermaid
graph TB
    subgraph "iOS App (Swift)"
        VIEW["SwiftUI View"]
        VM_IOS["ObservableObject / ViewModel"]
        FACTORY["Module Factory"]
    end

    subgraph "Shared KMP Module"
        UC_IOS["SuspendUseCase"]
        DEPS_IOS["DomainDependencies"]
        MOD_IOS["FeatureDomainModule"]
    end

    subgraph "Data Layer (KMP shared)"
        CD["CoreData / SQLDelight"]
        URL["URLSession / Ktor"]
        REPO_IOS["RepositoryImpl"]
    end

    VIEW --> VM_IOS
    VM_IOS --> UC_IOS
    FACTORY --> MOD_IOS
    FACTORY --> DEPS_IOS
    MOD_IOS --> UC_IOS
    REPO_IOS --> CD
    REPO_IOS --> URL

    style UC_IOS fill:#4CAF50,color:#fff
    style VM_IOS fill:#FF9800,color:#fff
    style REPO_IOS fill:#2196F3,color:#fff
```

### Step I1 — KMP framework export

**Scenario:** Your KMP project needs to export the shared module as an iOS framework.

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

### Step I2 — Platform providers for iOS

**Scenario:** Provide iOS-specific implementations using Kotlin/Native.

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

### Step I3 — Module factory for Swift

**Scenario:** Swift cannot call Kotlin constructors with complex generics directly.
Create a factory function that Swift can call easily.

```kotlin
// shared/src/iosMain/kotlin/ModuleFactory.kt
import com.domain.core.di.DomainDependencies

object SharedModuleFactory {

    private val domainDeps = DomainDependencies(
        clock = iosClock,
        idProvider = iosIdProvider,
    )

    fun createTaskModule(taskRepository: TaskRepository): TaskDomainModule =
        TaskDomainModuleImpl(
            deps = domainDeps,
            taskRepository = taskRepository,
        )
}
```

### Step I4 — SQLDelight repository for iOS

**Scenario:** Implement `TaskRepository` with SQLDelight (KMP-native persistence).

```kotlin
// shared data module
class TaskRepositoryImpl(
    private val queries: TaskQueries,
) : TaskRepository {

    override suspend fun findById(id: TaskId): DomainResult<Task?> =
        runDomainCatching(
            errorMapper = { DomainError.Infrastructure(detail = "DB read failed", cause = it) }
        ) {
            queries.findById(id.value).executeAsOneOrNull()?.toDomain()
        }

    override suspend fun save(entity: Task): DomainResult<Unit> =
        runDomainCatching(
            errorMapper = { DomainError.Infrastructure(detail = "DB write failed", cause = it) }
        ) {
            queries.insertOrReplace(
                id = entity.id.value,
                title = entity.title,
                completed = entity.completed,
                createdAt = entity.createdAt,
            )
        }

    override suspend fun delete(entity: Task): DomainResult<Unit> =
        runDomainCatching(
            errorMapper = { DomainError.Infrastructure(detail = "DB delete failed", cause = it) }
        ) {
            queries.deleteById(entity.id.value)
        }
}
```

### Step I5 — Calling from Swift

**Scenario:** Use the domain module from a SwiftUI ViewModel.

```swift
import SharedDomain

@MainActor
class TaskViewModel: ObservableObject {
    @Published var state: TaskState = .idle

    private let createTask: any SuspendUseCaseProtocol

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
            if let validation = failure.error as? DomainErrorValidation {
                state = .validationError(field: validation.field, detail: validation.detail)
            } else {
                state = .error(failure.error.message)
            }
        }
    }
}

enum TaskState {
    case idle
    case loading
    case success(Task)
    case validationError(field: String, detail: String)
    case error(String)
}
```

### Step I6 — SwiftUI View

**Scenario:** Connect the ViewModel to a SwiftUI View.

```swift
struct CreateTaskView: View {
    @StateObject private var viewModel: TaskViewModel
    @State private var title = ""

    init(taskModule: TaskDomainModule) {
        _viewModel = StateObject(wrappedValue: TaskViewModel(taskModule: taskModule))
    }

    var body: some View {
        VStack(spacing: 16) {
            TextField("Task title", text: $title)
                .textFieldStyle(.roundedBorder)

            Button("Create Task") {
                Task { await viewModel.onCreateTask(title: title) }
            }

            switch viewModel.state {
            case .idle:
                EmptyView()
            case .loading:
                ProgressView()
            case .success(let task):
                Text("Created: \(task.title)")
            case .validationError(let field, let detail):
                Text("\(field): \(detail)")
                    .foregroundColor(.red)
            case .error(let message):
                Text(message)
                    .foregroundColor(.red)
            }
        }
        .padding()
    }
}
```

### Step I7 — App entry point wiring

**Scenario:** Create the module factory at app launch.

```swift
@main
struct MyApp: App {
    private let taskModule: TaskDomainModule

    init() {
        let database = /* your SQLDelight driver setup */
        let taskRepo = TaskRepositoryImpl(queries: database.taskQueries)
        taskModule = SharedModuleFactory().createTaskModule(taskRepository: taskRepo)
    }

    var body: some Scene {
        WindowGroup {
            CreateTaskView(taskModule: taskModule)
        }
    }
}
```

### iOS FAQ

**Q: How does Kotlin/Native handle `suspend` functions in Swift?**
Kotlin 2.0+ generates Swift `async` functions for `suspend` Kotlin functions.
You call them with `await` in Swift. No manual callback wrappers needed.

**Q: How are sealed classes exposed in Swift?**
`DomainResult.Success` and `DomainResult.Failure` become separate classes in Swift.
Use `is` checks or `as?` casts to distinguish them. `DomainError` subtypes
similarly become separate classes (`DomainErrorValidation`, `DomainErrorNotFound`, etc.).

**Q: Can I use this with Combine instead of async/await?**
Yes. Wrap the `async` calls in Combine publishers if your project still uses Combine:
```swift
Future<Task, Error> { promise in
    Task { /* call the async use case here */ }
}
```
However, native `async/await` is recommended for new projects.

**Q: Do I need to worry about memory management with KMP objects?**
Kotlin/Native uses automatic reference counting (ARC) for objects exposed to Swift.
No manual memory management is needed. Avoid retaining Kotlin objects in long-lived
closures to prevent reference cycles.

**Q: What about `Flow` use cases on iOS?**
`Flow` is exposed as `Kotlinx_coroutines_coreFlow` in Swift. Use the
[KMP-NativeCoroutines](https://github.com/nicklockwood/KMP-NativeCoroutines) library
or SKIE to get native Swift `AsyncSequence` wrappers.

**Q: Is the framework size large?**
The domain SDK is very small — only typed interfaces, sealed classes, and pure functions.
Typical binary size contribution is under 100KB.

**Q: Can I use SwiftUI previews with this SDK?**
Yes. Create fake modules with stub repositories for previews:
```swift
#Preview {
    CreateTaskView(taskModule: FakeTaskDomainModule())
}
```

**Q: Does the SDK support watchOS / tvOS / macOS?**
The domain code is pure `commonMain` Kotlin. Add the targets in `build.gradle.kts`
and they will compile without changes:
```kotlin
watchosArm64()
tvosArm64()
macosArm64()
```

---

## Error Handling Reference

### Complete error mapping flow

```mermaid
flowchart TD
    A["Use case receives input"] --> B{"Validate input"}
    B -->|"Invalid"| C["DomainError.Validation<br/>field + detail"]
    B -->|"Valid"| D{"Apply business policy"}
    D -->|"Violated"| E["DomainError.Conflict<br/>detail"]
    D -->|"Satisfied"| F{"Call repository"}
    F -->|"Entity not found"| G["DomainError.NotFound<br/>resourceType + id"]
    F -->|"DB/network error"| H["DomainError.Infrastructure<br/>detail + cause"]
    F -->|"Auth failure"| I["DomainError.Unauthorized<br/>detail"]
    F -->|"Success"| J["DomainResult.Success&lt;T&gt;"]
    F -->|"Unexpected"| K["DomainError.Unknown<br/>detail + cause"]

    C --> L["DomainResult.Failure"]
    E --> L
    G --> L
    H --> L
    I --> L
    K --> L

    style J fill:#4CAF50,color:#fff
    style L fill:#f44336,color:#fff
```

### When to use each error type

| Error | When to use | Example |
|---|---|---|
| `Validation` | Input fails a domain invariant | Email format invalid, title too long |
| `NotFound` | Requested entity does not exist | User with ID "xyz" not in database |
| `Unauthorized` | Caller lacks permission | Non-admin tries to delete a user |
| `Conflict` | Operation conflicts with current state | Duplicate email, invalid state transition |
| `Infrastructure` | External dependency failed | Database timeout, network error |
| `Unknown` | Unexpected condition (should be rare) | Fallback for unclassified errors |

---

## Testing Strategy

### Test double decision tree

```mermaid
flowchart TD
    A["Need a test double?"] --> B{"Is it a fun interface?"}
    B -->|"Yes"| C["Use SAM lambda<br/><code>ClockProvider { 42L }</code>"]
    B -->|"No"| D{"Is it a Repository / Gateway?"}
    D -->|"Yes"| E["Use object expression<br/><code>object : Repo { ... }</code>"]
    D -->|"No"| F["Use data class / value"]

    C --> G{"Need to verify calls?"}
    G -->|"Yes"| H["Use capturing double<br/><code>val saved = mutableListOf()</code>"]
    G -->|"No"| I["Use fixed double"]

    style C fill:#4CAF50,color:#fff
    style E fill:#2196F3,color:#fff
    style H fill:#FF9800,color:#fff
```

### Available test helpers

Import from `com.domain.core.testing.TestDoubles` (in `commonTest` only):

| Helper | Description |
|---|---|
| `testDeps` | `DomainDependencies` with fixed clock + fixed ID |
| `fixedClock` / `clockAt(ms)` | Deterministic `ClockProvider` |
| `fixedId` / `idOf(s)` | Deterministic `IdProvider` |
| `sequentialIds(prefix)` | `IdProvider` that returns "prefix-1", "prefix-2", … |
| `advancingClock(start, step)` | `ClockProvider` that advances by step on each call |
| `validationError(field, detail)` | Quick `DomainError.Validation` builder |
| `notFoundError(type, id)` | Quick `DomainError.NotFound` builder |
| `shouldBeSuccess()` | Extracts value or throws clear `AssertionError` |
| `shouldBeFailure()` | Extracts error or throws clear `AssertionError` |
| `shouldFailWith<E>()` | Extracts and casts to expected error subtype |

See [TESTING.md](TESTING.md) for the full testing guide, naming conventions,
and anti-patterns.

---

## Versioning

This SDK follows [Semantic Versioning](https://semver.org/):

| Change type | Version bump | Consumer impact |
|---|---|---|
| Bug fix, doc update | **PATCH** | Safe to update |
| New contracts added | **MINOR** | Safe to update |
| Breaking changes to `public` API | **MAJOR** | Compile-time errors expected |

See [ARCHITECTURE.md](ARCHITECTURE.md) for design principles and
[INTEGRATION.md](INTEGRATION.md) for data-layer boundary rules.

---

## License

Private repository. All rights reserved.
