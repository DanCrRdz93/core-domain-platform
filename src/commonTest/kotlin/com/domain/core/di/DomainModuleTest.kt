package com.domain.core.di

import com.domain.core.result.DomainResult
import com.domain.core.result.asSuccess
import com.domain.core.testing.shouldBeSuccess
import com.domain.core.testing.testDeps
import com.domain.core.testing.testDepsWithSequentialIds
import com.domain.core.usecase.SuspendUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Verifies that the [DomainModule] composition pattern compiles and behaves
 * correctly when implemented by a feature module.
 *
 * This test acts as a living contract: it demonstrates the canonical pattern
 * that feature modules must follow, without introducing any real business logic.
 */
class DomainModuleTest {

    // ── Minimal fake feature — only what's needed to test the pattern ─────────

    private data class Widget(val id: String, val label: String)
    private data class CreateWidgetParams(val label: String)

    private interface WidgetRepository {
        suspend fun save(widget: Widget): DomainResult<Unit>
    }

    private val inMemoryRepo = object : WidgetRepository {
        val saved = mutableListOf<Widget>()
        override suspend fun save(widget: Widget): DomainResult<Unit> {
            saved += widget
            return Unit.asSuccess()
        }
    }

    private class CreateWidgetUseCase(
        private val deps: DomainDependencies,
        private val repository: WidgetRepository,
    ) : SuspendUseCase<CreateWidgetParams, Widget> {
        override suspend fun invoke(params: CreateWidgetParams): DomainResult<Widget> {
            val widget = Widget(id = deps.idProvider.next(), label = params.label)
            return repository.save(widget).let { widget.asSuccess() }
        }
    }

    // ── Feature DomainModule contract ─────────────────────────────────────────

    private interface WidgetDomainModule : DomainModule {
        val createWidget: SuspendUseCase<CreateWidgetParams, Widget>
    }

    private class WidgetDomainModuleImpl(
        deps: DomainDependencies,
        widgetRepository: WidgetRepository,
    ) : WidgetDomainModule {
        override val createWidget = CreateWidgetUseCase(deps, widgetRepository)
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `DomainModule implementation assembles via constructor injection`() {
        val module: WidgetDomainModule = WidgetDomainModuleImpl(testDeps, inMemoryRepo)
        assertIs<WidgetDomainModule>(module)
    }

    @Test
    fun `use case exposed by module is invocable and returns Success`() =
        kotlinx.coroutines.test.runTest {
            val module = WidgetDomainModuleImpl(testDeps, inMemoryRepo)
            val result = module.createWidget(CreateWidgetParams("Sprocket"))
            assertEquals("Sprocket", result.shouldBeSuccess().label)
        }

    @Test
    fun `module uses injected IdProvider — id is deterministic`() =
        kotlinx.coroutines.test.runTest {
            val module = WidgetDomainModuleImpl(testDeps, inMemoryRepo)
            val widget = module.createWidget(CreateWidgetParams("Bolt")).shouldBeSuccess()
            assertEquals("test-id-001", widget.id)
        }

    @Test
    fun `swapping deps produces different deterministic output without changing module code`() =
        kotlinx.coroutines.test.runTest {
            val altDeps = testDepsWithSequentialIds("widget")
            val altModule = WidgetDomainModuleImpl(altDeps, inMemoryRepo)

            val w1 = altModule.createWidget(CreateWidgetParams("Nut")).shouldBeSuccess()
            val w2 = altModule.createWidget(CreateWidgetParams("Bolt")).shouldBeSuccess()

            assertEquals("widget-1", w1.id)
            assertEquals("widget-2", w2.id)
        }

    @Test
    fun `module can be typed as DomainModule marker — no feature leakage`() {
        val module: DomainModule = WidgetDomainModuleImpl(testDeps, inMemoryRepo)
        assertIs<DomainModule>(module)
    }
}
