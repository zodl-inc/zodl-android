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

    private fun setContent(
        hint: ZashiMaxHintRowState?,
        banner: ZashiInlineBannerState?
    ) {
        composeTestRule.setContent {
            ZcashTheme {
                ZashiMaxFeedbackSlot(hint = hint, banner = banner)
            }
        }
    }

    @Test
    @MediumTest
    fun the_banner_wins_over_the_hint() {
        setContent(hint = maxFundsConfirmingHintState(), banner = maxFailedBannerState())

        composeTestRule.onNodeWithTag(ZashiMaxFeedbackSlotTags.BANNER).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ZashiMaxFeedbackSlotTags.HINT).assertDoesNotExist()
    }

    @Test
    @MediumTest
    fun a_lone_hint_is_shown() {
        setContent(hint = maxFundsConfirmingHintState(), banner = null)

        composeTestRule.onNodeWithTag(ZashiMaxFeedbackSlotTags.HINT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ZashiMaxFeedbackSlotTags.BANNER).assertDoesNotExist()
    }

    @Test
    @MediumTest
    fun nothing_is_shown_without_feedback() {
        setContent(hint = null, banner = null)

        composeTestRule.onNodeWithTag(ZashiMaxFeedbackSlotTags.HINT).assertDoesNotExist()
        composeTestRule.onNodeWithTag(ZashiMaxFeedbackSlotTags.BANNER).assertDoesNotExist()
    }
}
