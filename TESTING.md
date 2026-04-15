# Testing Guide — Domain SDK Core

This document defines the testing strategy, naming conventions, test double
patterns, and rules for keeping the domain test suite fast, deterministic, and
maintainable.

---

## 1. Test taxonomy

| Type | What it tests | Location | I/O |
|---|---|---|---|
| **Unit** | A single function, operator, or contract | `commonTest` | None |
| **Contract** | A `fun interface` implementation fulfils its contract | `commonTest` | None |
| **Composition** | `DomainModule` wiring produces correct behaviour | `commonTest` | None |
| **Integration** | Data-layer impl against real DB/network | `androidTest` / data module | Yes |

**All tests in this SDK are unit or contract tests. Zero I/O.**

---

## 2. Test naming convention

Format: `` `<subject> - <condition> — <expected outcome>` ``

Rules:
- Use backtick names — no camelCase in test names.
- Subject = the thing under test (class, function, combinator).
- Condition = the specific input or state being exercised.
- Outcome = what must be true after the call.
- Use `—` (em-dash) to separate condition from outcome when the name would
  otherwise be ambiguous.

### Examples

```kotlin
// Good — specific subject, condition, outcome
fun `map - transforms success value`()
fun `flatMap - short-circuits on failure and does not invoke transform`()
fun `notBlankValidator - blank string — returns Validation error with correct field`()
fun `DomainPolicy.and - first violated — short-circuits without evaluating second`()
fun `validateAll - multiple failures — evaluates all validators`()

// Bad — too vague
fun `test map`()
fun `it works`()
fun `failure case`()
```

---

## 3. Test structure — Arrange / Act / Assert

Every test follows a strict three-section structure. No exceptions.

```kotlin
@Test
fun `maxLengthValidator - value at exact limit — returns Success`() {
    // Arrange
    val validator = maxLengthValidator("bio", 5)

    // Act
    val result = validator.validate("hello")

    // Assert
    assertIs<DomainResult.Success<Unit>>(result)
}
```

For trivial one-liners, collapsing is acceptable only when all three sections
are still clearly identifiable on the same line:

```kotlin
@Test
fun `asSuccess wraps value`() {
    assertEquals("x", "x".asSuccess().shouldBeSuccess())
}
```

---

## 4. Test doubles — canonical patterns

**Never use a mocking framework in `commonTest`.**
`fun interface` makes every contract testable with a one-line lambda.

### 4.1 Fixed providers (most common)

```kotlin
val clock = ClockProvider { 1_700_000_000_000L }
val ids   = IdProvider { "test-id" }
```

Use `testDeps` from `TestDoubles.kt` for the canonical combination.

### 4.2 Counting / sequencing doubles

```kotlin
var callCount = 0
val countingClock = ClockProvider { callCount++; TEST_NOW_MILLIS }

var idCounter = 0
val sequentialIds = IdProvider { "id-${++idCounter}" }
```

Or use `advancingClock()` / `sequentialIds()` from `TestDoubles.kt`.

### 4.3 Stub repositories (object expression)

```kotlin
val fakeRepo = object : UserRepository {
    override suspend fun findById(id: UserId) = fakeUser.asSuccess()
    override suspend fun save(entity: User) = Unit.asSuccess()
    override suspend fun delete(entity: User) = Unit.asSuccess()
}
```

**Name fakes by what they do, not by what they are:**
- `successRepo` — always returns success
- `failingRepo` — always returns a specific failure
- `capturingRepo` — records calls for assertion

### 4.4 Capturing doubles (verify interactions)

```kotlin
val capturingRepo = object : ItemRepository {
    val saved = mutableListOf<Item>()
    override suspend fun save(item: Item): DomainResult<Unit> {
        saved += item
        return Unit.asSuccess()
    }
}
// later...
assertEquals(1, capturingRepo.saved.size)
assertEquals("expected-id", capturingRepo.saved[0].id)
```

### 4.5 Assertion helpers from `TestDoubles.kt`

```kotlin
// Extract value or fail with a clear message
val value = result.shouldBeSuccess()

// Extract error or fail
val error = result.shouldBeFailure()

// Extract and cast to expected subtype
val validation = result.shouldFailWith<DomainError.Validation>()
assertEquals("email", validation.field)
```

---

## 5. Anti-patterns to avoid in domain tests

| Anti-pattern | Why it's wrong | Correct approach |
|---|---|---|
| `Mockito.mock(Repository::class.java)` | Framework dep in `commonTest`; breaks KMP | `object : Repository { ... }` |
| `every { repo.save(any()) } returns ...` | MockK not available in commonMain | Stub with object expression |
| `System.currentTimeMillis()` in test | Non-deterministic | `ClockProvider { FIXED_MS }` |
| `UUID.randomUUID()` in test | Non-deterministic | `IdProvider { "test-id" }` |
| `Thread.sleep()` in test | Flaky, slow | Use coroutine test dispatcher |
| Shared mutable state between tests | Order-dependent failures | Reset state in each test or use `var` locals |
| `runBlocking` in test | Blocks thread, hides suspension bugs | `kotlinx.coroutines.test.runTest` |
| Testing implementation details | Brittle, couples to internals | Test observable behaviour only |
| One assertion per file | Tests become test plans, not tests | Multiple focused `@Test` functions |

---

## 6. Coroutine tests

All `suspend` use cases and repositories must be tested with `runTest`:

```kotlin
@Test
fun `use case returns success on valid input`() = kotlinx.coroutines.test.runTest {
    val result = useCase(params)
    assertIs<DomainResult.Success<Item>>(result)
}
```

**Never use `runBlocking` in domain tests.**
`runTest` gives coroutine test dispatchers and virtual time for free.

---

## 7. Test file organisation

One test class per production class. Mirror the source package structure exactly:

```
commonMain/kotlin/com/domain/core/result/DomainResult.kt
commonTest/kotlin/com/domain/core/result/DomainResultTest.kt   ← unit
commonTest/kotlin/com/domain/core/result/DomainResultZipTest.kt ← operator group
```

When a file has multiple operator groups (e.g., `zip`, `flatMap`, error paths),
split into separate focused test classes rather than one god test class.

---

## 8. Test double reuse across features

Import from `com.domain.core.testing.TestDoubles`:

```kotlin
import com.domain.core.testing.testDeps
import com.domain.core.testing.fixedClock
import com.domain.core.testing.sequentialIds
import com.domain.core.testing.validationError
import com.domain.core.testing.shouldBeSuccess
import com.domain.core.testing.shouldFailWith
```

These helpers are in `commonTest` only — they are never shipped in the production
artifact. `TestDoubles.kt` is the single source of truth for test wiring defaults.

---

## 9. What makes a good domain test

A domain test is good when all of these are true:

- **Deterministic** — same code, same result, every run, every platform
- **Fast** — sub-millisecond; no I/O, no real clocks, no network
- **Isolated** — does not depend on other test execution order
- **Readable** — the test name is a complete sentence describing behaviour
- **Minimal** — tests one thing; if it fails, you know exactly why
- **Framework-free** — no Koin, Hilt, Mockito, MockK in `commonTest`
