package co.electriccoin.zcash.ui.screen.colorpicker

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The colour picker sheet: a tab tap reports that tab, the swatch of the picked colour is the
 * selected one, named by its hex, and the Sliders tab's hex field shows the picked colour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColorPickerViewTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val tabs = mutableListOf<ColorPickerTab>()

    private val picks = mutableListOf<Color>()

    @Test
    fun tappingATabReportsIt() {
        show(ColorPickerTab.GRID)

        tab(R.string.colorPicker_spectrum).performClick()
        tab(R.string.colorPicker_sliders).performClick()

        assertEquals(listOf(ColorPickerTab.SPECTRUM, ColorPickerTab.SLIDERS), tabs)
    }

    @Test
    fun thePickedSwatchIsReportedSelected() {
        show(ColorPickerTab.GRID)

        composeTestRule.onNodeWithContentDescription("#$PICKED_HEX").assertIsSelected()
        composeTestRule.onNodeWithContentDescription("#$OTHER_HEX").assertIsNotSelected().performClick()

        assertEquals(listOf(Color(OTHER)), picks)
    }

    @Test
    fun theHexFieldShowsThePickedColor() {
        show(ColorPickerTab.SLIDERS)

        composeTestRule
            .onNodeWithContentDescription(string(R.string.colorPicker_hex))
            .assertTextEquals(PICKED_HEX, includeEditableText = true)
    }

    private fun show(tab: ColorPickerTab) {
        val state =
            ColorPickerState.create(
                selection = ColorPickerSelection.initial(Color(PICKED)).copy(tab = tab),
                onTabSelect = { tabs += it },
                onColorSelect = { picks += it },
                onSpectrumPick = { _: Offset -> },
                onChannelChange = { _, _ -> },
                onFieldChange = { _, _ -> },
                onBack = {},
            )
        composeTestRule.setContent { ZcashTheme { ColorPickerView(state) } }
        composeTestRule.waitForIdle()
    }

    /**
     * The tab cell labelled [label]. The tab row subcomposes its cells to measure them, so only the
     * placed copy of each label is the one on screen.
     */
    private fun tab(label: Int) =
        composeTestRule.onNode(hasText(string(label)) and SemanticsMatcher("is placed") { it.layoutInfo.isPlaced })

    private fun string(resource: Int) = RuntimeEnvironment.getApplication().getString(resource)

    private companion object {
        const val PICKED = 0xFF0088FF
        const val PICKED_HEX = "0088FF"
        const val OTHER = 0xFFFF383C
        const val OTHER_HEX = "FF383C"
    }
}
