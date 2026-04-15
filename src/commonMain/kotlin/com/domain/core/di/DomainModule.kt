package com.domain.core.di

/**
 * Contract that every feature's domain module must implement.
 *
 * Design rationale:
 * ─ Manual composition without DI framework.
 *   The app layer creates one concrete [DomainModule] per feature, passing
 *   all required dependencies through the constructor of the implementing class.
 *   The feature exposes its use cases as properties on this interface.
 *   The app/presentation layer only sees this interface — it never instantiates
 *   use cases directly, which keeps wiring centralised and swappable.
 *
 * ─ Why an interface and not an abstract class?
 *   Features may need to compose multiple module slices. An interface allows
 *   delegation without forcing a class hierarchy. The implementing class remains
 *   a plain class with constructor-injected dependencies.
 *
 * ─ No base methods are declared here.
 *   Each feature defines its own sub-interface extending [DomainModule] and
 *   declares exactly the use cases it exposes. This keeps the contract narrow
 *   and prevents the "fat module" anti-pattern.
 *
 * ─ Lifecycle.
 *   [DomainModule] implementations have no lifecycle callbacks. They are
 *   stateless holders of injected stateless use cases. Creation and destruction
 *   is controlled entirely by the caller (typically the app layer's DI graph
 *   or a top-level `object` in tests).
 *
 * Canonical implementation pattern (in the feature module, NOT here):
 * ```kotlin
 * interface UserDomainModule : DomainModule {
 *     val registerUser: SuspendUseCase<RegisterUserParams, User>
 *     val getUser: SuspendUseCase<UserId, User>
 * }
 *
 * class UserDomainModuleImpl(
 *     deps: DomainDependencies,
 *     userRepository: UserRepository,
 * ) : UserDomainModule {
 *     override val registerUser = RegisterUserUseCase(deps, userRepository)
 *     override val getUser = GetUserUseCase(userRepository)
 * }
 * ```
 *
 * App-layer composition (NOT here):
 * ```kotlin
 * val userModule: UserDomainModule = UserDomainModuleImpl(
 *     deps = domainDeps,
 *     userRepository = UserRepositoryImpl(db),
 * )
 * ```
 */
public interface DomainModule
