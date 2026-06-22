package co.electriccoin.zcash.ui.screen.onboarding.view

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.filters.MediumTest
import co.electriccoin.zcash.test.UiTestPrerequisites
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.screen.onboarding.OnboardingTestSetup
import co.electriccoin.zcash.ui.test.getStringResource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlin.test.Ignore

class OnboardingViewTest : UiTestPrerequisites() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun newTestSetup(): OnboardingTestSetup =
        OnboardingTestSetup(composeTestRule).apply {
            setDefaultContent()
        }

    @Test
    @MediumTest
    fun layout() {
        newTestSetup()

        composeTestRule
            .onNodeWithText(
                text = getStringResource(R.string.plainOnboarding_button_createNewWallet),
                ignoreCase = true
            ).also {
                it.assertExists()
                it.assertIsEnabled()
                it.assertHasClickAction()
            }

        composeTestRule
            .onNodeWithText(
                text = getStringResource(R.string.plainOnboarding_button_restoreWallet),
                ignoreCase = true
            ).also {
                it.assertExists()
                it.assertIsEnabled()
                it.assertHasClickAction()
            }
    }

    @Test
    @MediumTest
    fun click_create_wallet() {
        val testSetup = newTestSetup()

        val newWalletButton =
            composeTestRule.onNodeWithText(
                text = getStringResource(R.string.plainOnboarding_button_createNewWallet),
                ignoreCase = true
            )
        newWalletButton.performClick()

        assertEquals(1, testSetup.getOnCreateWalletCallbackCount())
        assertEquals(0, testSetup.getOnImportWalletCallbackCount())
    }

    @Test
    @MediumTest
    @Ignore("Disabling this until [SemanticNodeInteraction.performScrollTo] works as expected")
    fun click_import_wallet() {
        val testSetup = newTestSetup()

        val newWalletButton =
            composeTestRule.onNodeWithText(
                text = getStringResource(R.string.plainOnboarding_button_restoreWallet),
                ignoreCase = true
            )
        newWalletButton.performClick()

        assertEquals(1, testSetup.getOnImportWalletCallbackCount())
        assertEquals(0, testSetup.getOnCreateWalletCallbackCount())
    }
}
