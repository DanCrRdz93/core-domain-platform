package com.domain.core.mapper

import kotlin.test.Test
import kotlin.test.assertEquals

class MapperTest {

    private data class UserDto(val id: String, val name: String, val age: Int)
    private data class User(val id: String, val fullName: String)
    private data class UserSummary(val label: String)

    private val dtoToUser = Mapper<UserDto, User> { dto ->
        User(id = dto.id, fullName = dto.name)
    }

    @Test
    fun `map transforms single input to output`() {
        val dto = UserDto(id = "1", name = "Alice", age = 30)
        val user = dtoToUser.map(dto)
        assertEquals("1", user.id)
        assertEquals("Alice", user.fullName)
    }

    @Test
    fun `mapList transforms a list of inputs`() {
        val dtos = listOf(
            UserDto("1", "Alice", 30),
            UserDto("2", "Bob", 25),
        )
        val users = dtoToUser.mapList(dtos)
        assertEquals(2, users.size)
        assertEquals("Alice", users[0].fullName)
        assertEquals("Bob", users[1].fullName)
    }

    @Test
    fun `mapList on empty list returns empty list`() {
        val result = dtoToUser.mapList(emptyList())
        assertEquals(emptyList(), result)
    }

    @Test
    fun `andThen chains two mappers into a pipeline`() {
        val userToSummary = Mapper<User, UserSummary> { user ->
            UserSummary(label = "${user.fullName} (${user.id})")
        }
        val dtoToSummary = dtoToUser.andThen(userToSummary)

        val result = dtoToSummary.map(UserDto("42", "Charlie", 28))
        assertEquals("Charlie (42)", result.label)
    }

    @Test
    fun `BidirectionalMapper maps in both directions`() {
        val biMapper = object : BidirectionalMapper<UserDto, User> {
            override fun map(input: UserDto) = User(id = input.id, fullName = input.name)
            override fun reverseMap(output: User) = UserDto(id = output.id, name = output.fullName, age = 0)
        }

        val user = biMapper.map(UserDto("1", "Alice", 30))
        assertEquals("Alice", user.fullName)

        val dto = biMapper.reverseMap(User("1", "Alice"))
        assertEquals("1", dto.id)
        assertEquals("Alice", dto.name)
    }
}
