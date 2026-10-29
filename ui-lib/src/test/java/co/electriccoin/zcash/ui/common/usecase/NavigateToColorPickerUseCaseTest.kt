package co.electriccoin.zcash.ui.common.usecase

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.navigation.NavBackStackEntry
import androidx.navigation.serialization.generateHashCode
import androidx.navigation.toRoute
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.screen.colorpicker.ColorPickerArgs
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [NavigateToColorPickerUseCase] opens the sheet seeded with the initial colour under a fresh request
 * for every collection, streams every pick live and ends on the last one however the sheet closes,
 * pops the sheet only while that very sheet is on top, keeps the last pick through a burst larger than
 * its buffer, ignores the events of another request, and knows whether a caller is collecting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigateToColorPickerUseCaseTest {
    private val forwarded = mutableListOf<Any>()
    private val router =
        mockk<NavigationRouter>(relaxed = true) {
            every { forward(*anyVararg()) } answers {
                (args[0] as Array<*>).filterNotNull().forEach { forwarded.add(it) }
            }
        }
    private val useCase = NavigateToColorPickerUseCase(router)

    @Test
    fun picksStreamLiveAndClosingEndsOnTheLastOne() =
        runTest(UnconfinedTestDispatcher()) {
            val emitted = collect(Color(INITIAL))

            val args = forwarded.single() as ColorPickerArgs
            assertEquals(Color(INITIAL).toArgb(), args.initialArgb)

            useCase.onPicked(Color(PICKED), args)
            useCase.onPicked(Color(OTHER), args)
            assertEquals(listOf(Color(PICKED), Color(OTHER)), emitted.values)
            assertFalse(emitted.job.isCompleted)

            useCase.onClosed(args)

            assertTrue(emitted.job.isCompleted)
            assertEquals(listOf(Color(PICKED), Color(OTHER)), emitted.values)
        }

    @Test
    fun closingWithoutAPickEndsWithNothing() =
        runTest(UnconfinedTestDispatcher()) {
            val emitted = collect(null)
            val args = forwarded.single() as ColorPickerArgs
            assertNull(args.initialArgb)

            useCase.onClosed(args)

            assertTrue(emitted.job.isCompleted)
            assertTrue(emitted.values.isEmpty())
        }

    @Test
    fun dismissPopsOnlyTheSheetOfItsOwnRequestWhileItIsOnTop() {
        val args = ColorPickerArgs(initialArgb = null)
        val block = slot<(NavBackStackEntry?) -> NavigationCommand?>()
        every { router.custom(capture(block)) } returns Unit

        useCase.dismiss(args)

        mockkStatic(NAV_BACK_STACK_ENTRY_KT)
        try {
            assertEquals(NavigationCommand.Back, block.captured(sheetOf(args)))
            assertNull(block.captured(sheetOf(args.copy(requestId = "other"))))
            assertNull(block.captured(mockk { every { destination.id } returns PICKER_ROUTE_ID + 1 }))
            assertNull(block.captured(null))
        } finally {
            unmockkStatic(NAV_BACK_STACK_ENTRY_KT)
        }
        verify(exactly = 0) { router.back() }
    }

    @Test
    fun aSheetClosedOffTheBackStackKeepsTheLastPickAndEnds() =
        runTest(UnconfinedTestDispatcher()) {
            val emitted = collect(Color(INITIAL))
            val args = forwarded.single() as ColorPickerArgs

            useCase.onPicked(Color(PICKED), args)
            useCase.onClosed(args)

            assertTrue(emitted.job.isCompleted)
            assertEquals(listOf(Color(PICKED)), emitted.values)
            verify(exactly = 0) { router.back() }
        }

    @Test
    fun ignoresTheEventsOfADifferentRequest() =
        runTest(UnconfinedTestDispatcher()) {
            val emitted = collect(null)
            val args = forwarded.single() as ColorPickerArgs
            val stale = args.copy(requestId = "stale")

            useCase.onPicked(Color(INITIAL), stale)
            useCase.onClosed(stale)
            assertTrue(emitted.values.isEmpty())
            assertFalse(emitted.job.isCompleted)

            useCase.onPicked(Color(PICKED), args)
            useCase.onClosed(args)
            assertEquals(listOf(Color(PICKED)), emitted.values)
            assertTrue(emitted.job.isCompleted)
        }

    @Test
    fun everyCollectionOpensItsOwnRequest() =
        runTest(UnconfinedTestDispatcher()) {
            val flow = useCase(Color(INITIAL))
            val first = mutableListOf<Color>()
            val second = mutableListOf<Color>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(first) }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(second) }

            val (firstArgs, secondArgs) = forwarded.map { it as ColorPickerArgs }
            assertNotEquals(firstArgs.requestId, secondArgs.requestId)

            useCase.onPicked(Color(PICKED), secondArgs)
            assertTrue(first.isEmpty())
            assertEquals(listOf(Color(PICKED)), second)
        }

    @Test
    fun aBurstLargerThanTheBufferStillEndsOnTheLastPick() =
        runTest {
            val values = mutableListOf<Color>()
            val job = backgroundScope.launch(StandardTestDispatcher(testScheduler)) { useCase(null).toList(values) }
            runCurrent()
            val args = forwarded.single() as ColorPickerArgs

            val picks = (1..BURST).map { Color(red = it, green = 0, blue = 0) }
            picks.forEach { useCase.onPicked(it, args) }
            useCase.onClosed(args)
            runCurrent()

            assertTrue(job.isCompleted)
            assertEquals(picks.last(), values.last())
        }

    @Test
    fun knowsWhetherACallerIsCollecting() =
        runTest(UnconfinedTestDispatcher()) {
            assertFalse(useCase.hasCollector())

            val emitted = collect(null)
            assertTrue(useCase.hasCollector())

            useCase.onClosed(forwarded.single() as ColorPickerArgs)
            assertTrue(emitted.job.isCompleted)
            assertFalse(useCase.hasCollector())
        }

    private fun TestScope.collect(initial: Color?): Collected {
        val values = mutableListOf<Color>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { useCase(initial).toList(values) }
        return Collected(values, job)
    }

    private fun sheetOf(args: ColorPickerArgs): NavBackStackEntry {
        val entry = mockk<NavBackStackEntry> { every { destination.id } returns PICKER_ROUTE_ID }
        every { entry.toRoute<ColorPickerArgs>(ColorPickerArgs::class) } returns args
        return entry
    }

    private class Collected(
        val values: List<Color>,
        val job: Job,
    )

    private companion object {
        const val INITIAL = 0xFF34C759
        const val PICKED = 0xFFBE38F3
        const val OTHER = 0xFF0088FF
        const val BURST = 20
        const val NAV_BACK_STACK_ENTRY_KT = "androidx.navigation.NavBackStackEntryKt"
        val PICKER_ROUTE_ID = ColorPickerArgs.serializer().generateHashCode()
    }
}
