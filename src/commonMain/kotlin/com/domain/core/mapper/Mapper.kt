package com.domain.core.mapper

/**
 * Contract for transforming between two types — typically DTO ↔ domain model.
 *
 * Design rationale:
 * - Repository implementations receive DTOs from infrastructure (network, DB)
 *   and must convert them to domain models. Without a typed contract, each
 *   team invents its own ad-hoc mapping pattern.
 * - `fun interface` enables single-line lambda implementations for simple cases
 *   and class-based implementations for complex mappings.
 * - Pure function: no I/O, no side effects. If mapping requires I/O (e.g.,
 *   fetching a related entity), that logic belongs in the use case, not the mapper.
 * - Separate from [Validator]: a mapper transforms shape; a validator checks invariants.
 *
 * [I] — the input type (e.g., `UserDto`).
 * [O] — the output type (e.g., `User`).
 *
 * Usage:
 * ```kotlin
 * class UserDtoToUser : Mapper<UserDto, User> {
 *     override fun map(input: UserDto): User = User(
 *         id = UserId(input.id),
 *         name = input.displayName,
 *         email = input.email,
 *     )
 * }
 *
 * // Or as a lambda:
 * val toUser = Mapper<UserDto, User> { dto ->
 *     User(id = UserId(dto.id), name = dto.displayName, email = dto.email)
 * }
 * ```
 */
public fun interface Mapper<in I, out O> {
    public fun map(input: I): O
}

/**
 * Maps a [List] of inputs using this [Mapper].
 * Avoids writing `.map { mapper.map(it) }` at every call site.
 */
public fun <I, O> Mapper<I, O>.mapList(inputs: List<I>): List<O> =
    inputs.map { map(it) }

/**
 * Combines two mappers into a single pipeline: `this` runs first, then [other].
 *
 * Useful when mapping passes through an intermediate representation:
 * `dtoToIntermediate.andThen(intermediateToModel)`.
 */
public fun <A, B, C> Mapper<A, B>.andThen(other: Mapper<B, C>): Mapper<A, C> =
    Mapper { input -> other.map(map(input)) }

/**
 * Contract for bidirectional mapping — e.g., domain model ↔ DTO.
 *
 * Design rationale:
 * - Some repository implementations need to map in both directions:
 *   read (DTO → Domain) and write (Domain → DTO).
 * - Extending [Mapper] for the forward direction keeps it compatible with
 *   any code that accepts a unidirectional [Mapper].
 *
 * [I] — the "external" type (DTO, DB entity).
 * [O] — the "domain" type (aggregate, value object).
 */
public interface BidirectionalMapper<I, O> : Mapper<I, O> {
    public fun reverseMap(output: O): I
}
