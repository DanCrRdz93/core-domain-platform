package com.domain.core.result

/**
 * Optional metadata that can accompany a [DomainResult] when the domain layer
 * needs infrastructure context — e.g., a request ID for error reporting,
 * response timing for metrics, or cache headers for staleness checks.
 *
 * Design rationale:
 * - [DomainResult] itself stays clean: [Success] carries a value, [Failure]
 *   carries a [com.domain.core.error.DomainError]. No metadata pollutes
 *   the sealed class hierarchy.
 * - [ResultMetadata] is a standalone data class that repository implementations
 *   can attach when it matters and ignore when it doesn't.
 * - This maps directly from the data SDK's `ResponseMetadata` (statusCode,
 *   headers, durationMs, requestId, attemptCount) — the repository impl
 *   converts the transport-specific fields into these domain-safe fields.
 * - All fields are optional: a pure use case (no I/O) never produces metadata;
 *   a network-backed repository might populate all of them.
 *
 * Usage in a repository implementation:
 * ```kotlin
 * class UserRepositoryImpl(
 *     private val dataSource: UserRemoteDataSource,
 *     private val mapper: Mapper<UserDto, User>,
 * ) : UserRepository {
 *
 *     override suspend fun findById(id: UserId): DomainResultWithMeta<User?> {
 *         val networkResult = dataSource.getUser(id.value)
 *         return networkResult.fold(
 *             onSuccess = { dto ->
 *                 DomainResultWithMeta(
 *                     result = mapper.map(dto).asSuccess(),
 *                     metadata = ResultMetadata(
 *                         requestId = networkResult.metadata.requestId,
 *                         durationMs = networkResult.metadata.durationMs,
 *                     ),
 *                 )
 *             },
 *             onFailure = { error ->
 *                 DomainResultWithMeta(
 *                     result = error.toDomainError().asDomainFailure(),
 *                 )
 *             },
 *         )
 *     }
 * }
 * ```
 */
public data class ResultMetadata(
    val requestId: String? = null,
    val durationMs: Long? = null,
    val attemptCount: Int? = null,
    val extra: Map<String, String> = emptyMap(),
) {
    public companion object {
        public val EMPTY: ResultMetadata = ResultMetadata()
    }
}

/**
 * A [DomainResult] paired with optional [ResultMetadata].
 *
 * Design rationale:
 * - Keeps [DomainResult] itself untouched — use cases that don't care about
 *   metadata continue using [DomainResult] directly.
 * - Repository implementations that bridge from the data SDK can return
 *   [DomainResultWithMeta] when the caller (e.g., a ViewModel for error
 *   reporting) needs the extra context.
 * - Destructuring: `val (result, meta) = repo.findById(id)`
 */
public data class DomainResultWithMeta<out T>(
    val result: DomainResult<T>,
    val metadata: ResultMetadata = ResultMetadata.EMPTY,
)
