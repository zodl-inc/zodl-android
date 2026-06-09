package co.electriccoin.zcash.ui.integration.test.screen.scan.view

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import co.electriccoin.zcash.test.UiTestPrerequisites
import co.electriccoin.zcash.ui.integration.test.common.IntegrationTestingActivity
import co.electriccoin.zcash.ui.integration.test.common.getPermissionPositiveButtonUiObject
import co.electriccoin.zcash.ui.screen.scan.ScanScreenState
import co.electriccoin.zcash.ui.screen.scan.ScanTag
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ScanViewIntegrationTest : UiTestPrerequisites() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<IntegrationTestingActivity>()

    private lateinit var testSetup: ScanViewTestSetup

    @Before
    fun prepare_test_setup() {
        testSetup = ScanViewTestSetup(composeTestRule)
    }

    @Test
    @LargeTest
    fun scan_state_restoration() {
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            testSetup.DefaultContent()
        }

        assertEquals(testSetup.getScanState(), ScanScreenState.Permission)

        testSetup.grantPermission()

        assertEquals(testSetup.getScanState(), ScanScreenState.Scanning)

        restorationTester.emulateSavedInstanceStateRestore()

        assertEquals(testSetup.getScanState(), ScanScreenState.Scanning)
    }

    @Test
    @LargeTest
    fun scan_permission_dialog_restoration() {
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            testSetup.DefaultContent()
        }

        val permissionPositiveButtonUiObject = getPermissionPositiveButtonUiObject()

        // permission dialog displayed
        Assert.assertNotNull(permissionPositiveButtonUiObject)
        Assert.assertTrue(permissionPositiveButtonUiObject!!.exists())

        restorationTester.emulateSavedInstanceStateRestore()

        // permission dialog still exists
        Assert.assertNotNull(permissionPositiveButtonUiObject)
        Assert.assertTrue(permissionPositiveButtonUiObject.exists())

        testSetup.denyPermission()
    }

    // Crashes on API 34+ in Compose SlotWriter during disposal
    // (Compose + CameraX + StateRestorationTester interop).
    @Test
    @LargeTest
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.TIRAMISU)
    fun scan_views_restoration() {
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            testSetup.DefaultContent()
        }

        testSetup.grantPermission()

        composeTestRule.onNodeWithTag(ScanTag.QR_FRAME).also {
            it.assertIsDisplayed()
        }

        restorationTester.emulateSavedInstanceStateRestore()

        // scan frame is still displayed
        composeTestRule.onNodeWithTag(ScanTag.QR_FRAME).also {
            it.assertIsDisplayed()
        }

        // We don't run this test and its assertion on the camera view, as we'd need to wait for its
        // layout as we already do in the ScanViewTest.grant_system_permission(). So we can speed up
        // the test by omitting this assertion.
    }
}
