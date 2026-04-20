package com.domain.core.repository

import com.domain.core.result.DomainResult

/**
 * Immutable page of domain results returned by paginated queries.
 *
 * Design rationale:
 * - Almost every API returns paginated data. Without a shared model, each
 *   feature module invents its own `PagedList`, `PaginatedResult`, etc.
 * - [Page] is a pure domain concept — it does not know about HTTP pagination
 *   headers, cursor tokens, or offset mechanics. The data layer maps from
 *   its transport-specific pagination to this type.
 * - [totalPages] and [totalItems] are nullable because some APIs don't
 *   provide them (e.g., cursor-based pagination).
 *
 * [T] — the domain model type.
 *
 * Example:
 * ```kotlin
 * val page = Page(
 *     items = listOf(user1, user2),
 *     page = 0,
 *     size = 20,
 *     totalPages = 5,
 *     totalItems = 100,
 * )
 * println(page.hasNext)     // true  (page 0 < 4)
 * println(page.hasPrevious) // false (page 0)
 * println(page.isEmpty)     // false
 * ```
 */
public data class Page<out T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalPages: Int? = null,
    val totalItems: Long? = null,
) {
    val hasNext: Boolean get() = totalPages?.let { page < it - 1 } ?: (items.size == size)
    val hasPrevious: Boolean get() = page > 0
    val isEmpty: Boolean get() = items.isEmpty()
}

/**
 * Parameters for a paginated query.
 *
 * Design rationale:
 * - Kept as a separate data class rather than raw `page: Int, size: Int`
 *   parameters so that it can be extended with sorting or filtering without
 *   breaking existing signatures.
 *
 * Example:
 * ```kotlin
 * // Default: page 0, size 20
 * val first = PageRequest()
 *
 * // Custom:
 * val second = PageRequest(page = 2, size = 50)
 *
 * // Invalid — throws at construction:
 * // PageRequest(page = -1) // IllegalArgumentException
 * // PageRequest(size = 0)  // IllegalArgumentException
 * ```
 */
public data class PageRequest(
    val page: Int = 0,
    val size: Int = 20,
) {
    init {
        require(page >= 0) { "page must be >= 0" }
        require(size in 1..100) { "size must be between 1 and 100" }
    }
}

/**
 * Contract for repositories that support paginated reads.
 *
 * Design rationale:
 * - Separate from [ReadCollectionRepository] because not all collections
 *   are paginated and not all paginated endpoints need `observeAll`.
 * - [findPage] is a suspend one-shot query, matching the typical API call pattern.
 *
 * [T] — the domain model type.
 *
 * Example:
 * ```kotlin
 * interface ProductRepository : PaginatedRepository<Product>
 *
 * // Call site:
 * val page = productRepo.findPage(PageRequest(page = 0, size = 20))
 * page.onSuccess { result ->
 *     showProducts(result.items)
 *     if (result.hasNext) enableLoadMore()
 * }
 * ```
 */
public interface PaginatedRepository<out T> : Repository {
    public suspend fun findPage(request: PageRequest): DomainResult<Page<T>>
}
