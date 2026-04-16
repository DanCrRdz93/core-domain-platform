package com.domain.core.repository

import com.domain.core.result.DomainResult
import com.domain.core.result.asSuccess
import com.domain.core.result.map
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
 * Contract for repositories that support reading a single aggregate by its identity
 * and checking existence.
 *
 * Design rationale:
 * - [existsById] avoids fetching a full aggregate when only existence matters.
 *   Default implementation delegates to [findById] so implementors only need
 *   to override it when an optimised query is available (e.g., `SELECT 1`).
 */
public interface ReadRepository<in ID, out T> : Repository {
    public suspend fun findById(id: ID): DomainResult<T?>

    public suspend fun existsById(id: ID): DomainResult<Boolean> =
        findById(id).map { it != null }
}

/**
 * Contract for repositories that support reading a collection of aggregates.
 *
 * Design rationale:
 * - [findAll] returns a single snapshot — use when you need a list once.
 * - [observeAll] returns a [Flow] — use when you need to observe changes over time.
 * - Both are provided because many use cases only need a one-shot fetch (e.g.,
 *   "get all active users"), while others need live updates (e.g., a dashboard).
 *
 * [T] — the aggregate root type.
 */
public interface ReadCollectionRepository<out T> : Repository {
    public suspend fun findAll(): DomainResult<List<T>>
    public fun observeAll(): Flow<DomainResult<List<T>>>
}

/**
 * Contract for repositories that support write operations.
 *
 * Design rationale:
 * - [saveAll] and [deleteAll] have default implementations that loop over
 *   the individual operations. Override them when the data layer supports
 *   batch operations (e.g., bulk INSERT, batch HTTP request) for better
 *   performance.
 * - Default implementations are fail-fast: they stop at the first failure.
 *
 * [T] — the aggregate root type.
 */
public interface WriteRepository<in T> : Repository {
    public suspend fun save(entity: T): DomainResult<Unit>
    public suspend fun delete(entity: T): DomainResult<Unit>

    public suspend fun saveAll(entities: List<T>): DomainResult<Unit> {
        for (entity in entities) {
            val result = save(entity)
            if (result.isFailure) return result
        }
        return Unit.asSuccess()
    }

    public suspend fun deleteAll(entities: List<T>): DomainResult<Unit> {
        for (entity in entities) {
            val result = delete(entity)
            if (result.isFailure) return result
        }
        return Unit.asSuccess()
    }
}

/**
 * Convenience interface combining read-by-id, collection reads, and writes.
 *
 * Design rationale:
 * - Many aggregates need all three capabilities. Extending this single interface
 *   avoids `MyRepo : ReadRepository<ID, T>, ReadCollectionRepository<T>, WriteRepository<T>`
 *   boilerplate in every feature module.
 * - If an aggregate only needs a subset, use the granular interfaces directly.
 */
public interface ReadWriteRepository<in ID, T> :
    ReadRepository<ID, T>,
    ReadCollectionRepository<T>,
    WriteRepository<T>

