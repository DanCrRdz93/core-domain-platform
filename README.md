# Core Domain Platform SDK

![Version](https://img.shields.io/badge/version-1.0.0-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-purple)
![Platform](https://img.shields.io/badge/platform-JVM%20%7C%20Android%20%7C%20iOS-green)

> **Idioma / Language:** 🇪🇸 [Español](#tabla-de-contenidos) (predeterminado) · 🇺🇸 [English](#english-version)

Un **SDK de capa de dominio puro** para Kotlin Multiplatform. Cero dependencias de frameworks.
Cero infraestructura. Cero UI. Solo contratos tipados, manejo funcional de errores
y Clean Architecture forzada a nivel del compilador.

```
Targets: JVM · Android · iOS (arm64, x64, simulator)
Lenguaje: Kotlin 2.1.20 · KMP
Dependencias: kotlinx-coroutines-core 1.10.1 (única)
```

---

## Tabla de Contenidos

- [¿Por qué este SDK?](#por-qué-este-sdk)
- [Vista General de Arquitectura](#vista-general-de-arquitectura)
- [Estructura de Paquetes](#estructura-de-paquetes)
- [Componentes Principales](#componentes-principales)
- [DomainDependencies](#domaindependencies--el-contenedor-de-infraestructura-del-dominio)
- [Referencia Completa de Casos de Uso](#referencia-completa-de-casos-de-uso)
- [Integración con Core Data Platform](#integración-con-core-data-platform)
- [Guía de Implementación Paso a Paso](#guía-de-implementación-paso-a-paso)
- [Guía de Integración Android](#guía-de-integración-android)
- [Guía de Integración iOS](#guía-de-integración-ios)
- [Referencia de Manejo de Errores](#referencia-de-manejo-de-errores)
- [Estrategia de Testing](#estrategia-de-testing)
- [Versionado](#versionado)

---

## ¿Por qué este SDK?

| Ventaja | Detalle |
|---|---|
| **Dominio puro** | Tu lógica de negocio no depende de Room, Retrofit, Ktor, CoreData, SwiftUI ni Compose. Si mañana cambias de base de datos, el dominio no se entera. |
| **Sin excepciones** | Todos los contratos retornan `DomainResult<T>`. El error es un valor explícito y tipado, nunca una excepción que se propaga silenciosamente. |
| **Testeable sin mocking** | Todos los contratos son `fun interface`. En tests creas stubs con una lambda de una línea. Cero Mockito, cero MockK. |
| **Determinista** | El tiempo (`ClockProvider`) y la generación de IDs (`IdProvider`) son dependencias inyectadas. En tests pasas valores fijos → resultados 100% reproducibles. |
| **Composable** | Validadores se encadenan con `andThen`. Políticas se componen con `and` / `or` / `negate`. Results se combinan con `zip`. |
| **Multiplataforma** | Un solo módulo de dominio para Android + iOS + Desktop. El mismo código, las mismas reglas de negocio. |
| **Clean Architecture forzada** | El compilador impide que el dominio importe de data o UI. La dirección de dependencias se garantiza en tiempo de compilación. |

---

## Vista General de Arquitectura

```mermaid
graph TB
    subgraph "Capa de App"
        VM["ViewModel / Presenter"]
        DI["Wiring Manual / Framework DI"]
    end

    subgraph "Domain SDK (este módulo)"
        UC["Casos de Uso<br/><i>PureUseCase · SuspendUseCase · FlowUseCase</i>"]
        POL["Políticas<br/><i>DomainPolicy · SuspendDomainPolicy</i>"]
        VAL["Validadores<br/><i>Validator · andThen · validateAll</i>"]
        MOD["Modelo<br/><i>Entity · ValueObject · AggregateRoot · EntityId</i>"]
        ERR["DomainError<br/><i>Validation · NotFound · Conflict · …</i>"]
        RES["DomainResult&lt;T&gt;<br/><i>Success · Failure · map · flatMap · zip</i>"]
        REPO["Contratos de Repositorio<br/><i>ReadRepository · WriteRepository</i>"]
        GW["Contratos de Gateway<br/><i>SuspendGateway · CommandGateway</i>"]
        PROV["Providers<br/><i>ClockProvider · IdProvider</i>"]
        DEPS["DomainDependencies<br/><i>clock + idProvider</i>"]
    end

    subgraph "Capa de Datos (externa)"
        DB["Room / SQLDelight / CoreData"]
        NET["Ktor / Retrofit / URLSession"]
        IMPL["Implementaciones de Repository y Gateway"]
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

### Regla de Dependencia

```mermaid
graph LR
    DATA["Data SDK"] -->|"depende de"| DOMAIN["Domain SDK"]
    APP["Capa de App"] -->|"depende de"| DOMAIN
    APP -->|"depende de"| DATA
    DOMAIN -.->|"no conoce nada de"| DATA
    DOMAIN -.->|"no conoce nada de"| APP

    style DOMAIN fill:#4CAF50,color:#fff
    style DATA fill:#2196F3,color:#fff
    style APP fill:#FF9800,color:#fff
```

**El SDK de dominio define contratos. Las capas externas los implementan.**
El dominio nunca importa de data, UI ni ningún framework.

---

## Estructura de Paquetes

```
com.domain.core/
├── di/            → DomainDependencies, DomainModule
├── error/         → DomainError (jerarquía sealed)
├── result/        → DomainResult<T> + operadores (map, flatMap, zip, …)
├── model/         → Entity, ValueObject, AggregateRoot, EntityId
├── usecase/       → PureUseCase, SuspendUseCase, FlowUseCase, NoParams*
├── repository/    → Repository, ReadRepository, WriteRepository, ReadCollectionRepository
├── gateway/       → Gateway, SuspendGateway, CommandGateway
├── validation/    → Validator<T>, andThen, validateAll, collectValidationErrors
├── policy/        → DomainPolicy, SuspendDomainPolicy, and/or/negate
└── provider/      → ClockProvider, IdProvider
```

---

## Componentes Principales

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

### Jerarquía de DomainError

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

### Contratos de Casos de Uso

```mermaid
graph LR
    P["PureUseCase&lt;I, O&gt;<br/><i>síncrono, sin I/O</i>"] --> R["DomainResult&lt;O&gt;"]
    S["SuspendUseCase&lt;I, O&gt;<br/><i>asíncrono, valor único</i>"] --> R
    F["FlowUseCase&lt;I, O&gt;<br/><i>reactivo, múltiples valores</i>"] --> FR["Flow&lt;DomainResult&lt;O&gt;&gt;"]

    style P fill:#4CAF50,color:#fff
    style S fill:#2196F3,color:#fff
    style F fill:#9C27B0,color:#fff
```

### Flujo de Composición

```mermaid
sequenceDiagram
    participant App as Capa de App
    participant Deps as DomainDependencies
    participant Mod as FeatureDomainModule
    participant UC as Caso de Uso
    participant Repo as Repository (contrato)
    participant Data as Capa de Datos (impl)

    App->>Deps: Crear(clock, idProvider)
    App->>Data: Crear RepoImpl(db)
    App->>Mod: Crear ModuleImpl(deps, repo)
    App->>UC: module.crearUsuario(params)
    UC->>Repo: repo.save(entidad)
    Repo->>Data: La capa de datos maneja persistencia
    Data-->>Repo: DomainResult<Unit>
    Repo-->>UC: DomainResult<Unit>
    UC-->>App: DomainResult<User>
```

---

## DomainDependencies — El Contenedor de Infraestructura del Dominio

`DomainDependencies` es un `data class` inmutable que agrupa las **dos únicas dependencias de infraestructura** que el dominio necesita universalmente:

```kotlin
public data class DomainDependencies(
    val clock: ClockProvider,    // ¿Qué hora es?
    val idProvider: IdProvider,  // Dame un ID único
)
```

### El problema que resuelve

Cada vez que un caso de uso crea una entidad necesita un **ID** y un **timestamp**. Sin `DomainDependencies`:

```kotlin
// ❌ Impuro — side-effects directos dentro del dominio
val task = Task(
    id = TaskId(UUID.randomUUID().toString()),   // acoplado a JVM, no compila en iOS
    createdAt = System.currentTimeMillis(),        // resultado distinto en cada ejecución
)
```

**Problemas:** No testeable (cada ejecución genera valores distintos → imposible `assertEquals`), acoplado a plataforma (no compila en iOS), y no reproducible.

Con `DomainDependencies`:

```kotlin
// ✅ Determinista — el dominio no sabe de dónde vienen los valores
val task = Task(
    id = TaskId(deps.idProvider.next()),
    createdAt = deps.clock.nowMillis(),
)
```

### Cómo se configura

**En producción** — la app crea una sola instancia y la pasa a todos los use cases:

```kotlin
// Android (Application.onCreate) o iOS (AppDelegate)
val domainDeps = DomainDependencies(
    clock = ClockProvider { System.currentTimeMillis() },
    idProvider = IdProvider { UUID.randomUUID().toString() },
)
```

**En tests** — valores fijos, resultados 100% reproducibles:

```kotlin
val testDeps = DomainDependencies(
    clock = ClockProvider { 1_700_000_000_000L },
    idProvider = IdProvider { "fixed-id-123" },
)

// Ahora puedes verificar con exactitud:
val result = createTask(CreateTaskParams("Comprar leche"))
val task = result.getOrNull()!!
assertEquals("fixed-id-123", task.id.value)          // ✅ siempre pasa
assertEquals(1_700_000_000_000L, task.createdAt)     // ✅ siempre pasa
```

### ¿Por qué agruparlos en un solo objeto?

Si tienes 20 use cases que crean entidades, sin `DomainDependencies` pasarías `clock` + `idProvider` como **40 parámetros individuales**. Con el contenedor, son **20 parámetros** (`deps`).

### ¿Qué NO va aquí?

Repositorios y gateways de features se inyectan **directamente** en cada use case. Solo va aquí lo **cross-cutting** (que prácticamente todos los use cases necesitan):

```kotlin
class PlaceOrderUseCase(
    private val deps: DomainDependencies,          // ✅ cross-cutting: clock + id
    private val orderRepository: OrderRepository,  // ✅ específico de esta feature
    private val inventoryGateway: SuspendGateway<...>,
) : SuspendUseCase<PlaceOrderParams, Order> { ... }
```

---

## Referencia Completa de Casos de Uso

El SDK provee **5 contratos de caso de uso**. Todos son `fun interface` (SAM), lo que permite
implementarlos como lambdas o como clases:

| Contrato | Firma | Cuándo usar |
|---|---|---|
| `PureUseCase<I, O>` | `(I) → DomainResult<O>` | Lógica síncrona y pura. Sin I/O. Seguro desde main thread |
| `SuspendUseCase<I, O>` | `suspend (I) → DomainResult<O>` | Operaciones async de resultado único (crear, actualizar, buscar) |
| `FlowUseCase<I, O>` | `(I) → Flow<DomainResult<O>>` | Observar cambios en el tiempo (streams reactivos) |
| `NoParamsUseCase<O>` | `suspend () → DomainResult<O>` | Variante sin parámetros de `SuspendUseCase` |
| `NoParamsFlowUseCase<O>` | `() → Flow<DomainResult<O>>` | Variante sin parámetros de `FlowUseCase` |

> Todos retornan `DomainResult<T>`. Nunca lanzan excepciones al consumidor.

### 1. PureUseCase — Lógica síncrona y pura

Para cálculos, transformaciones, reglas de negocio que **no tocan red ni disco**.
Seguro desde cualquier hilo, incluyendo el main thread.

**Escenario real:** Un banco necesita evaluar si un cliente califica para un préstamo
personal. Las reglas dependen del ingreso mensual, el porcentaje de endeudamiento
y el score crediticio. No hay I/O — son reglas de negocio puras.

```kotlin
data class LoanEligibilityParams(
    val monthlyIncome: Double,
    val currentDebtPercent: Double,
    val creditScore: Int,
    val requestedAmount: Double,
)

data class LoanEligibilityResult(
    val approved: Boolean,
    val maxApprovedAmount: Double,
    val interestRate: Double,
    val reason: String,
)

class EvaluateLoanEligibility : PureUseCase<LoanEligibilityParams, LoanEligibilityResult> {

    override fun invoke(params: LoanEligibilityParams): DomainResult<LoanEligibilityResult> {
        // Validar input
        if (params.monthlyIncome <= 0)
            return domainFailure(DomainError.Validation("monthlyIncome", "debe ser positivo"))
        if (params.creditScore !in 300..850)
            return domainFailure(DomainError.Validation("creditScore", "debe estar entre 300 y 850"))

        // Regla 1: No prestar si el endeudamiento supera el 40%
        if (params.currentDebtPercent > 40.0) {
            return LoanEligibilityResult(
                approved = false,
                maxApprovedAmount = 0.0,
                interestRate = 0.0,
                reason = "Endeudamiento actual supera el 40%",
            ).asSuccess()
        }

        // Regla 2: Determinar tasa de interés según score crediticio
        val interestRate = when {
            params.creditScore >= 750 -> 8.5
            params.creditScore >= 650 -> 12.0
            params.creditScore >= 550 -> 18.5
            else -> return LoanEligibilityResult(
                approved = false,
                maxApprovedAmount = 0.0,
                interestRate = 0.0,
                reason = "Score crediticio insuficiente (mínimo 550)",
            ).asSuccess()
        }

        // Regla 3: Monto máximo = 5x ingreso mensual
        val maxAmount = params.monthlyIncome * 5
        val approved = params.requestedAmount <= maxAmount

        return LoanEligibilityResult(
            approved = approved,
            maxApprovedAmount = maxAmount,
            interestRate = interestRate,
            reason = if (approved) "Aprobado" else "Monto solicitado excede el máximo permitido",
        ).asSuccess()
    }
}

// Uso — síncrono, se puede llamar desde el main thread sin problema:
val result = evaluateLoanEligibility(
    LoanEligibilityParams(
        monthlyIncome = 45_000.0,
        currentDebtPercent = 25.0,
        creditScore = 720,
        requestedAmount = 150_000.0,
    )
)
// → Success(LoanEligibilityResult(approved=true, maxApprovedAmount=225000.0, interestRate=12.0, ...))
```

### 2. SuspendUseCase — Operación async de resultado único

Para operaciones que requieren I/O (persistir, consultar, llamar API) y producen **un solo valor**.

**Escenario real:** Un e-commerce necesita procesar una orden de compra. El caso de uso
valida el carrito, verifica el inventario, calcula el total con impuestos, reserva el stock
y persiste la orden. Todo en una sola transacción lógica.

```kotlin
data class PlaceOrderParams(
    val cartId: CartId,
    val shippingAddressId: AddressId,
    val paymentMethodId: PaymentMethodId,
)

class PlaceOrderUseCase(
    private val deps: DomainDependencies,
    private val cartRepository: CartRepository,
    private val inventoryGateway: SuspendGateway<List<CartItem>, InventoryCheckResult>,
    private val orderRepository: OrderRepository,
    private val taxCalculator: PureUseCase<TaxParams, TaxResult>,
) : SuspendUseCase<PlaceOrderParams, Order> {

    override suspend fun invoke(params: PlaceOrderParams): DomainResult<Order> {
        // 1. Obtener el carrito
        val cart = cartRepository.findById(params.cartId).getOrElse { return domainFailure(it) }
            ?: return domainFailure(DomainError.NotFound("Cart", params.cartId.value))

        if (cart.items.isEmpty())
            return domainFailure(DomainError.Validation("cart", "El carrito está vacío"))

        // 2. Verificar inventario disponible
        val inventoryCheck = inventoryGateway.execute(cart.items).getOrElse { return domainFailure(it) }
        if (!inventoryCheck.allAvailable)
            return domainFailure(DomainError.Conflict(
                detail = "Sin stock: ${inventoryCheck.unavailableItems.joinToString { it.name }}"
            ))

        // 3. Calcular impuestos (lógica pura — PureUseCase)
        val tax = taxCalculator(TaxParams(cart.subtotal, cart.shippingState))
            .getOrElse { return domainFailure(it) }

        // 4. Crear la orden
        val order = Order(
            id = OrderId(deps.idProvider.next()),
            items = cart.items,
            subtotal = cart.subtotal,
            tax = tax.amount,
            total = cart.subtotal + tax.amount,
            status = OrderStatus.CONFIRMED,
            createdAt = deps.clock.nowMillis(),
        )

        // 5. Persistir
        return orderRepository.save(order).map { order }
    }
}

// Uso:
viewModelScope.launch {
    placeOrder(PlaceOrderParams(cartId, addressId, paymentId))
        .onSuccess { order -> navigateToConfirmation(order.id) }
        .onFailure { error ->
            when (error) {
                is DomainError.Conflict -> showOutOfStockDialog(error.detail)
                is DomainError.Validation -> showCartError(error.detail)
                else -> showGenericError(error.message)
            }
        }
}
```

### 3. FlowUseCase — Observar datos que cambian en el tiempo

Para streams reactivos: datos que se actualizan, notificaciones en tiempo real, cambios de estado.
Cada emisión puede ser éxito o fallo individualmente sin cancelar el stream.

**Escenario real:** Un dashboard de logística donde un operador observa los envíos
filtrados por estado (pendiente, en tránsito, entregado). Cada vez que un envío
cambia de estado, la lista se actualiza automáticamente.

```kotlin
data class ShipmentFilterParams(
    val status: ShipmentStatus,
    val warehouseId: WarehouseId,
)

class ObserveShipmentsByStatus(
    private val shipmentRepository: ShipmentRepository,
) : FlowUseCase<ShipmentFilterParams, List<Shipment>> {

    override fun invoke(params: ShipmentFilterParams): Flow<DomainResult<List<Shipment>>> {
        return shipmentRepository
            .observeByWarehouseAndStatus(params.warehouseId, params.status)
    }
}

// Uso — en el ViewModel:
init {
    viewModelScope.launch {
        observeShipmentsByStatus(
            ShipmentFilterParams(
                status = ShipmentStatus.IN_TRANSIT,
                warehouseId = currentWarehouseId,
            )
        ).collect { result ->
            result
                .onSuccess { shipments ->
                    _uiState.value = DashboardState.Loaded(
                        shipments = shipments,
                        count = shipments.size,
                    )
                }
                .onFailure { error ->
                    _uiState.value = DashboardState.Error(error.message)
                }
        }
    }
}
```

### 4. NoParamsUseCase — Async sin parámetros

Igual que `SuspendUseCase` pero cuando **no necesitas parámetros**. Evita pasar `Unit`.

**Escenario real:** Una app de salud necesita cerrar la sesión del usuario. Esto implica
invalidar el token en el servidor, limpiar la caché local y registrar el evento de logout.
No necesita parámetros — la sesión activa determina todo.

```kotlin
class LogoutUseCase(
    private val sessionGateway: CommandGateway<LogoutCommand>,
    private val localCacheGateway: CommandGateway<ClearCacheCommand>,
    private val auditRepository: WriteRepository<AuditEvent>,
    private val deps: DomainDependencies,
) : NoParamsUseCase<Unit> {

    override suspend fun invoke(): DomainResult<Unit> {
        // 1. Invalidar token en el servidor
        sessionGateway.dispatch(LogoutCommand).getOrElse { return domainFailure(it) }

        // 2. Limpiar datos locales sensibles
        localCacheGateway.dispatch(ClearCacheCommand).getOrElse { return domainFailure(it) }

        // 3. Registrar evento de auditoría
        val event = AuditEvent(
            id = AuditEventId(deps.idProvider.next()),
            type = "LOGOUT",
            timestamp = deps.clock.nowMillis(),
        )
        return auditRepository.save(event)
    }
}

// Uso — sin parámetros:
viewModelScope.launch {
    logout()
        .onSuccess { navigateToLogin() }
        .onFailure { error -> showError("No se pudo cerrar sesión: ${error.message}") }
}
```

### 5. NoParamsFlowUseCase — Stream reactivo sin parámetros

Ideal para observar datos globales que no requieren filtro.

**Escenario real:** Una app de fintech necesita mostrar el valor total del portafolio
de inversiones del usuario actualizado en tiempo real. El valor cambia conforme
fluctúan los precios de los activos. No necesita parámetros — el usuario autenticado
determina qué portafolio observar.

```kotlin
class ObservePortfolioValue(
    private val portfolioRepository: PortfolioRepository,
) : NoParamsFlowUseCase<PortfolioSummary> {

    override fun invoke(): Flow<DomainResult<PortfolioSummary>> {
        return portfolioRepository.observeCurrentUserPortfolio()
        // Emite cada vez que cambia un precio o se ejecuta una transacción
    }
}

data class PortfolioSummary(
    val totalValue: Double,
    val dailyChange: Double,
    val dailyChangePercent: Double,
    val holdings: List<Holding>,
)

// Uso — en el ViewModel de la pantalla principal:
init {
    viewModelScope.launch {
        observePortfolioValue()
            .collect { result ->
                result
                    .onSuccess { summary ->
                        _uiState.value = HomeState.Loaded(
                            totalValue = summary.totalValue,
                            changePercent = summary.dailyChangePercent,
                            isPositive = summary.dailyChange >= 0,
                        )
                    }
                    .onFailure { error ->
                        _uiState.value = HomeState.Error(error.message)
                    }
            }
    }
}
```

### Diagrama de decisión

```mermaid
flowchart TD
    A["¿Qué tipo de caso de uso necesito?"] --> B{"¿Necesita I/O?"}
    B -->|"No — cálculo puro"| C["PureUseCase"]
    B -->|"Sí"| D{"¿Retorna un stream?"}
    D -->|"No — valor único"| E{"¿Necesita parámetros?"}
    D -->|"Sí — múltiples valores"| F{"¿Necesita parámetros?"}
    E -->|"Sí"| G["SuspendUseCase"]
    E -->|"No"| H["NoParamsUseCase"]
    F -->|"Sí"| I["FlowUseCase"]
    F -->|"No"| J["NoParamsFlowUseCase"]

    style C fill:#4CAF50,color:#fff
    style G fill:#2196F3,color:#fff
    style H fill:#2196F3,color:#fff
    style I fill:#9C27B0,color:#fff
    style J fill:#9C27B0,color:#fff
```

---

## Integración con Core Data Platform

El **Core Domain Platform** (este SDK) define los contratos del dominio. El **[Core Data Platform](https://github.com/DanCrRdz93/core-data-platform)** implementa el acceso a datos remotos (HTTP, seguridad, sesiones). Ambos son KMP y están **100% alineados** en versiones:

| | Domain SDK | Data SDK |
|---|---|---|
| Kotlin | 2.1.20 | 2.1.20 |
| Coroutines | 1.10.1 | 1.10.1 |
| Gradle | 9.3.1 | 9.3.1 |

### Arquitectura completa

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          TU APP (Android/iOS)                           │
│                                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                              │
│  │Feature A │  │Feature B │  │Feature C │  ← ViewModels                 │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘                              │
│       │              │              │                                    │
│  ┌────▼──────────────▼──────────────▼────────────────┐                  │
│  │     Domain Use Cases (este SDK)                   │                  │
│  │  PureUseCase · SuspendUseCase · FlowUseCase       │                  │
│  │  (genera operationId, decide retryPolicyOverride)  │                  │
│  └────┬──────────────┬──────────────┬────────────────┘                  │
│       │              │              │                                    │
│  ┌────▼──────────────▼──────────────▼────────────────┐                  │
│  │   Repository / Gateway Implementations            │  ← TÚ LOS       │
│  │   (puente entre ambos SDKs)                       │    ESCRIBES      │
│  │   Propaga: RequestContext, ResultMetadata          │                  │
│  └────┬──────────────┬──────────┬────────────────────┘                  │
│       │              │          │                                        │
│  ┌────▼────┐  ┌──────▼────┐  ┌─▼───────────────────────────────────┐   │
│  │network  │  │ network   │  │  security-core                      │   │
│  │ -core   │  │  -ktor    │  │  SessionController · CredentialProv │   │
│  │         │  │           │  │  SecretStore · TrustPolicy          │   │
│  └─────────┘  └───────────┘  └─────────────────────────────────────┘   │
│                 ↑ Data SDK                                              │
│  Observability: LoggingObserver · MetricsObserver · TracingObserver     │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1. Mapeo de errores: `NetworkError` → `DomainError`

> **Nota:** `NetworkError` **no** es `Throwable`. Cada subtipo tiene `diagnostic: Diagnostic?`
> donde `Diagnostic.cause: Throwable?` contiene la excepción original. El mapeo debe pasar
> `diagnostic?.cause` a `DomainError.Infrastructure.cause`, no el `NetworkError` en sí.

```kotlin
// Extensión que vive en tu capa de datos (NO en ninguno de los dos SDKs)
fun NetworkError.toDomainError(): DomainError = when (this) {
    // ── Transporte ──
    is NetworkError.Connectivity -> DomainError.Infrastructure(
        detail = "Sin conexión a internet",
        cause = diagnostic?.cause,     // ← Throwable? del Data SDK
    )
    is NetworkError.Timeout -> DomainError.Infrastructure(
        detail = "Tiempo de espera agotado",
        cause = diagnostic?.cause,
    )
    is NetworkError.Cancelled -> DomainError.Cancelled(
        detail = "Solicitud cancelada",  // ← Intencional, no falla de infra
    )

    // ── HTTP semántico ──
    is NetworkError.Authentication -> DomainError.Unauthorized("Autenticación requerida")
    is NetworkError.Authorization  -> DomainError.Unauthorized("Acceso denegado")
    is NetworkError.ClientError -> when (statusCode) {
        404  -> DomainError.NotFound("Recurso", diagnostic?.description ?: "")
        409  -> DomainError.Conflict(message)
        422  -> DomainError.Validation("request", diagnostic?.description ?: message)
        else -> DomainError.Infrastructure("Error HTTP $statusCode", diagnostic?.cause)
    }
    is NetworkError.ServerError -> DomainError.Infrastructure(
        detail = "Error del servidor ($statusCode)",
        cause = diagnostic?.cause,
    )

    // ── Procesamiento de datos ──
    is NetworkError.Serialization -> DomainError.Infrastructure(
        detail = "Error al procesar respuesta",
        cause = diagnostic?.cause,
    )
    is NetworkError.ResponseValidation -> DomainError.Infrastructure(
        detail = reason,
        cause = diagnostic?.cause,
    )

    // ── Catch-all ──
    is NetworkError.Unknown -> DomainError.Unknown(
        detail = message,
        cause = diagnostic?.cause,
    )
}
```

**¿Por qué `Cancelled` no es `Infrastructure`?** Una cancelación es intencional (el usuario
navegó fuera, el scope de coroutine se canceló). Mapearla como `Infrastructure` la haría
indistinguible de una caída de servidor en el `when` del ViewModel. Con `DomainError.Cancelled`
el consumidor puede suprimirla sin mostrar diálogo de error.

### 2. Implementación de un Repository (el puente)

> **Escenario:** Un use case necesita obtener un usuario por ID. El DataSource del Data SDK
> retorna `NetworkResult<UserDto>`. El repository convierte DTO → modelo y error → DomainError.

```kotlin
class UserRepositoryImpl(
    private val dataSource: UserRemoteDataSource,      // ← Data SDK
    private val mapper: Mapper<UserDto, User>,          // ← Domain SDK contrato
) : ReadRepository<UserId, User> {                      // ← Domain SDK contrato

    override suspend fun findById(id: UserId): DomainResult<User?> {
        val networkResult = dataSource.getUser(id.value)
        return networkResult.fold(
            onSuccess = { dto -> mapper.map(dto).asSuccess() },
            onFailure = { error -> domainFailure(error.toDomainError()) },
        )
    }
}
```

### 3. Propagación de `ResponseMetadata` al dominio

> **Escenario:** Un equipo de soporte necesita que el ViewModel muestre el `requestId`
> cuando una operación falla, para que el usuario pueda reportarlo. También se quiere
> medir la latencia percibida (`durationMs`) y cuántos reintentos se hicieron (`attemptCount`).

```kotlin
class UserRepositoryWithMeta(
    private val dataSource: UserRemoteDataSource,
    private val mapper: Mapper<UserDto, User>,
) : ReadRepository<UserId, User> {

    // Retorna DomainResultWithMeta para que el ViewModel acceda a metadata
    suspend fun findByIdWithMeta(id: UserId): DomainResultWithMeta<User?> {
        val networkResult = dataSource.getUser(id.value)
        return when (networkResult) {
            is NetworkResult.Success -> DomainResultWithMeta(
                result = mapper.map(networkResult.data).asSuccess(),
                metadata = networkResult.metadata.toDomainMeta(),
            )
            is NetworkResult.Failure -> DomainResultWithMeta(
                result = domainFailure(networkResult.error.toDomainError()),
                // En failure también puedes propagar metadata si la tienes
            )
        }
    }

    // Contrato estándar (sin metadata) sigue disponible
    override suspend fun findById(id: UserId): DomainResult<User?> =
        findByIdWithMeta(id).result
}

// Extensión en tu capa de datos
fun ResponseMetadata.toDomainMeta(): ResultMetadata = ResultMetadata(
    requestId = requestId,
    durationMs = durationMs,
    attemptCount = attemptCount,
    extra = buildMap {
        headers["X-RateLimit-Remaining"]?.firstOrNull()?.let { put("rateLimitRemaining", it) }
        headers["ETag"]?.firstOrNull()?.let { put("etag", it) }
    },
)
```

**Uso en el ViewModel:**

```kotlin
val (result, meta) = userRepo.findByIdWithMeta(userId)
result.fold(
    onSuccess = { user -> showUser(user) },
    onFailure = { error ->
        showError(error.message)
        // El usuario puede reportar el requestId al soporte
        analytics.logError(requestId = meta.requestId, duration = meta.durationMs)
    },
)
```

### 4. `RequestContext` — correlación dominio → HTTP

> **Escenario:** El equipo de SRE quiere ver en Datadog qué use case generó cada request HTTP.
> `PlaceOrderUseCase` genera un `operationId = "place-order"` que viaja hasta los headers HTTP
> para correlacionar la operación de dominio con la traza de infraestructura.

```kotlin
// ── Contrato en tu capa de dominio (interface del repository) ──
interface OrderRepository : WriteRepository<Order> {
    suspend fun placeOrder(order: Order, operationId: String): DomainResult<Unit>
}

// ── Implementación en tu capa de datos (puente) ──
class OrderRepositoryImpl(
    private val dataSource: OrderRemoteDataSource,
) : OrderRepository {

    override suspend fun placeOrder(order: Order, operationId: String): DomainResult<Unit> {
        val context = RequestContext(
            operationId = operationId,              // ← Viene del use case
            tags = mapOf("orderId" to order.id),    // ← Contexto de negocio
            requiresAuth = true,
        )
        val result = dataSource.createOrder(order.toDto(), context)
        return result.fold(
            onSuccess = { Unit.asSuccess() },
            onFailure = { error -> domainFailure(error.toDomainError()) },
        )
    }

    // save/delete estándar para WriteRepository
    override suspend fun save(entity: Order) = placeOrder(entity, "save-order")
    override suspend fun delete(entity: Order): DomainResult<Unit> { /* ... */ }
}

// ── Use case genera el operationId ──
class PlaceOrderUseCase(
    private val orderRepo: OrderRepository,
    private val deps: DomainDependencies,
) : SuspendUseCase<PlaceOrderInput, Unit> {

    override suspend fun invoke(input: PlaceOrderInput): DomainResult<Unit> {
        val order = Order(id = deps.idProvider.generate(), /* ... */)
        return orderRepo.placeOrder(order, operationId = "place-order")
    }
}
```

En Datadog verás: `operation_id=place-order, orderId=abc-123` vinculado al request HTTP.

### 5. `RetryPolicy` override desde el dominio

> **Escenario:** Un pago NO debe reintentarse — reintentar podría cobrar dos veces al usuario.
> El use case indica al repository que esta operación es `RetryPolicy.None`.

```kotlin
// ── Contrato del repository (dominio) ──
interface PaymentRepository : Repository {
    suspend fun processPayment(payment: Payment, allowRetry: Boolean = false): DomainResult<PaymentResult>
}

// ── Implementación (puente) ──
class PaymentRepositoryImpl(
    private val dataSource: PaymentRemoteDataSource,
) : PaymentRepository {

    override suspend fun processPayment(
        payment: Payment,
        allowRetry: Boolean,
    ): DomainResult<PaymentResult> {
        val context = RequestContext(
            operationId = "process-payment",
            // Si el dominio dice no reintentar, override la policy
            retryPolicyOverride = if (!allowRetry) RetryPolicy.None else null,
            requiresAuth = true,
        )
        val result = dataSource.charge(payment.toDto(), context)
        return result.fold(
            onSuccess = { dto -> PaymentResult(dto.transactionId).asSuccess() },
            onFailure = { error -> domainFailure(error.toDomainError()) },
        )
    }
}

// ── Use case ──
class ProcessPaymentUseCase(
    private val paymentRepo: PaymentRepository,
) : SuspendUseCase<Payment, PaymentResult> {

    override suspend fun invoke(input: Payment): DomainResult<PaymentResult> =
        paymentRepo.processPayment(input, allowRetry = false) // ← NUNCA reintentar pagos
}
```

### 6. Ciclo de vida completo de sesión

> **Escenario:** La app necesita: login, logout, force-logout por 401, refresh de token,
> y observar eventos de sesión (para analytics y para actualizar la UI).

```kotlin
// ── Adapter: login ──
class LoginGateway(
    private val session: SessionController,
) : CommandGateway<SessionCredentials> {

    override suspend fun dispatch(input: SessionCredentials): DomainResult<Unit> =
        runDomainCatching { session.startSession(input) }
}

// ── Adapter: logout voluntario ──
class LogoutGateway(
    private val session: SessionController,
) : CommandGateway<Unit> {

    override suspend fun dispatch(input: Unit): DomainResult<Unit> =
        runDomainCatching { session.endSession() }
}

// ── Adapter: force-logout (401 o seguridad comprometida) ──
class ForceLogoutGateway(
    private val session: SessionController,
) : CommandGateway<Unit> {

    override suspend fun dispatch(input: Unit): DomainResult<Unit> =
        runDomainCatching { session.invalidate() }
}

// ── Adapter: refresh de token ──
// RefreshOutcome es un sealed del Data SDK: Refreshed, NotNeeded, Failed
class RefreshSessionGateway(
    private val session: SessionController,
) : SuspendGateway<Unit, RefreshOutcome> {

    override suspend fun execute(input: Unit): DomainResult<RefreshOutcome> =
        runDomainCatching { session.refreshSession() }
}

// ── Adapter: estado de sesión (StateFlow → FlowGateway) ──
class SessionStateGateway(
    private val session: SessionController,
) : NoParamsFlowGateway<Boolean> {

    override fun observe(): Flow<DomainResult<Boolean>> =
        session.state.map { state ->
            (state is SessionState.Active).asSuccess()
        }
}

// ── Adapter: eventos de sesión para analytics ──
class SessionEventsGateway(
    private val session: SessionController,
) : NoParamsFlowGateway<SessionEvent> {

    override fun observe(): Flow<DomainResult<SessionEvent>> =
        session.events.map { event -> event.asSuccess() }
}
```

**Uso en un use case:**

```kotlin
class LogoutUseCase(
    private val logout: LogoutGateway,
    private val clearCache: ClearCacheGateway, // otro gateway tuyo
) : SuspendUseCase<Unit, Unit> {

    override suspend fun invoke(input: Unit): DomainResult<Unit> =
        logout.dispatch(Unit).flatMap { clearCache.dispatch(Unit) }
}
```

### 7. Múltiples APIs con diferentes configuraciones

> **Escenario:** `PlaceOrderUseCase` orquesta 3 APIs (orders, inventory, payments) cada una
> con su propio `NetworkConfig`, executor y URL base.

```kotlin
fun provideMultiApiDependencies(): AppDependencies {
    // ── Configuraciones por API ──
    val ordersConfig = NetworkConfig(
        baseUrl = "https://orders.api.example.com",
        connectTimeout = 10_000L,
        retryPolicy = RetryPolicy.ExponentialBackoff(maxRetries = 3),
    )
    val inventoryConfig = NetworkConfig(
        baseUrl = "https://inventory.api.example.com",
        connectTimeout = 5_000L,
        retryPolicy = RetryPolicy.FixedDelay(maxRetries = 2, delay = 1_000L),
    )
    val paymentsConfig = NetworkConfig(
        baseUrl = "https://payments.api.example.com",
        connectTimeout = 30_000L,
        retryPolicy = RetryPolicy.None, // ← Pagos: NUNCA reintentar por defecto
    )

    // ── Executors independientes por API ──
    val ordersExecutor = DefaultSafeRequestExecutor(
        engine = KtorHttpEngine.create(ordersConfig),
        config = ordersConfig,
        classifier = KtorErrorClassifier(),
        observers = listOf(loggingObserver, metricsObserver),
    )
    val inventoryExecutor = DefaultSafeRequestExecutor(
        engine = KtorHttpEngine.create(inventoryConfig),
        config = inventoryConfig,
        classifier = KtorErrorClassifier(),
    )
    val paymentsExecutor = DefaultSafeRequestExecutor(
        engine = KtorHttpEngine.create(paymentsConfig, bankTrustPolicy), // ← Gap 9: TrustPolicy
        config = paymentsConfig,
        classifier = KtorErrorClassifier(),
    )

    // ── DataSources ──
    val orderDataSource = OrderRemoteDataSource(ordersExecutor)
    val inventoryDataSource = InventoryRemoteDataSource(inventoryExecutor)
    val paymentDataSource = PaymentRemoteDataSource(paymentsExecutor)

    // ── Repositories (puente) ──
    val orderRepo = OrderRepositoryImpl(orderDataSource)
    val inventoryGateway = InventoryGatewayImpl(inventoryDataSource)
    val paymentRepo = PaymentRepositoryImpl(paymentDataSource)

    // ── Use case orquesta las 3 APIs ──
    val placeOrder = PlaceOrderUseCase(orderRepo, inventoryGateway, paymentRepo, domainDeps)

    return AppDependencies(placeOrder, /* ... */)
}
```

### 8. Exposición de Rate Limits al dominio

> **Escenario:** La API retorna `X-RateLimit-Remaining` en headers. El dominio necesita
> un gateway que exponga el rate limit actual para decidir si hacer throttling preventivo
> (ej., deshabilitar el botón "Actualizar" si quedan pocas solicitudes).

```kotlin
// ── Gateway reactivo (dominio) ──
interface RateLimitGateway : NoParamsFlowGateway<Int> // Remaining count

// ── Implementación (puente) — usa ResponseInterceptor del Data SDK ──
class RateLimitGatewayImpl : RateLimitGateway, ResponseInterceptor {
    private val _remaining = MutableStateFlow(Int.MAX_VALUE)

    // ResponseInterceptor del Data SDK — intercepta cada respuesta HTTP
    override suspend fun intercept(response: InterceptedResponse): InterceptedResponse {
        response.headers["X-RateLimit-Remaining"]?.firstOrNull()?.toIntOrNull()?.let {
            _remaining.value = it
        }
        return response
    }

    // NoParamsFlowGateway del Domain SDK — expone al dominio
    override fun observe(): Flow<DomainResult<Int>> =
        _remaining.map { it.asSuccess() }
}

// Registrar como ResponseInterceptor al crear el executor:
val rateLimitGateway = RateLimitGatewayImpl()
val executor = DefaultSafeRequestExecutor(
    engine = engine,
    config = config,
    classifier = KtorErrorClassifier(),
    responseInterceptors = listOf(rateLimitGateway), // ← doble rol
)
```

### 9. `TrustPolicy` y Certificate Pinning en el wiring

> **Escenario:** Una app de banca necesita protección contra ataques MITM.
> El Data SDK soporta `TrustPolicy` con `CertificatePin` que valida los
> certificados del servidor contra pins conocidos.

```kotlin
// ── TrustPolicy para banca / salud ──
val bankTrustPolicy = DefaultTrustPolicy(
    pins = listOf(
        CertificatePin(
            hostname = "payments.api.example.com",
            sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", // SHA-256 del cert
        ),
        CertificatePin(
            hostname = "payments.api.example.com",
            sha256 = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=", // Backup pin
        ),
    ),
)

// Pasar al crear el engine
val secureEngine = KtorHttpEngine.create(
    config = paymentsConfig,
    trustPolicy = bankTrustPolicy,    // ← Pinning activo
)
val secureExecutor = DefaultSafeRequestExecutor(
    engine = secureEngine,
    config = paymentsConfig,
    classifier = KtorErrorClassifier(),
)
```

### 10. Observers en el wiring

> **Escenario:** Necesitas logging de requests HTTP en desarrollo, métricas de latencia
> en producción, y un observer custom que reporte errores a Crashlytics.

```kotlin
// ── LoggingObserver (Data SDK) — solo en debug ──
val loggingObserver = LoggingObserver(
    logger = { tag, msg -> println("[$tag] $msg") },
    tag = "HTTP",
    headerSanitizer = { key, value ->
        if (key.equals("Authorization", ignoreCase = true)) "***" else value
    },
)

// ── Observer custom: reporte a Crashlytics ──
val crashlyticsObserver = object : NetworkEventObserver {
    override fun onRequestStarted(url: String, method: String) { /* no-op */ }
    override fun onResponseReceived(url: String, statusCode: Int, durationMs: Long) {
        if (statusCode >= 500) {
            crashlytics.log("Server error: $url → $statusCode (${durationMs}ms)")
        }
    }
    override fun onRetryScheduled(url: String, attempt: Int, delayMs: Long) {
        crashlytics.log("Retry #$attempt for $url in ${delayMs}ms")
    }
    override fun onRequestFailed(url: String, error: NetworkError) {
        crashlytics.recordError(error.diagnostic?.cause ?: Exception(error.message))
    }
}

// ── Ensamblar en el executor ──
val executor = DefaultSafeRequestExecutor(
    engine = KtorHttpEngine.create(config),
    config = config,
    classifier = KtorErrorClassifier(),
    observers = listOf(loggingObserver, crashlyticsObserver), // ← ambos activos
)
```

### Tabla de correspondencia completa

| Data SDK | Domain SDK | Dónde se conectan |
|---|---|---|
| `NetworkResult<T>` | `DomainResult<T>` | Repository impl con `fold` |
| `NetworkResult.Success.metadata` | `ResultMetadata` / `DomainResultWithMeta` | `ResponseMetadata.toDomainMeta()` |
| `NetworkError.*` (10 subtipos) | `DomainError.*` (7 subtipos) | `NetworkError.toDomainError()` |
| `NetworkError.Cancelled` | `DomainError.Cancelled` | Mapeo semántico correcto |
| `NetworkError.diagnostic?.cause` | `DomainError.Infrastructure.cause` | `Throwable?` preservado |
| `RequestContext.operationId` | Use case genera el ID | Propagado vía repository impl |
| `RequestContext.retryPolicyOverride` | Use case decide allowRetry | Repository propaga a `RequestContext` |
| `ResponseMetadata.headers` | `ResultMetadata.extra` | Rate limits, ETags, etc. |
| `SessionController.startSession` | `CommandGateway<SessionCredentials>` | `LoginGateway` adapter |
| `SessionController.endSession` | `CommandGateway<Unit>` | `LogoutGateway` adapter |
| `SessionController.invalidate` | `CommandGateway<Unit>` | `ForceLogoutGateway` adapter |
| `SessionController.refreshSession` | `SuspendGateway<Unit, RefreshOutcome>` | `RefreshSessionGateway` adapter |
| `SessionController.state` | `NoParamsFlowGateway<Boolean>` | `SessionStateGateway` adapter |
| `SessionController.events` | `NoParamsFlowGateway<SessionEvent>` | `SessionEventsGateway` adapter |
| `ResponseInterceptor` | `NoParamsFlowGateway<Int>` | `RateLimitGatewayImpl` (doble rol) |
| `NetworkConfig` (por API) | Múltiples executors | Wiring multi-API |
| `TrustPolicy` / `CertificatePin` | N/A (infra pura) | `KtorHttpEngine.create(config, trustPolicy)` |
| `NetworkEventObserver` | N/A (infra pura) | `DefaultSafeRequestExecutor(observers = ...)` |
| `LoggingObserver` | N/A (infra pura) | Configurado en el wiring |
| DTOs (`@Serializable`) | Domain models (puros) | `Mapper<Dto, Model>` |
| Batch HTTP requests | `WriteRepository.saveAll()` | Repository impl override |

---

## Guía de Implementación Paso a Paso

Esta guía te lleva paso a paso a través de la integración del SDK en un proyecto KMP nuevo o existente.
Sigue cada paso en orden.

### Paso 1 — Agregar el SDK como dependencia

**Escenario:** Tienes un proyecto KMP y quieres usar este SDK como tu capa de dominio.

Agrega el módulo del SDK a tu proyecto. Si es un módulo local:

```kotlin
// settings.gradle.kts
include(":coredomainplatform")
project(":coredomainplatform").projectDir = file("ruta/a/coredomainplatform")
```

Luego en tu módulo de feature o app:

```kotlin
// build.gradle.kts de tu módulo app/feature
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

### Paso 2 — Definir tus modelos de dominio

**Escenario:** Estás construyendo una feature de gestión de tareas y necesitas una entidad `Task` con un ID tipado.

```kotlin
// En el paquete de dominio de tu feature (NO en este SDK)
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

### Paso 3 — Definir tu contrato de repositorio

**Escenario:** Tu feature de `Task` necesita persistencia — el dominio define lo que necesita, no cómo se implementa.

```kotlin
package com.myapp.feature.task.repository

import com.domain.core.repository.ReadRepository
import com.domain.core.repository.WriteRepository
import com.myapp.feature.task.model.Task
import com.myapp.feature.task.model.TaskId

interface TaskRepository : ReadRepository<TaskId, Task>, WriteRepository<Task>
```

### Paso 4 — Crear validadores para tus reglas de dominio

**Escenario:** El título de una tarea no debe estar vacío y no debe exceder 200 caracteres.

```kotlin
package com.myapp.feature.task.validation

import com.domain.core.validation.notBlankValidator
import com.domain.core.validation.maxLengthValidator
import com.domain.core.validation.andThen

val taskTitleValidator = notBlankValidator("title")
    .andThen(maxLengthValidator("title", 200))
```

### Paso 5 — Crear políticas para reglas de negocio

**Escenario:** Una tarea solo puede ser completada si tiene un título (no vacío). Esta es una regla de negocio semántica, no solo validación de campo.

```kotlin
package com.myapp.feature.task.policy

import com.domain.core.error.DomainError
import com.domain.core.policy.DomainPolicy
import com.domain.core.result.asSuccess
import com.domain.core.result.domainFailure
import com.myapp.feature.task.model.Task

val canCompleteTask = DomainPolicy<Task> { task ->
    if (task.title.isNotBlank()) Unit.asSuccess()
    else domainFailure(DomainError.Conflict(detail = "No se puede completar una tarea sin título"))
}
```

### Paso 6 — Implementar tu caso de uso

**Escenario:** Crear una nueva tarea. El caso de uso valida input, genera un ID, timestamp, y persiste.

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
        // 1. Validar
        val validation = taskTitleValidator.validate(params.title)
        if (validation.isFailure) return validation as DomainResult<Task>

        // 2. Construir entidad
        val task = Task(
            id = TaskId(deps.idProvider.next()),
            title = params.title,
            completed = false,
            createdAt = deps.clock.nowMillis(),
        )

        // 3. Persistir y retornar
        return repository.save(task).flatMap { task.asSuccess() }
    }
}
```

### Paso 7 — Definir tu DomainModule de feature

**Escenario:** Exponer todos los casos de uso de la feature de tareas a través de un solo módulo composable.

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

### Paso 8 — Conectar todo en la capa de app

**Escenario:** El arranque de tu app crea todas las dependencias y ensambla todos los módulos.

```kotlin
// Capa de app — wiring. Este es el ÚNICO lugar donde los tipos concretos se encuentran.
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

### Paso 9 — Testear tu caso de uso

**Escenario:** Testear que `CreateTaskUseCase` produce una tarea con ID y timestamp deterministas.

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
    fun `crea tarea con id y timestamp inyectados`() = runTest {
        val result = useCase(CreateTaskParams("Comprar víveres"))
        val task = result.shouldBeSuccess()

        assertEquals("task-001", task.id.value)
        assertEquals(1_700_000_000_000L, task.createdAt)
        assertEquals("Comprar víveres", task.title)
        assertFalse(task.completed)
    }

    @Test
    fun `rechaza título vacío`() = runTest {
        val result = useCase(CreateTaskParams("   "))
        result.shouldFailWith<DomainError.Validation>()
    }
}
```

---

## Guía de Integración Android

> **Guía completa:** [GUIDE_ANDROID.md](GUIDE_ANDROID.md)

Documenta en detalle todos los contratos del SDK (Use Cases, DomainResult, DomainError,
Model, Repository, Gateway, Validators, Policies, Providers) y cómo inyectar los
casos de uso en tu ViewModel. Incluye ejemplos de implementación, validación, testing y FAQ.

---

## Guía de Integración iOS

> **Guía completa:** [GUIDE_IOS.md](GUIDE_IOS.md)

Documenta todos los contratos del SDK, cómo se exponen los tipos Kotlin en Swift
(tabla de mapeo Kotlin→Swift), cómo cablear DomainDependencies para iOS, y cómo
inyectar los casos de uso en el ViewModel Swift. Incluye ejemplos, testing y FAQ.

---

## Referencia de Manejo de Errores

### Flujo completo de mapeo de errores

```mermaid
flowchart TD
    A["El caso de uso recibe input"] --> B{"¿Validar input?"}
    B -->|"Inválido"| C["DomainError.Validation<br/>field + detail"]
    B -->|"Válido"| D{"¿Aplicar política de negocio?"}
    D -->|"Violada"| E["DomainError.Conflict<br/>detail"]
    D -->|"Satisfecha"| F{"¿Llamar repositorio?"}
    F -->|"Entidad no encontrada"| G["DomainError.NotFound<br/>resourceType + id"]
    F -->|"Error de DB/red"| H["DomainError.Infrastructure<br/>detail + cause"]
    F -->|"Fallo de auth"| I["DomainError.Unauthorized<br/>detail"]
    F -->|"Éxito"| J["DomainResult.Success&lt;T&gt;"]
    F -->|"Inesperado"| K["DomainError.Unknown<br/>detail + cause"]

    C --> L["DomainResult.Failure"]
    E --> L
    G --> L
    H --> L
    I --> L
    K --> L

    style J fill:#4CAF50,color:#fff
    style L fill:#f44336,color:#fff
```

### Cuándo usar cada tipo de error

| Error | Cuándo usarlo | Ejemplo |
|---|---|---|
| `Validation` | El input falla un invariante del dominio | Formato de email inválido, título muy largo |
| `NotFound` | La entidad solicitada no existe | Usuario con ID "xyz" no está en la base de datos |
| `Unauthorized` | El llamador no tiene permiso | Un no-admin intenta eliminar un usuario |
| `Conflict` | La operación conflictúa con el estado actual | Email duplicado, transición de estado inválida |
| `Infrastructure` | Una dependencia externa falló | Timeout de base de datos, error de red |
| `Unknown` | Condición inesperada (debería ser raro) | Fallback para errores no clasificados |

---

## Estrategia de Testing

### Árbol de decisión de test doubles

```mermaid
flowchart TD
    A["¿Necesitas un test double?"] --> B{"¿Es un fun interface?"}
    B -->|"Sí"| C["Usa lambda SAM<br/><code>ClockProvider { 42L }</code>"]
    B -->|"No"| D{"¿Es un Repository / Gateway?"}
    D -->|"Sí"| E["Usa object expression<br/><code>object : Repo { ... }</code>"]
    D -->|"No"| F["Usa data class / valor"]

    C --> G{"¿Necesitas verificar llamadas?"}
    G -->|"Sí"| H["Usa capturing double<br/><code>val saved = mutableListOf()</code>"]
    G -->|"No"| I["Usa double fijo"]

    style C fill:#4CAF50,color:#fff
    style E fill:#2196F3,color:#fff
    style H fill:#FF9800,color:#fff
```

### Helpers de test disponibles

Importar desde `com.domain.core.testing.TestDoubles` (solo en `commonTest`):

| Helper | Descripción |
|---|---|
| `testDeps` | `DomainDependencies` con clock fijo + ID fijo |
| `fixedClock` / `clockAt(ms)` | `ClockProvider` determinista |
| `fixedId` / `idOf(s)` | `IdProvider` determinista |
| `sequentialIds(prefix)` | `IdProvider` que retorna "prefix-1", "prefix-2", … |
| `advancingClock(start, step)` | `ClockProvider` que avanza por step en cada llamada |
| `validationError(field, detail)` | Builder rápido de `DomainError.Validation` |
| `notFoundError(type, id)` | Builder rápido de `DomainError.NotFound` |
| `shouldBeSuccess()` | Extrae el valor o lanza un `AssertionError` descriptivo |
| `shouldBeFailure()` | Extrae el error o lanza un `AssertionError` descriptivo |
| `shouldFailWith<E>()` | Extrae y castea al subtipo de error esperado |

Consulta [TESTING.md](TESTING.md) para la guía completa de testing, convenciones
de nombres y anti-patrones.

---

## Versionado

Este SDK sigue [Versionado Semántico](https://semver.org/lang/es/):

| Tipo de cambio | Bump de versión | Impacto al consumidor |
|---|---|---|
| Corrección de bug, actualización de docs | **PATCH** | Seguro de actualizar |
| Nuevos contratos añadidos | **MINOR** | Seguro de actualizar |
| Cambios breaking en la API `public` | **MAJOR** | Se esperan errores de compilación |

Consulta [ARCHITECTURE.md](ARCHITECTURE.md) para principios de diseño e
[INTEGRATION.md](INTEGRATION.md) para reglas de frontera con la capa de datos.

---

## Licencia

Repositorio privado. Todos los derechos reservados.

---

<details>
<summary><h2 id="english-version">🇺🇸 English Version</h2></summary>

A **pure domain layer SDK** for Kotlin Multiplatform. Zero framework dependencies.
Zero infrastructure. Zero UI. Just typed contracts, functional error handling,
and Clean Architecture enforced at the compiler level.

```
Targets: JVM · Android · iOS (arm64, x64, simulator)
Language: Kotlin 2.1.20 · KMP
Dependencies: kotlinx-coroutines-core 1.10.1 (only)
```

---

## Why this SDK?

| Advantage | Detail |
|---|---|
| **Pure domain** | Your business logic does not depend on Room, Retrofit, Ktor, CoreData, SwiftUI or Compose. If you swap databases tomorrow, the domain doesn't know or care. |
| **No exceptions** | All contracts return `DomainResult<T>`. Errors are explicit, typed values — never silently propagating exceptions. |
| **Testable without mocking** | All contracts are `fun interface`. In tests, create stubs with a one-line lambda. Zero Mockito, zero MockK. |
| **Deterministic** | Time (`ClockProvider`) and ID generation (`IdProvider`) are injected dependencies. In tests, pass fixed values → 100% reproducible results. |
| **Composable** | Validators chain with `andThen`. Policies compose with `and` / `or` / `negate`. Results combine with `zip`. |
| **Multiplatform** | A single domain module for Android + iOS + Desktop. Same code, same business rules. |
| **Enforced Clean Architecture** | The compiler prevents the domain from importing data or UI. Dependency direction is guaranteed at compile time. |

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

## DomainDependencies — The Domain Infrastructure Container

`DomainDependencies` is an immutable `data class` that groups the **only two infrastructure dependencies** the domain needs universally:

```kotlin
public data class DomainDependencies(
    val clock: ClockProvider,    // What time is it?
    val idProvider: IdProvider,  // Give me a unique ID
)
```

### The problem it solves

Every time a use case creates an entity it needs an **ID** and a **timestamp**. Without `DomainDependencies`:

```kotlin
// ❌ Impure — direct side-effects inside the domain
val task = Task(
    id = TaskId(UUID.randomUUID().toString()),   // coupled to JVM, won't compile on iOS
    createdAt = System.currentTimeMillis(),        // different result every execution
)
```

**Problems:** Not testable (every run generates different values → impossible to `assertEquals`), platform-coupled (won't compile on iOS), and not reproducible.

With `DomainDependencies`:

```kotlin
// ✅ Deterministic — the domain doesn't know where the values come from
val task = Task(
    id = TaskId(deps.idProvider.next()),
    createdAt = deps.clock.nowMillis(),
)
```

### How to configure it

**Production** — the app creates a single instance and passes it to all use cases:

```kotlin
// Android (Application.onCreate) or iOS (AppDelegate)
val domainDeps = DomainDependencies(
    clock = ClockProvider { System.currentTimeMillis() },
    idProvider = IdProvider { UUID.randomUUID().toString() },
)
```

**Tests** — fixed values, 100% reproducible results:

```kotlin
val testDeps = DomainDependencies(
    clock = ClockProvider { 1_700_000_000_000L },
    idProvider = IdProvider { "fixed-id-123" },
)

// Now you can verify with precision:
val result = createTask(CreateTaskParams("Buy milk"))
val task = result.getOrNull()!!
assertEquals("fixed-id-123", task.id.value)          // ✅ always passes
assertEquals(1_700_000_000_000L, task.createdAt)     // ✅ always passes
```

### Why group them in a single object?

If you have 20 use cases that create entities, without `DomainDependencies` you'd pass `clock` + `idProvider` as **40 individual parameters**. With the container, it's **20 parameters** (`deps`).

### What does NOT go here?

Feature repositories and gateways are injected **directly** into each use case. Only **cross-cutting** dependencies (needed by virtually all use cases) belong here:

```kotlin
class PlaceOrderUseCase(
    private val deps: DomainDependencies,          // ✅ cross-cutting: clock + id
    private val orderRepository: OrderRepository,  // ✅ feature-specific
    private val inventoryGateway: SuspendGateway<...>,
) : SuspendUseCase<PlaceOrderParams, Order> { ... }
```

---

## Complete Use Case Reference

The SDK provides **5 use case contracts**. All are `fun interface` (SAM), allowing
implementation as lambdas or classes:

| Contract | Signature | When to use |
|---|---|---|
| `PureUseCase<I, O>` | `(I) → DomainResult<O>` | Synchronous, pure logic. No I/O. Safe from main thread |
| `SuspendUseCase<I, O>` | `suspend (I) → DomainResult<O>` | Async single-result operations (create, update, fetch) |
| `FlowUseCase<I, O>` | `(I) → Flow<DomainResult<O>>` | Observe changes over time (reactive streams) |
| `NoParamsUseCase<O>` | `suspend () → DomainResult<O>` | No-params variant of `SuspendUseCase` |
| `NoParamsFlowUseCase<O>` | `() → Flow<DomainResult<O>>` | No-params variant of `FlowUseCase` |

> All return `DomainResult<T>`. They never throw exceptions to the consumer.

### 1. PureUseCase — Synchronous, pure logic

For calculations, transformations, business rules that **don't touch network or disk**.
Safe from any thread, including the main thread.

**Real-world scenario:** A bank needs to evaluate whether a client qualifies for a
personal loan. Rules depend on monthly income, debt-to-income ratio, and credit score.
No I/O — pure business rules.

```kotlin
data class LoanEligibilityParams(
    val monthlyIncome: Double,
    val currentDebtPercent: Double,
    val creditScore: Int,
    val requestedAmount: Double,
)

data class LoanEligibilityResult(
    val approved: Boolean,
    val maxApprovedAmount: Double,
    val interestRate: Double,
    val reason: String,
)

class EvaluateLoanEligibility : PureUseCase<LoanEligibilityParams, LoanEligibilityResult> {

    override fun invoke(params: LoanEligibilityParams): DomainResult<LoanEligibilityResult> {
        if (params.monthlyIncome <= 0)
            return domainFailure(DomainError.Validation("monthlyIncome", "must be positive"))
        if (params.creditScore !in 300..850)
            return domainFailure(DomainError.Validation("creditScore", "must be between 300 and 850"))

        // Rule 1: Deny if debt exceeds 40%
        if (params.currentDebtPercent > 40.0) {
            return LoanEligibilityResult(
                approved = false, maxApprovedAmount = 0.0, interestRate = 0.0,
                reason = "Current debt exceeds 40%",
            ).asSuccess()
        }

        // Rule 2: Interest rate based on credit score
        val interestRate = when {
            params.creditScore >= 750 -> 8.5
            params.creditScore >= 650 -> 12.0
            params.creditScore >= 550 -> 18.5
            else -> return LoanEligibilityResult(
                approved = false, maxApprovedAmount = 0.0, interestRate = 0.0,
                reason = "Insufficient credit score (minimum 550)",
            ).asSuccess()
        }

        // Rule 3: Max amount = 5x monthly income
        val maxAmount = params.monthlyIncome * 5
        val approved = params.requestedAmount <= maxAmount

        return LoanEligibilityResult(
            approved = approved, maxApprovedAmount = maxAmount, interestRate = interestRate,
            reason = if (approved) "Approved" else "Requested amount exceeds maximum allowed",
        ).asSuccess()
    }
}

// Usage — synchronous, safe from main thread:
val result = evaluateLoanEligibility(
    LoanEligibilityParams(monthlyIncome = 45_000.0, currentDebtPercent = 25.0, creditScore = 720, requestedAmount = 150_000.0)
)
// → Success(LoanEligibilityResult(approved=true, maxApprovedAmount=225000.0, interestRate=12.0, ...))
```

### 2. SuspendUseCase — Async single-result

For operations requiring I/O (persist, query, call API) that produce **one value**.

**Real-world scenario:** An e-commerce app needs to place an order. The use case validates
the cart, checks inventory, calculates taxes, and persists the order — all in one logical
transaction.

```kotlin
data class PlaceOrderParams(
    val cartId: CartId,
    val shippingAddressId: AddressId,
    val paymentMethodId: PaymentMethodId,
)

class PlaceOrderUseCase(
    private val deps: DomainDependencies,
    private val cartRepository: CartRepository,
    private val inventoryGateway: SuspendGateway<List<CartItem>, InventoryCheckResult>,
    private val orderRepository: OrderRepository,
    private val taxCalculator: PureUseCase<TaxParams, TaxResult>,
) : SuspendUseCase<PlaceOrderParams, Order> {

    override suspend fun invoke(params: PlaceOrderParams): DomainResult<Order> {
        // 1. Fetch cart
        val cart = cartRepository.findById(params.cartId).getOrElse { return domainFailure(it) }
            ?: return domainFailure(DomainError.NotFound("Cart", params.cartId.value))

        if (cart.items.isEmpty())
            return domainFailure(DomainError.Validation("cart", "Cart is empty"))

        // 2. Check inventory
        val inventoryCheck = inventoryGateway.execute(cart.items).getOrElse { return domainFailure(it) }
        if (!inventoryCheck.allAvailable)
            return domainFailure(DomainError.Conflict(
                detail = "Out of stock: ${inventoryCheck.unavailableItems.joinToString { it.name }}"
            ))

        // 3. Calculate tax (pure logic — PureUseCase)
        val tax = taxCalculator(TaxParams(cart.subtotal, cart.shippingState))
            .getOrElse { return domainFailure(it) }

        // 4. Build order
        val order = Order(
            id = OrderId(deps.idProvider.next()),
            items = cart.items,
            subtotal = cart.subtotal,
            tax = tax.amount,
            total = cart.subtotal + tax.amount,
            status = OrderStatus.CONFIRMED,
            createdAt = deps.clock.nowMillis(),
        )

        // 5. Persist
        return orderRepository.save(order).map { order }
    }
}

// Usage:
viewModelScope.launch {
    placeOrder(PlaceOrderParams(cartId, addressId, paymentId))
        .onSuccess { order -> navigateToConfirmation(order.id) }
        .onFailure { error ->
            when (error) {
                is DomainError.Conflict -> showOutOfStockDialog(error.detail)
                is DomainError.Validation -> showCartError(error.detail)
                else -> showGenericError(error.message)
            }
        }
}
```

### 3. FlowUseCase — Observe data over time

For reactive streams: self-updating data, real-time notifications, state changes.
Each emission can be success or failure individually without cancelling the stream.

**Real-world scenario:** A logistics dashboard where an operator observes shipments
filtered by status (pending, in transit, delivered). Whenever a shipment changes
status, the list updates automatically.

```kotlin
data class ShipmentFilterParams(
    val status: ShipmentStatus,
    val warehouseId: WarehouseId,
)

class ObserveShipmentsByStatus(
    private val shipmentRepository: ShipmentRepository,
) : FlowUseCase<ShipmentFilterParams, List<Shipment>> {

    override fun invoke(params: ShipmentFilterParams): Flow<DomainResult<List<Shipment>>> {
        return shipmentRepository
            .observeByWarehouseAndStatus(params.warehouseId, params.status)
    }
}

// Usage — in the ViewModel:
init {
    viewModelScope.launch {
        observeShipmentsByStatus(
            ShipmentFilterParams(status = ShipmentStatus.IN_TRANSIT, warehouseId = currentWarehouseId)
        ).collect { result ->
            result
                .onSuccess { shipments ->
                    _uiState.value = DashboardState.Loaded(shipments = shipments, count = shipments.size)
                }
                .onFailure { error -> _uiState.value = DashboardState.Error(error.message) }
        }
    }
}
```

### 4. NoParamsUseCase — Async without parameters

Same as `SuspendUseCase` but when **no parameters are needed**. Avoids passing `Unit`.

**Real-world scenario:** A healthcare app needs to log the user out. This involves
invalidating the token on the server, clearing the local cache, and recording an
audit event. No parameters needed — the active session determines everything.

```kotlin
class LogoutUseCase(
    private val sessionGateway: CommandGateway<LogoutCommand>,
    private val localCacheGateway: CommandGateway<ClearCacheCommand>,
    private val auditRepository: WriteRepository<AuditEvent>,
    private val deps: DomainDependencies,
) : NoParamsUseCase<Unit> {

    override suspend fun invoke(): DomainResult<Unit> {
        // 1. Invalidate token on the server
        sessionGateway.dispatch(LogoutCommand).getOrElse { return domainFailure(it) }

        // 2. Clear sensitive local data
        localCacheGateway.dispatch(ClearCacheCommand).getOrElse { return domainFailure(it) }

        // 3. Record audit event
        val event = AuditEvent(
            id = AuditEventId(deps.idProvider.next()),
            type = "LOGOUT",
            timestamp = deps.clock.nowMillis(),
        )
        return auditRepository.save(event)
    }
}

// Usage — no parameters:
viewModelScope.launch {
    logout()
        .onSuccess { navigateToLogin() }
        .onFailure { error -> showError("Could not log out: ${error.message}") }
}
```

### 5. NoParamsFlowUseCase — Reactive stream without parameters

Ideal for observing global data that requires no filter.

**Real-world scenario:** A fintech app needs to display the user's investment portfolio
total value updated in real time. The value changes as asset prices fluctuate. No
parameters needed — the authenticated user determines which portfolio to observe.

```kotlin
class ObservePortfolioValue(
    private val portfolioRepository: PortfolioRepository,
) : NoParamsFlowUseCase<PortfolioSummary> {

    override fun invoke(): Flow<DomainResult<PortfolioSummary>> {
        return portfolioRepository.observeCurrentUserPortfolio()
        // Emits every time a price changes or a transaction is executed
    }
}

data class PortfolioSummary(
    val totalValue: Double,
    val dailyChange: Double,
    val dailyChangePercent: Double,
    val holdings: List<Holding>,
)

// Usage — in the home screen ViewModel:
init {
    viewModelScope.launch {
        observePortfolioValue()
            .collect { result ->
                result
                    .onSuccess { summary ->
                        _uiState.value = HomeState.Loaded(
                            totalValue = summary.totalValue,
                            changePercent = summary.dailyChangePercent,
                            isPositive = summary.dailyChange >= 0,
                        )
                    }
                    .onFailure { error -> _uiState.value = HomeState.Error(error.message) }
            }
    }
}
```

### Decision diagram

```mermaid
flowchart TD
    A["What type of use case do I need?"] --> B{"Does it need I/O?"}
    B -->|"No — pure calculation"| C["PureUseCase"]
    B -->|"Yes"| D{"Returns a stream?"}
    D -->|"No — single value"| E{"Needs parameters?"}
    D -->|"Yes — multiple values"| F{"Needs parameters?"}
    E -->|"Yes"| G["SuspendUseCase"]
    E -->|"No"| H["NoParamsUseCase"]
    F -->|"Yes"| I["FlowUseCase"]
    F -->|"No"| J["NoParamsFlowUseCase"]

    style C fill:#4CAF50,color:#fff
    style G fill:#2196F3,color:#fff
    style H fill:#2196F3,color:#fff
    style I fill:#9C27B0,color:#fff
    style J fill:#9C27B0,color:#fff
```

---

## Integration with Core Data Platform

The **Core Domain Platform** (this SDK) defines domain contracts. The **[Core Data Platform](https://github.com/DanCrRdz93/core-data-platform)** implements remote data access (HTTP, security, sessions). Both are KMP and **100% version-aligned**:

| | Domain SDK | Data SDK |
|---|---|---|
| Kotlin | 2.1.20 | 2.1.20 |
| Coroutines | 1.10.1 | 1.10.1 |
| Gradle | 9.3.1 | 9.3.1 |

### Full Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          YOUR APP (Android/iOS)                         │
│                                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                              │
│  │Feature A │  │Feature B │  │Feature C │  ← ViewModels                 │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘                              │
│       │              │              │                                    │
│  ┌────▼──────────────▼──────────────▼────────────────┐                  │
│  │     Domain Use Cases (this SDK)                   │                  │
│  │  PureUseCase · SuspendUseCase · FlowUseCase       │                  │
│  │  (generates operationId, decides retryOverride)    │                  │
│  └────┬──────────────┬──────────────┬────────────────┘                  │
│       │              │              │                                    │
│  ┌────▼──────────────▼──────────────▼────────────────┐                  │
│  │   Repository / Gateway Implementations            │  ← YOU WRITE     │
│  │   (bridge between both SDKs)                      │    THESE         │
│  │   Propagates: RequestContext, ResultMetadata       │                  │
│  └────┬──────────────┬──────────┬────────────────────┘                  │
│       │              │          │                                        │
│  ┌────▼────┐  ┌──────▼────┐  ┌─▼───────────────────────────────────┐   │
│  │network  │  │ network   │  │  security-core                      │   │
│  │ -core   │  │  -ktor    │  │  SessionController · CredentialProv │   │
│  │         │  │           │  │  SecretStore · TrustPolicy          │   │
│  └─────────┘  └───────────┘  └─────────────────────────────────────┘   │
│                 ↑ Data SDK                                              │
│  Observability: LoggingObserver · MetricsObserver · TracingObserver     │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1. Error mapping: `NetworkError` → `DomainError`

> **Note:** `NetworkError` is **not** a `Throwable`. Each subtype has `diagnostic: Diagnostic?`
> where `Diagnostic.cause: Throwable?` holds the original exception. The mapping must pass
> `diagnostic?.cause` to `DomainError.Infrastructure.cause`, not the `NetworkError` itself.

```kotlin
// Extension living in your data layer (NOT in either SDK)
fun NetworkError.toDomainError(): DomainError = when (this) {
    // ── Transport ──
    is NetworkError.Connectivity -> DomainError.Infrastructure(
        detail = "No internet connection",
        cause = diagnostic?.cause,     // ← Throwable? from Data SDK
    )
    is NetworkError.Timeout -> DomainError.Infrastructure(
        detail = "Request timed out",
        cause = diagnostic?.cause,
    )
    is NetworkError.Cancelled -> DomainError.Cancelled(
        detail = "Request cancelled",  // ← Intentional, not infra failure
    )

    // ── HTTP semantic ──
    is NetworkError.Authentication -> DomainError.Unauthorized("Authentication required")
    is NetworkError.Authorization  -> DomainError.Unauthorized("Access denied")
    is NetworkError.ClientError -> when (statusCode) {
        404  -> DomainError.NotFound("Resource", diagnostic?.description ?: "")
        409  -> DomainError.Conflict(message)
        422  -> DomainError.Validation("request", diagnostic?.description ?: message)
        else -> DomainError.Infrastructure("HTTP error $statusCode", diagnostic?.cause)
    }
    is NetworkError.ServerError -> DomainError.Infrastructure(
        detail = "Server error ($statusCode)",
        cause = diagnostic?.cause,
    )

    // ── Data processing ──
    is NetworkError.Serialization -> DomainError.Infrastructure(
        detail = "Failed to process response",
        cause = diagnostic?.cause,
    )
    is NetworkError.ResponseValidation -> DomainError.Infrastructure(
        detail = reason,
        cause = diagnostic?.cause,
    )

    // ── Catch-all ──
    is NetworkError.Unknown -> DomainError.Unknown(
        detail = message,
        cause = diagnostic?.cause,
    )
}
```

**Why `Cancelled` is not `Infrastructure`:** A cancellation is intentional (user navigated away,
coroutine scope was cancelled). Mapping it as `Infrastructure` would make it indistinguishable
from a server crash in the ViewModel's `when`. With `DomainError.Cancelled`, consumers can
suppress it without showing an error dialog.

### 2. Repository implementation (the bridge)

> **Scenario:** A use case needs to fetch a user by ID. The Data SDK's DataSource returns
> `NetworkResult<UserDto>`. The repository converts DTO → model and error → DomainError.

```kotlin
class UserRepositoryImpl(
    private val dataSource: UserRemoteDataSource,      // ← Data SDK
    private val mapper: Mapper<UserDto, User>,          // ← Domain SDK contract
) : ReadRepository<UserId, User> {                      // ← Domain SDK contract

    override suspend fun findById(id: UserId): DomainResult<User?> {
        val networkResult = dataSource.getUser(id.value)
        return networkResult.fold(
            onSuccess = { dto -> mapper.map(dto).asSuccess() },
            onFailure = { error -> domainFailure(error.toDomainError()) },
        )
    }
}
```

### 3. `ResponseMetadata` propagation to the domain

> **Scenario:** A support team needs the ViewModel to display the `requestId` when an
> operation fails, so the user can report it. You also want to measure perceived latency
> (`durationMs`) and how many retries were attempted (`attemptCount`).

```kotlin
class UserRepositoryWithMeta(
    private val dataSource: UserRemoteDataSource,
    private val mapper: Mapper<UserDto, User>,
) : ReadRepository<UserId, User> {

    // Returns DomainResultWithMeta so the ViewModel can access metadata
    suspend fun findByIdWithMeta(id: UserId): DomainResultWithMeta<User?> {
        val networkResult = dataSource.getUser(id.value)
        return when (networkResult) {
            is NetworkResult.Success -> DomainResultWithMeta(
                result = mapper.map(networkResult.data).asSuccess(),
                metadata = networkResult.metadata.toDomainMeta(),
            )
            is NetworkResult.Failure -> DomainResultWithMeta(
                result = domainFailure(networkResult.error.toDomainError()),
                // On failure you can also propagate metadata if available
            )
        }
    }

    // Standard contract (no metadata) remains available
    override suspend fun findById(id: UserId): DomainResult<User?> =
        findByIdWithMeta(id).result
}

// Extension in your data layer
fun ResponseMetadata.toDomainMeta(): ResultMetadata = ResultMetadata(
    requestId = requestId,
    durationMs = durationMs,
    attemptCount = attemptCount,
    extra = buildMap {
        headers["X-RateLimit-Remaining"]?.firstOrNull()?.let { put("rateLimitRemaining", it) }
        headers["ETag"]?.firstOrNull()?.let { put("etag", it) }
    },
)
```

**Usage in the ViewModel:**

```kotlin
val (result, meta) = userRepo.findByIdWithMeta(userId)
result.fold(
    onSuccess = { user -> showUser(user) },
    onFailure = { error ->
        showError(error.message)
        // User can report the requestId to support
        analytics.logError(requestId = meta.requestId, duration = meta.durationMs)
    },
)
```

### 4. `RequestContext` — domain → HTTP correlation

> **Scenario:** The SRE team wants to see in Datadog which use case generated each HTTP request.
> `PlaceOrderUseCase` generates an `operationId = "place-order"` that travels all the way to the
> HTTP headers, correlating the domain operation with the infrastructure trace.

```kotlin
// ── Contract in your domain layer (repository interface) ──
interface OrderRepository : WriteRepository<Order> {
    suspend fun placeOrder(order: Order, operationId: String): DomainResult<Unit>
}

// ── Implementation in your data layer (bridge) ──
class OrderRepositoryImpl(
    private val dataSource: OrderRemoteDataSource,
) : OrderRepository {

    override suspend fun placeOrder(order: Order, operationId: String): DomainResult<Unit> {
        val context = RequestContext(
            operationId = operationId,              // ← From the use case
            tags = mapOf("orderId" to order.id),    // ← Business context
            requiresAuth = true,
        )
        val result = dataSource.createOrder(order.toDto(), context)
        return result.fold(
            onSuccess = { Unit.asSuccess() },
            onFailure = { error -> domainFailure(error.toDomainError()) },
        )
    }

    // Standard save/delete for WriteRepository
    override suspend fun save(entity: Order) = placeOrder(entity, "save-order")
    override suspend fun delete(entity: Order): DomainResult<Unit> { /* ... */ }
}

// ── Use case generates the operationId ──
class PlaceOrderUseCase(
    private val orderRepo: OrderRepository,
    private val deps: DomainDependencies,
) : SuspendUseCase<PlaceOrderInput, Unit> {

    override suspend fun invoke(input: PlaceOrderInput): DomainResult<Unit> {
        val order = Order(id = deps.idProvider.generate(), /* ... */)
        return orderRepo.placeOrder(order, operationId = "place-order")
    }
}
```

In Datadog you'll see: `operation_id=place-order, orderId=abc-123` linked to the HTTP request.

### 5. `RetryPolicy` override from the domain

> **Scenario:** A payment must NOT be retried — retrying could charge the user twice.
> The use case tells the repository this operation is `RetryPolicy.None`.

```kotlin
// ── Repository contract (domain) ──
interface PaymentRepository : Repository {
    suspend fun processPayment(payment: Payment, allowRetry: Boolean = false): DomainResult<PaymentResult>
}

// ── Implementation (bridge) ──
class PaymentRepositoryImpl(
    private val dataSource: PaymentRemoteDataSource,
) : PaymentRepository {

    override suspend fun processPayment(
        payment: Payment,
        allowRetry: Boolean,
    ): DomainResult<PaymentResult> {
        val context = RequestContext(
            operationId = "process-payment",
            // If domain says no retry, override the policy
            retryPolicyOverride = if (!allowRetry) RetryPolicy.None else null,
            requiresAuth = true,
        )
        val result = dataSource.charge(payment.toDto(), context)
        return result.fold(
            onSuccess = { dto -> PaymentResult(dto.transactionId).asSuccess() },
            onFailure = { error -> domainFailure(error.toDomainError()) },
        )
    }
}

// ── Use case ──
class ProcessPaymentUseCase(
    private val paymentRepo: PaymentRepository,
) : SuspendUseCase<Payment, PaymentResult> {

    override suspend fun invoke(input: Payment): DomainResult<PaymentResult> =
        paymentRepo.processPayment(input, allowRetry = false) // ← NEVER retry payments
}
```

### 6. Full session lifecycle

> **Scenario:** The app needs: login, logout, force-logout on 401, token refresh,
> and session event observation (for analytics and UI updates).

```kotlin
// ── Adapter: login ──
class LoginGateway(
    private val session: SessionController,
) : CommandGateway<SessionCredentials> {

    override suspend fun dispatch(input: SessionCredentials): DomainResult<Unit> =
        runDomainCatching { session.startSession(input) }
}

// ── Adapter: voluntary logout ──
class LogoutGateway(
    private val session: SessionController,
) : CommandGateway<Unit> {

    override suspend fun dispatch(input: Unit): DomainResult<Unit> =
        runDomainCatching { session.endSession() }
}

// ── Adapter: force-logout (401 or compromised security) ──
class ForceLogoutGateway(
    private val session: SessionController,
) : CommandGateway<Unit> {

    override suspend fun dispatch(input: Unit): DomainResult<Unit> =
        runDomainCatching { session.invalidate() }
}

// ── Adapter: token refresh ──
// RefreshOutcome is a sealed from the Data SDK: Refreshed, NotNeeded, Failed
class RefreshSessionGateway(
    private val session: SessionController,
) : SuspendGateway<Unit, RefreshOutcome> {

    override suspend fun execute(input: Unit): DomainResult<RefreshOutcome> =
        runDomainCatching { session.refreshSession() }
}

// ── Adapter: session state (StateFlow → FlowGateway) ──
class SessionStateGateway(
    private val session: SessionController,
) : NoParamsFlowGateway<Boolean> {

    override fun observe(): Flow<DomainResult<Boolean>> =
        session.state.map { state ->
            (state is SessionState.Active).asSuccess()
        }
}

// ── Adapter: session events for analytics ──
class SessionEventsGateway(
    private val session: SessionController,
) : NoParamsFlowGateway<SessionEvent> {

    override fun observe(): Flow<DomainResult<SessionEvent>> =
        session.events.map { event -> event.asSuccess() }
}
```

**Usage in a use case:**

```kotlin
class LogoutUseCase(
    private val logout: LogoutGateway,
    private val clearCache: ClearCacheGateway, // another gateway of yours
) : SuspendUseCase<Unit, Unit> {

    override suspend fun invoke(input: Unit): DomainResult<Unit> =
        logout.dispatch(Unit).flatMap { clearCache.dispatch(Unit) }
}
```

### 7. Multiple APIs with different configurations

> **Scenario:** `PlaceOrderUseCase` orchestrates 3 APIs (orders, inventory, payments) each
> with its own `NetworkConfig`, executor, and base URL.

```kotlin
fun provideMultiApiDependencies(): AppDependencies {
    // ── Per-API configurations ──
    val ordersConfig = NetworkConfig(
        baseUrl = "https://orders.api.example.com",
        connectTimeout = 10_000L,
        retryPolicy = RetryPolicy.ExponentialBackoff(maxRetries = 3),
    )
    val inventoryConfig = NetworkConfig(
        baseUrl = "https://inventory.api.example.com",
        connectTimeout = 5_000L,
        retryPolicy = RetryPolicy.FixedDelay(maxRetries = 2, delay = 1_000L),
    )
    val paymentsConfig = NetworkConfig(
        baseUrl = "https://payments.api.example.com",
        connectTimeout = 30_000L,
        retryPolicy = RetryPolicy.None, // ← Payments: NEVER retry by default
    )

    // ── Independent executors per API ──
    val ordersExecutor = DefaultSafeRequestExecutor(
        engine = KtorHttpEngine.create(ordersConfig),
        config = ordersConfig,
        classifier = KtorErrorClassifier(),
        observers = listOf(loggingObserver, metricsObserver),
    )
    val inventoryExecutor = DefaultSafeRequestExecutor(
        engine = KtorHttpEngine.create(inventoryConfig),
        config = inventoryConfig,
        classifier = KtorErrorClassifier(),
    )
    val paymentsExecutor = DefaultSafeRequestExecutor(
        engine = KtorHttpEngine.create(paymentsConfig, bankTrustPolicy), // ← TrustPolicy
        config = paymentsConfig,
        classifier = KtorErrorClassifier(),
    )

    // ── DataSources ──
    val orderDataSource = OrderRemoteDataSource(ordersExecutor)
    val inventoryDataSource = InventoryRemoteDataSource(inventoryExecutor)
    val paymentDataSource = PaymentRemoteDataSource(paymentsExecutor)

    // ── Repositories (bridge) ──
    val orderRepo = OrderRepositoryImpl(orderDataSource)
    val inventoryGateway = InventoryGatewayImpl(inventoryDataSource)
    val paymentRepo = PaymentRepositoryImpl(paymentDataSource)

    // ── Use case orchestrates all 3 APIs ──
    val placeOrder = PlaceOrderUseCase(orderRepo, inventoryGateway, paymentRepo, domainDeps)

    return AppDependencies(placeOrder, /* ... */)
}
```

### 8. Exposing Rate Limits to the domain

> **Scenario:** The API returns `X-RateLimit-Remaining` in headers. The domain needs
> a gateway that exposes the current rate limit to decide on preventive throttling
> (e.g., disable the "Refresh" button when few requests remain).

```kotlin
// ── Reactive gateway (domain) ──
interface RateLimitGateway : NoParamsFlowGateway<Int> // Remaining count

// ── Implementation (bridge) — uses ResponseInterceptor from Data SDK ──
class RateLimitGatewayImpl : RateLimitGateway, ResponseInterceptor {
    private val _remaining = MutableStateFlow(Int.MAX_VALUE)

    // ResponseInterceptor from Data SDK — intercepts every HTTP response
    override suspend fun intercept(response: InterceptedResponse): InterceptedResponse {
        response.headers["X-RateLimit-Remaining"]?.firstOrNull()?.toIntOrNull()?.let {
            _remaining.value = it
        }
        return response
    }

    // NoParamsFlowGateway from Domain SDK — exposes to the domain
    override fun observe(): Flow<DomainResult<Int>> =
        _remaining.map { it.asSuccess() }
}

// Register as ResponseInterceptor when creating the executor:
val rateLimitGateway = RateLimitGatewayImpl()
val executor = DefaultSafeRequestExecutor(
    engine = engine,
    config = config,
    classifier = KtorErrorClassifier(),
    responseInterceptors = listOf(rateLimitGateway), // ← dual role
)
```

### 9. `TrustPolicy` and Certificate Pinning in the wiring

> **Scenario:** A banking app needs MITM attack protection. The Data SDK supports
> `TrustPolicy` with `CertificatePin` that validates server certificates against
> known pins.

```kotlin
// ── TrustPolicy for banking / healthcare ──
val bankTrustPolicy = DefaultTrustPolicy(
    pins = listOf(
        CertificatePin(
            hostname = "payments.api.example.com",
            sha256 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", // SHA-256 of cert
        ),
        CertificatePin(
            hostname = "payments.api.example.com",
            sha256 = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=", // Backup pin
        ),
    ),
)

// Pass when creating the engine
val secureEngine = KtorHttpEngine.create(
    config = paymentsConfig,
    trustPolicy = bankTrustPolicy,    // ← Pinning active
)
val secureExecutor = DefaultSafeRequestExecutor(
    engine = secureEngine,
    config = paymentsConfig,
    classifier = KtorErrorClassifier(),
)
```

### 10. Observers in the wiring

> **Scenario:** You need HTTP request logging in development, latency metrics in
> production, and a custom observer that reports errors to Crashlytics.

```kotlin
// ── LoggingObserver (Data SDK) — debug only ──
val loggingObserver = LoggingObserver(
    logger = { tag, msg -> println("[$tag] $msg") },
    tag = "HTTP",
    headerSanitizer = { key, value ->
        if (key.equals("Authorization", ignoreCase = true)) "***" else value
    },
)

// ── Custom observer: Crashlytics reporting ──
val crashlyticsObserver = object : NetworkEventObserver {
    override fun onRequestStarted(url: String, method: String) { /* no-op */ }
    override fun onResponseReceived(url: String, statusCode: Int, durationMs: Long) {
        if (statusCode >= 500) {
            crashlytics.log("Server error: $url → $statusCode (${durationMs}ms)")
        }
    }
    override fun onRetryScheduled(url: String, attempt: Int, delayMs: Long) {
        crashlytics.log("Retry #$attempt for $url in ${delayMs}ms")
    }
    override fun onRequestFailed(url: String, error: NetworkError) {
        crashlytics.recordError(error.diagnostic?.cause ?: Exception(error.message))
    }
}

// ── Assemble in the executor ──
val executor = DefaultSafeRequestExecutor(
    engine = KtorHttpEngine.create(config),
    config = config,
    classifier = KtorErrorClassifier(),
    observers = listOf(loggingObserver, crashlyticsObserver), // ← both active
)
```

### Full correspondence table

| Data SDK | Domain SDK | Where they connect |
|---|---|---|
| `NetworkResult<T>` | `DomainResult<T>` | Repository impl via `fold` |
| `NetworkResult.Success.metadata` | `ResultMetadata` / `DomainResultWithMeta` | `ResponseMetadata.toDomainMeta()` |
| `NetworkError.*` (10 subtypes) | `DomainError.*` (7 subtypes) | `NetworkError.toDomainError()` |
| `NetworkError.Cancelled` | `DomainError.Cancelled` | Correct semantic mapping |
| `NetworkError.diagnostic?.cause` | `DomainError.Infrastructure.cause` | `Throwable?` preserved |
| `RequestContext.operationId` | Use case generates the ID | Propagated via repository impl |
| `RequestContext.retryPolicyOverride` | Use case decides allowRetry | Repository propagates to `RequestContext` |
| `ResponseMetadata.headers` | `ResultMetadata.extra` | Rate limits, ETags, etc. |
| `SessionController.startSession` | `CommandGateway<SessionCredentials>` | `LoginGateway` adapter |
| `SessionController.endSession` | `CommandGateway<Unit>` | `LogoutGateway` adapter |
| `SessionController.invalidate` | `CommandGateway<Unit>` | `ForceLogoutGateway` adapter |
| `SessionController.refreshSession` | `SuspendGateway<Unit, RefreshOutcome>` | `RefreshSessionGateway` adapter |
| `SessionController.state` | `NoParamsFlowGateway<Boolean>` | `SessionStateGateway` adapter |
| `SessionController.events` | `NoParamsFlowGateway<SessionEvent>` | `SessionEventsGateway` adapter |
| `ResponseInterceptor` | `NoParamsFlowGateway<Int>` | `RateLimitGatewayImpl` (dual role) |
| `NetworkConfig` (per API) | Multiple executors | Multi-API wiring |
| `TrustPolicy` / `CertificatePin` | N/A (pure infra) | `KtorHttpEngine.create(config, trustPolicy)` |
| `NetworkEventObserver` | N/A (pure infra) | `DefaultSafeRequestExecutor(observers = ...)` |
| `LoggingObserver` | N/A (pure infra) | Configured in wiring |
| DTOs (`@Serializable`) | Domain models (pure) | `Mapper<Dto, Model>` |
| Batch HTTP requests | `WriteRepository.saveAll()` | Repository impl override |

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

> **Full guide:** [GUIDE_ANDROID.md](GUIDE_ANDROID.md)

Documents all SDK contracts (Use Cases, DomainResult, DomainError, Model, Repository,
Gateway, Validators, Policies, Providers) and how to inject use cases into your ViewModel.
Includes implementation examples, validation, testing, and FAQ.

---

## iOS Integration Guide

> **Full guide:** [GUIDE_IOS.md](GUIDE_IOS.md)

Documents all SDK contracts, how Kotlin types are exposed in Swift (Kotlin→Swift
mapping table), how to wire DomainDependencies for iOS, and how to inject use cases
into the Swift ViewModel. Includes examples, testing, and FAQ.

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

</details>
