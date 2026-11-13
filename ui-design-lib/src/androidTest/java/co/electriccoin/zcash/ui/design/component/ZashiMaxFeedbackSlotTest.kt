package co.electriccoin.zcash.ui.design.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.filters.MediumTest
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import org.junit.Rule
import org.junit.Test

class ZashiMaxFeedbackSlotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(feedback: MaxFeedback?) {
        composeTestRule.setContent {
            ZcashTheme {
                ZashiMaxFeedbackSlot(feedback = feedback)
            }
        }
    }

    @Test
    @MediumTest
    fun the_hint_is_shown_as_a_neutral_row() {
        setContent(MaxFeedback.Hint)

        composeTestRule.onNodeWithTag(ZashiMaxFeedbackSlotTags.HINT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ZashiMaxFeedbackSlotTags.ERROR).assertDoesNotExist()
    }

    @Test
    @MediumTest
    fun the_error_is_shown_as_an_error_row() {
        setContent(MaxFeedback.Error(MaxVariant.SEND))

        composeTestRule.onNodeWithTag(ZashiMaxFeedbackSlotTags.ERROR).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ZashiMaxFeedbackSlotTags.HINT).assertDoesNotExist()
    }

    @Test
    @MediumTest
    fun nothing_is_shown_without_feedback() {
        setContent(null)

        composeTestRule.onNodeWithTag(ZashiMaxFeedbackSlotTags.HINT).assertDoesNotExist()
        composeTestRule.onNodeWithTag(ZashiMaxFeedbackSlotTags.ERROR).assertDoesNotExist()
    }
}
