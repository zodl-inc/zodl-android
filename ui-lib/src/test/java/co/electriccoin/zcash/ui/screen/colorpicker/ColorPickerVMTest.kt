package co.electriccoin.zcash.ui.screen.colorpicker

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavBackStackEntry
import androidx.navigation.serialization.generateHashCode
import androidx.navigation.toRoute
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.usecase.NavigateToColorPickerUseCase
import co.electriccoin.zcash.ui.design.component.colorpicker.ColorPickerPalette
import co.electriccoin.zcash.ui.design.component.colorpicker.spectrumColor
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [ColorPickerVM] starts on the colour in its args as opaque, switches tabs, picks opaque colours
 * from the grid, the spectrum, the sliders, the fields and the swatches, reports each change of
 * the picked colour live, and closes once, keeping the last pick: the close button, back or a sheet
 * cleared without a close all end the caller's flow on it. Every close pops the sheet while it is on
 * top and nothing else. A sheet restored without a caller closes itself, and still closes on the
 * user's close when that first pop never arrived.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ColorPickerVMTest {
    private val forwarded = mutableListOf<ColorPickerArgs>()

    /** The top of the back stack, which a dismissal pops when it is the sheet that dismissal targets. */
    private var top: NavBackStackEntry? = null

    private var pops = 0

    private val router =
        mockk<NavigationRouter>(relaxed = true) {
            every { forward(*anyVararg()) } answers {
                (args[0] as Array<*>).filterIsInstance<ColorPickerArgs>().forEach { forwarded.add(it) }
            }
            every { custom(any()) } answers {
                if (firstArg<(NavBackStackEntry?) -> NavigationCommand?>()(top) == NavigationCommand.Back) {
                    pops++
                    top = null
                }
            }
        }

    private val navigateToColorPicker = spyk(NavigateToColorPickerUseCase(router))

    /** Collects every view model's state, which is shared only while something subscribes. */
    private lateinit var collectors: CoroutineScope

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        collectors = CoroutineScope(UnconfinedTestDispatcher())
        mockkStatic(NAV_BACK_STACK_ENTRY_KT)
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic(NAV_BACK_STACK_ENTRY_KT)
        collectors.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun startsOnTheInitialColorAsOpaqueInTheGrid() {
        val vm = vm(initial = Color(HALF_PURPLE))

        val state = vm.state.value
        assertEquals(Color(PURPLE_OPAQUE), state.picked)
        assertEquals(PURPLE_OPAQUE, assertIs<ColorPickerContentState.Grid>(state.content).selected.toArgb())
        assertEquals(listOf(true, false, false), state.tabs.map { it.isSelected })
    }

    @Test
    fun startsOnOpaqueWhiteWithoutAColor() {
        assertEquals(Color.White, vm(initial = null).state.value.picked)
    }

    @Test
    fun tabsSwitchTheContent() {
        val vm = vm()

        vm.state.value.tabs[1]
            .onClick()
        assertIs<ColorPickerContentState.Spectrum>(vm.state.value.content)

        vm.state.value.tabs[2]
            .onClick()
        assertIs<ColorPickerContentState.Sliders>(vm.state.value.content)
        assertEquals(
            listOf(false, false, true),
            vm.state.value.tabs
                .map { it.isSelected }
        )
    }

    @Test
    fun aGridColorIsPickedOpaque() {
        val vm = vm(initial = Color(HALF_PURPLE))
        val green = ColorPickerPalette.grid.last()

        assertIs<ColorPickerContentState.Grid>(vm.state.value.content).onSelect(green.copy(alpha = 0.5f))

        assertEquals(green, vm.state.value.picked)
        assertEquals(green, assertIs<ColorPickerContentState.Grid>(vm.state.value.content).selected)
    }

    @Test
    fun theSpectrumPicksTheColorUnderTheTouchAndRingsIt() {
        val vm = vm()
        vm.state.value.tabs[1]
            .onClick()
        val touch = Offset(0.25f, 0.75f)

        assertIs<ColorPickerContentState.Spectrum>(vm.state.value.content).onPick(touch)

        val spectrum = assertIs<ColorPickerContentState.Spectrum>(vm.state.value.content)
        assertEquals(touch, spectrum.position)
        assertEquals(spectrumColor(touch), vm.state.value.picked)
    }

    @Test
    fun aSliderSetsItsChannel() {
        val vm = vm(initial = Color.Black)
        vm.state.value.tabs[2]
            .onClick()

        sliders(vm).channels[0].onValueChange(1f)

        assertEquals(Color.Red, vm.state.value.picked)
        assertEquals("255", sliders(vm).channels[0].text)
        assertEquals("FF0000", sliders(vm).hex)
    }

    @Test
    fun aTypedChannelAppliesOnceItIsAValueAndKeepsTheDraft() {
        val vm = vm(initial = Color.Black)
        vm.state.value.tabs[2]
            .onClick()

        sliders(vm).channels[1].onTextChange("3x00")
        assertEquals("300", sliders(vm).channels[1].text)
        assertEquals(Color.Black, vm.state.value.picked)

        sliders(vm).channels[1].onTextChange("128")
        assertEquals("128", sliders(vm).channels[1].text)
        assertEquals(
            PARTIAL_GREEN,
            vm.state.value.picked
                .toArgb()
        )
        assertEquals("008000", sliders(vm).hex)
    }

    @Test
    fun aTypedHexAppliesAtSixDigits() {
        val vm = vm(initial = Color.Black)
        vm.state.value.tabs[2]
            .onClick()

        sliders(vm).onHexChange("#be3")
        assertEquals("BE3", sliders(vm).hex)
        assertEquals(Color.Black, vm.state.value.picked)

        sliders(vm).onHexChange("be38f3")
        assertEquals(
            PURPLE_OPAQUE,
            vm.state.value.picked
                .toArgb()
        )
        assertEquals("190", sliders(vm).channels[0].text)
    }

    @Test
    fun aSwatchIsSelectedWhenItIsThePickedColor() {
        val vm = vm()
        val swatch = vm.state.value.swatches[6]

        swatch.onClick(swatch.color)

        assertEquals(swatch.color, vm.state.value.picked)
        assertEquals(
            listOf(6),
            vm.state.value.swatches.indices
                .filter {
                    vm.state.value.swatches[it]
                        .isSelected
                }
        )
    }

    @Test
    fun everyChangeOfThePickedColorIsReportedOnceAsItHappens() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, emitted) = openFromCaller(Color(PURPLE_OPAQUE))
            val green = ColorPickerPalette.grid.last()

            assertIs<ColorPickerContentState.Grid>(vm.state.value.content).onSelect(green)
            assertIs<ColorPickerContentState.Grid>(vm.state.value.content).onSelect(green)
            vm.state.value.tabs[2]
                .onClick()
            sliders(vm).onHexChange("12345")
            sliders(vm).onHexChange("123456")

            assertEquals(listOf(green, Color(0xFF123456)), emitted.values)
            assertFalse(emitted.job.isCompleted)
        }

    @Test
    fun theCloseButtonEndsTheFlowOnTheLastPickOnce() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, emitted) = openFromCaller(Color(HALF_PURPLE))
            val green = ColorPickerPalette.grid.last()
            assertIs<ColorPickerContentState.Grid>(vm.state.value.content).onSelect(green)

            vm.state.value.doneButton
                .onClick()
            vm.state.value.onBack()

            assertEquals(listOf(green), emitted.values)
            assertTrue(emitted.job.isCompleted)
            assertEquals(1, pops)
            verify(exactly = 0) { router.back() }
        }

    @Test
    fun backKeepsTheLastPickAndClosesOnce() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, emitted) = openFromCaller(Color(PURPLE_OPAQUE))
            val green = ColorPickerPalette.grid.last()
            assertIs<ColorPickerContentState.Grid>(vm.state.value.content).onSelect(green)

            vm.state.value.onBack()
            vm.state.value.doneButton
                .onClick()

            assertEquals(listOf(green), emitted.values)
            assertTrue(emitted.job.isCompleted)
            assertEquals(1, pops)
        }

    @Test
    fun aPickerPoppedWithoutACloseKeepsTheLastPickAndEnds() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, emitted) = openFromCaller(Color(PURPLE_OPAQUE))
            val green = ColorPickerPalette.grid.last()
            assertIs<ColorPickerContentState.Grid>(vm.state.value.content).onSelect(green)

            clear(vm)

            assertEquals(listOf(green), emitted.values)
            assertTrue(emitted.job.isCompleted)
            assertEquals(0, pops)
            verify(exactly = 0) { router.custom(any()) }
        }

    @Test
    fun aPickerClearedAfterItsCloseSendsNothingMore() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, emitted) = openFromCaller(Color(PURPLE_OPAQUE))
            val green = ColorPickerPalette.grid.last()
            assertIs<ColorPickerContentState.Grid>(vm.state.value.content).onSelect(green)
            vm.state.value.doneButton
                .onClick()

            clear(vm)

            assertEquals(listOf(green), emitted.values)
            assertEquals(1, pops)
        }

    @Test
    fun aCloseWhileAnotherScreenIsOnTopPopsNothing() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, emitted) = openFromCaller(Color(PURPLE_OPAQUE))
            top = otherScreen()

            vm.state.value.doneButton
                .onClick()

            assertTrue(emitted.job.isCompleted)
            assertEquals(0, pops)
        }

    @Test
    fun aPickerRestoredWithoutACallerClosesItselfOnce() {
        val args = ColorPickerArgs(initialArgb = PURPLE_OPAQUE)
        top = sheetOf(args)
        val vm = vm(args)

        assertEquals(1, pops)

        vm.state.value.doneButton
            .onClick()
        clear(vm)

        assertEquals(1, pops)
        verify(exactly = 1) { navigateToColorPicker.onClosed(args) }
    }

    @Test
    fun aRestoredPickerWhosePopNeverArrivedStillClosesOnTheUsersClose() {
        val args = ColorPickerArgs(initialArgb = PURPLE_OPAQUE)
        val vm = vm(args)
        top = sheetOf(args)

        vm.state.value.doneButton
            .onClick()
        assertEquals(1, pops)

        top = otherScreen()
        vm.state.value.onBack()
        clear(vm)

        assertEquals(1, pops)
        verify(exactly = 1) { navigateToColorPicker.onClosed(args) }
    }

    /** Starts a caller's collection, which opens the sheet, and the sheet's view model on its request. */
    private fun TestScope.openFromCaller(initial: Color): Pair<ColorPickerVM, Collected> {
        val values = mutableListOf<Color>()
        val job =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                navigateToColorPicker(initial).toList(values)
            }
        top = sheetOf(forwarded.single())
        return vm(forwarded.single()) to Collected(values, job)
    }

    /** The back stack entry of the sheet opened for [args]. */
    private fun sheetOf(args: ColorPickerArgs): NavBackStackEntry {
        val entry = mockk<NavBackStackEntry> { every { destination.id } returns PICKER_ROUTE_ID }
        every { entry.toRoute<ColorPickerArgs>(ColorPickerArgs::class) } returns args
        return entry
    }

    private fun otherScreen() = mockk<NavBackStackEntry> { every { destination.id } returns PICKER_ROUTE_ID + 1 }

    /** Clears [vm] as the navigation does when its sheet leaves the back stack. */
    private fun clear(vm: ColorPickerVM) {
        val store = ViewModelStore()
        ViewModelProvider.create(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
            }
        )[ColorPickerVM::class]
        store.clear()
    }

    private class Collected(
        val values: List<Color>,
        val job: Job,
    )

    private fun vm(initial: Color? = null) = vm(ColorPickerArgs(initialArgb = initial?.toArgb()))

    private fun vm(args: ColorPickerArgs) =
        ColorPickerVM(args = args, navigateToColorPicker = navigateToColorPicker)
            .also { vm -> collectors.launch { vm.state.collect { } } }

    private fun sliders(vm: ColorPickerVM) = assertIs<ColorPickerContentState.Sliders>(vm.state.value.content)

    private companion object {
        const val HALF_PURPLE = 0x80BE38F3
        const val PURPLE_OPAQUE = 0xFFBE38F3.toInt()
        const val PARTIAL_GREEN = 0xFF008000.toInt()
        const val NAV_BACK_STACK_ENTRY_KT = "androidx.navigation.NavBackStackEntryKt"
        val PICKER_ROUTE_ID = ColorPickerArgs.serializer().generateHashCode()
    }
}
