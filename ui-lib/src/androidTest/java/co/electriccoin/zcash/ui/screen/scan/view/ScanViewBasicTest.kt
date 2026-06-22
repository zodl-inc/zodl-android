package co.electriccoin.zcash.ui.screen.scan.view

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.filters.MediumTest
import androidx.test.rule.GrantPermissionRule
import co.electriccoin.zcash.test.UiTestPrerequisites
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.screen.scan.ScanScreenState
import co.electriccoin.zcash.ui.screen.scan.ScanTag
import co.electriccoin.zcash.ui.test.getStringResource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

// The tests are built with the presumption that we have camera permission granted before each test.
// Its ensured by GrantPermissionRule component. More complex UI and integration tests can be found
// in the ui-integration-test-lib module.
class ScanViewBasicTest : UiTestPrerequisites() {
    @get:Rule
    val composeTestRule = createComposeRule()

    // To automatically have CAMERA permission granted for all test in the class. Note, there is no
    // way to revoke the granted permission after it's granted.
    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test
    @MediumTest
    fun cancel() {
        val testSetup = newTestSetup()

        assertEquals(0, testSetup.getOnBackCount())

        composeTestRule
            .onNodeWithText(
                getStringResource(co.electriccoin.zcash.ui.design.R.string.general_cancel)
            ).also {
                it.performClick()
            }

        assertEquals(1, testSetup.getOnBackCount())
    }

    @Test
    @MediumTest
    fun check_all_ui_elements_displayed() {
        newTestSetup()

        // Permission granted ui items (visible):

        composeTestRule
            .onNodeWithText(
                getStringResource(co.electriccoin.zcash.ui.design.R.string.general_cancel)
            ).also {
                it.assertIsDisplayed()
            }

        // We don't test camera view, as it's not guaranteed to be laid out already.

        composeTestRule.onNodeWithTag(ScanTag.QR_FRAME).also {
            it.assertIsDisplayed()
        }

        composeTestRule.onNodeWithContentDescription(getStringResource(R.string.scan_torch_content_description)).also {
            it.assertIsDisplayed()
        }

        // Permission denied ui items (not visible):

        composeTestRule
            .onNodeWithText(
                text = getStringResource(R.string.scan_settings_button),
                ignoreCase = true
            ).also {
                it.assertDoesNotExist()
            }
    }

    @Test
    @MediumTest
    fun scan_state() {
        val testSetup = newTestSetup()

        assertEquals(ScanScreenState.Scanning, testSetup.getScanState())
    }

    private fun newTestSetup() =
        ScanViewBasicTestSetup(composeTestRule).apply {
            setDefaultContent()
        }
}
