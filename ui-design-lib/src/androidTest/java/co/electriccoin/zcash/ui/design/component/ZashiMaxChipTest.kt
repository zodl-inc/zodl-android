package co.electriccoin.zcash.ui.design.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ZashiMaxChipTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val label =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .getString(R.string.general_max)

    private var clicks = 0

    private fun setContent(
        isEnabled: Boolean,
        isLoading: Boolean
    ) {
        clicks = 0
        composeTestRule.setContent {
            ZcashTheme {
                ZashiMaxChip(SendMaxChipState(isEnabled = isEnabled, isLoading = isLoading, onClick = { clicks++ }))
            }
        }
    }

    @Test
    @MediumTest
    fun enabled_click_invokes_callback() {
        setContent(isEnabled = true, isLoading = false)
        composeTestRule.onNodeWithText(label).assertIsDisplayed().performClick()
        assertEquals(1, clicks)
    }

    @Test
    @MediumTest
    fun disabled_click_is_ignored() {
        setContent(isEnabled = false, isLoading = false)
        composeTestRule.onNodeWithText(label).assertIsDisplayed().performClick()
        assertEquals(0, clicks)
    }

    @Test
    @MediumTest
    fun loading_shows_progress_instead_of_label_and_ignores_click() {
        setContent(isEnabled = true, isLoading = true)
        composeTestRule.onNodeWithText(label).assertDoesNotExist()
        composeTestRule.onNodeWithTag(ZashiMaxChipTags.PROGRESS).assertIsDisplayed().performClick()
        assertEquals(0, clicks)
    }
}
