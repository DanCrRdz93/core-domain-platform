package com.domain.core.repository

import com.domain.core.result.DomainResult
import kotlinx.coroutines.flow.Flow

/**
 * Marker interface for all domain repository contracts.
 *
 * Design rationale:
 * - The domain defines the repository interface; data layer implements it.
 *   This is the Dependency Inversion Principle applied concretely.
 * - [Repository] is a pure marker: it signals intent without imposing methods,
 *   avoiding the "fat interface" anti-pattern.
 * - Concrete repository interfaces live in the domain packages that own their
 *   aggregate, extending this marker for discoverability and DI scanning.
 * - No generics here: each aggregate defines its own explicit contract.
 */
public interface Repository

/**
 * Contract for repositories that support reading a single aggregate by its identity.
 *
 * [ID]  — the type used as the aggregate's identity.
 * [T]   — the aggregate root type.
 *
 * Implementing classes must not cache state internally; caching belongs to
 * the data layer. This keeps the contract honest and deterministic for tests.
 */
public interface ReadRepository<in ID, out T> : Repository {
    public suspend fun findById(id: ID): DomainResult<T?>
}

/**
 * Contract for repositories that support reading a collection of aggregates.
 * [T] — the aggregate root type.
 */
public interface ReadCollectionRepository<out T> : Repository {
    public fun observeAll(): Flow<DomainResult<List<T>>>
}

/**
 * Contract for repositories that support write operations.
 * [T] — the aggregate root type.
 */
public interface WriteRepository<in T> : Repository {
    public suspend fun save(entity: T): DomainResult<Unit>
    public suspend fun delete(entity: T): DomainResult<Unit>
}

/**
 * Composite contract combining read-by-id and write operations.
 * Use this only when the aggregate genuinely requires both from a single
 * repository boundary. Do not use it to avoid declaring separate interfaces.
 */
public interface ReadWriteRepository<in ID, T> :
    ReadRepository<ID, T>,
    WriteRepository<T>
