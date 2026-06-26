package co.electriccoin.zcash.ui.integration.test.screen.scan.view

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.filters.LargeTest
import co.electriccoin.zcash.test.UiTestPrerequisites
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.integration.test.common.IntegrationTestingActivity
import co.electriccoin.zcash.ui.integration.test.common.getPermissionPositiveButtonUiObject
import co.electriccoin.zcash.ui.integration.test.common.getStringResource
import co.electriccoin.zcash.ui.integration.test.common.getStringResourceWithArgs
import co.electriccoin.zcash.ui.integration.test.common.waitForDeviceIdle
import co.electriccoin.zcash.ui.screen.scan.ScanScreenState
import co.electriccoin.zcash.ui.screen.scan.ScanTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class ScanViewTest : UiTestPrerequisites() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<IntegrationTestingActivity>()

    private lateinit var testSetup: ScanViewTestSetup

    @Before
    fun prepareTestSetup() {
        testSetup =
            ScanViewTestSetup(composeTestRule).apply {
                setDefaultContent()
            }
    }

    @Test
    @LargeTest
    fun is_camera_permission_dialog_shown() {
        val permissionPositiveButtonUiObject = getPermissionPositiveButtonUiObject()

        // permission dialog displayed
        assertNotNull(permissionPositiveButtonUiObject)
        assertTrue(permissionPositiveButtonUiObject!!.exists())

        composeTestRule.onNodeWithTag(ScanTag.QR_FRAME).also {
            it.assertDoesNotExist()
        }

        composeTestRule.onNodeWithTag(ScanTag.CAMERA_VIEW).also {
            it.assertDoesNotExist()
        }

        // hide permission dialog
        permissionPositiveButtonUiObject.click()
    }

    @Test
    @LargeTest
    fun grant_camera_permission() {
        assertEquals(ScanScreenState.Permission, testSetup.getScanState())

        composeTestRule.onNodeWithTag(ScanTag.CAMERA_VIEW).also {
            it.assertDoesNotExist()
        }

        testSetup.grantPermission()

        composeTestRule
            .onNodeWithText(
                getStringResource(co.electriccoin.zcash.ui.design.R.string.general_cancel)
            ).also {
                it.assertIsDisplayed()
            }

        composeTestRule.onNodeWithTag(ScanTag.QR_FRAME).also {
            it.assertIsDisplayed()
        }

        assertEquals(ScanScreenState.Scanning, testSetup.getScanState())

        // we need to actively wait for the camera preview initialization
        waitForDeviceIdle(timeout = 5000.milliseconds)

        composeTestRule.onNodeWithTag(ScanTag.CAMERA_VIEW).also {
            it.assertIsDisplayed()
        }
    }

    @Test
    @LargeTest
    fun deny_camera_permission() {
        assertEquals(ScanScreenState.Permission, testSetup.getScanState())

        testSetup.denyPermission()

        assertEquals(ScanScreenState.Permission, testSetup.getScanState())

        composeTestRule.onNodeWithTag(ScanTag.CAMERA_VIEW).also {
            it.assertDoesNotExist()
        }

        composeTestRule
            .onNodeWithText(
                getStringResourceWithArgs(
                    resId = R.string.scan_cameraSettings,
                    getStringResource(R.string.app_name)
                )
            ).also {
                it.assertIsDisplayed()
            }

        composeTestRule.onNodeWithText(getStringResource(R.string.scan_openSettings), ignoreCase = true).also {
            it.assertIsDisplayed()
            it.assertHasClickAction()
        }
    }

    @Test
    @LargeTest
    fun open_settings_test() {
        testSetup.denyPermission()

        assertEquals(0, testSetup.getOnOpenSettingsCount())

        composeTestRule.onNodeWithText(getStringResource(R.string.scan_openSettings), ignoreCase = true).also {
            it.performClick()
        }

        assertEquals(1, testSetup.getOnOpenSettingsCount())
    }
}
