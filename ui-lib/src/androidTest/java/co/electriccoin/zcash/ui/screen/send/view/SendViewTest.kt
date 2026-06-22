package co.electriccoin.zcash.ui.screen.send.view

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.filters.MediumTest
import cash.z.ecc.android.sdk.ext.collectWith
import cash.z.ecc.android.sdk.fixture.WalletAddressFixture
import cash.z.ecc.android.sdk.fixture.WalletFixture
import cash.z.ecc.android.sdk.model.Memo
import cash.z.ecc.android.sdk.model.MonetarySeparators
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.model.ZecSend
import cash.z.ecc.sdk.fixture.ZecRequestFixture
import cash.z.ecc.sdk.fixture.ZecSendFixture
import co.electriccoin.zcash.test.UiTestPrerequisites
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.screen.send.SendTag
import co.electriccoin.zcash.ui.screen.send.SendViewTestSetup
import co.electriccoin.zcash.ui.screen.send.assertOnForm
import co.electriccoin.zcash.ui.screen.send.assertOnSendFailure
import co.electriccoin.zcash.ui.screen.send.assertSendDisabled
import co.electriccoin.zcash.ui.screen.send.assertSendEnabled
import co.electriccoin.zcash.ui.screen.send.clickCreateAndSend
import co.electriccoin.zcash.ui.screen.send.clickScanner
import co.electriccoin.zcash.ui.screen.send.clickSettingsTopAppBarMenu
import co.electriccoin.zcash.ui.screen.send.dismissFailureDialog
import co.electriccoin.zcash.ui.screen.send.model.SendStage
import co.electriccoin.zcash.ui.screen.send.setAmount
import co.electriccoin.zcash.ui.screen.send.setMemo
import co.electriccoin.zcash.ui.screen.send.setValidAddress
import co.electriccoin.zcash.ui.screen.send.setValidAmount
import co.electriccoin.zcash.ui.screen.send.setValidMemo
import co.electriccoin.zcash.ui.test.getStringResource
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SendViewTest : UiTestPrerequisites() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun newTestSetup(
        sendStage: SendStage = SendStage.Form,
        zecSend: ZecSend? = null,
        hasCameraFeature: Boolean = true
    ) = SendViewTestSetup(
        composeTestRule,
        sendStage,
        zecSend,
        hasCameraFeature
    ).apply {
        setDefaultContent()
    }

    @Test
    @MediumTest
    fun create_button_disabled() {
        @Suppress("UNUSED_VARIABLE")
        val testSetup = newTestSetup()

        composeTestRule.onNodeWithTag(SendTag.SEND_FORM_BUTTON).also {
            it.assertExists()
            it.assertIsNotEnabled()
        }
    }

    @Test
    @MediumTest
    @Ignore("Currently disabled. Will be implemented as part of #1260")
    fun create_request_no_memo() =
        runTest {
            val testSetup = newTestSetup()

            assertEquals(0, testSetup.getOnCreateCount())
            assertEquals(null, testSetup.getLastZecSend())

            composeTestRule.setValidAmount()
            composeTestRule.setValidAddress()
            composeTestRule.clickCreateAndSend()

            launch {
                testSetup.mutableActionExecuted.collectWith(this) {
                    if (!it) return@collectWith

                    assertEquals(1, testSetup.getOnCreateCount())

                    launch {
                        testSetup.getLastZecSend().also {
                            assertNotNull(it)
                            assertEquals(WalletAddressFixture.sapling(), it.destination)
                            assertEquals(Zatoshi(12345678900000), it.amount)
                            assertEquals(ZecRequestFixture.MESSAGE.value, it.memo.value)
                        }
                    }
                    this.cancel()
                }
            }
        }

    @Test
    @MediumTest
    @Ignore("Currently disabled. Will be implemented as part of #1260")
    fun create_request_with_memo() =
        runTest {
            val testSetup = newTestSetup()

            assertEquals(0, testSetup.getOnCreateCount())
            assertEquals(null, testSetup.getLastZecSend())

            composeTestRule.setValidAmount()
            composeTestRule.setValidAddress()
            composeTestRule.setValidMemo()

            composeTestRule.clickCreateAndSend()

            launch {
                testSetup.mutableActionExecuted.collectWith(this) {
                    if (!it) return@collectWith

                    assertEquals(1, testSetup.getOnCreateCount())

                    launch {
                        testSetup.getLastZecSend().also {
                            assertNotNull(it)
                            assertEquals(WalletAddressFixture.sapling(), it.destination)
                            assertEquals(Zatoshi(12345678900000), it.amount)
                            assertEquals(ZecRequestFixture.MESSAGE.value, it.memo.value)
                        }
                    }
                    this.cancel()
                }
            }
        }

    @Test
    @MediumTest
    @Ignore("Currently disabled. Will be implemented as part of #1260")
    fun check_regex_functionality_valid_inputs() =
        runTest {
            val testSetup = newTestSetup()
            val separators = MonetarySeparators.current()

            assertEquals(0, testSetup.getOnCreateCount())
            assertEquals(null, testSetup.getLastZecSend())
            composeTestRule.assertSendDisabled()

            composeTestRule.setValidAmount()
            composeTestRule.setValidAddress()
            composeTestRule.setValidMemo()
            composeTestRule.assertSendEnabled()

            composeTestRule.setAmount("123")
            composeTestRule.assertSendEnabled()

            // e.g. 123,456
            composeTestRule.setAmount("123${separators.grouping}456")
            composeTestRule.assertSendEnabled()

            // e.g. 123.
            composeTestRule.setAmount("123${separators.decimal}")
            composeTestRule.assertSendEnabled()

            // e.g. 123,456.
            composeTestRule.setAmount("123${separators.grouping}456${separators.decimal}")
            composeTestRule.assertSendEnabled()

            // e.g. 123,456.789
            composeTestRule.setAmount("123${separators.grouping}456${separators.decimal}789")
            composeTestRule.assertSendEnabled()

            composeTestRule.clickCreateAndSend()

            launch {
                testSetup.mutableActionExecuted.collectWith(this) {
                    if (!it) return@collectWith

                    assertEquals(1, testSetup.getOnCreateCount())

                    launch {
                        testSetup.getLastZecSend().also {
                            assertNotNull(it)
                            assertEquals(WalletAddressFixture.sapling(), it.destination)
                            assertEquals(Zatoshi(12345678900000), it.amount)
                            assertEquals(ZecRequestFixture.MESSAGE.value, it.memo.value)
                        }
                    }
                    this.cancel()
                }
            }
        }

    @Test
    @MediumTest
    fun check_regex_functionality_invalid_inputs() =
        runTest {
            val testSetup = newTestSetup()
            val separators = MonetarySeparators.current()

            assertEquals(0, testSetup.getOnCreateCount())
            assertEquals(null, testSetup.getLastZecSend())
            composeTestRule.assertSendDisabled()

            composeTestRule.setAmount("aaa")
            composeTestRule.assertSendDisabled()

            composeTestRule.setAmount("123aaa")
            composeTestRule.assertSendDisabled()

            // e.g. ,.
            composeTestRule.setAmount("${separators.grouping}${separators.decimal}")
            composeTestRule.assertSendDisabled()

            // e.g. 123,.
            composeTestRule.setAmount("123${separators.grouping}${separators.decimal}")
            composeTestRule.assertSendDisabled()

            // e.g. 1,2,3
            composeTestRule.setAmount("1${separators.grouping}2${separators.grouping}3")
            composeTestRule.assertSendDisabled()

            // e.g. 1.2.3
            composeTestRule.setAmount("1${separators.decimal}2${separators.decimal}3")
            composeTestRule.assertSendDisabled()

            assertEquals(0, testSetup.getOnCreateCount())
            assertEquals(null, testSetup.getLastZecSend())
            composeTestRule.assertSendDisabled()
        }

    @Test
    @MediumTest
    @Ignore("Currently disabled. Will be implemented as part of #1260")
    fun max_memo_length() =
        runTest {
            val testSetup = newTestSetup()

            composeTestRule.setValidAmount()
            composeTestRule.setValidAddress()

            val input =
                buildString {
                    while (Memo.isWithinMaxLength(toString())) {
                        append("a")
                    }
                    // To align with the length limit restriction
                    deleteCharAt(length - 1)
                }

            composeTestRule.setMemo(input)

            composeTestRule.clickCreateAndSend()

            launch {
                testSetup.mutableActionExecuted.collectWith(this) {
                    if (!it) return@collectWith

                    assertEquals(1, testSetup.getOnCreateCount())

                    launch {
                        testSetup.getLastZecSend().also {
                            assertNotNull(it)
                            assertEquals(WalletAddressFixture.sapling(), it.destination)
                            assertEquals(Zatoshi(12345600000), it.amount)
                            assertTrue(it.memo.value.isEmpty())
                        }
                    }
                    this.cancel()
                }
            }
        }

    @Test
    @MediumTest
    fun on_settings_click_test() {
        val testSetup = newTestSetup()

        assertEquals(0, testSetup.getOnSettingsCount())

        composeTestRule.clickSettingsTopAppBarMenu()

        assertEquals(1, testSetup.getOnSettingsCount())
    }

    @Test
    @MediumTest
    @Ignore("Currently disabled. Will be implemented as part of #1260")
    fun back_on_confirmation() {
        val testSetup = newTestSetup()

        assertEquals(0, testSetup.getOnBackCount())

        composeTestRule.setValidAmount()
        composeTestRule.setValidAddress()
        composeTestRule.clickCreateAndSend()

        composeTestRule.assertOnForm()

        assertEquals(1, testSetup.getOnBackCount())
    }

    @Test
    @MediumTest
    fun back_on_send_failure() {
        val testSetup =
            newTestSetup(
                SendStage.SendFailure("Test error message"),
                runBlocking { ZecSendFixture.new() }
            )

        assertEquals(0, testSetup.getOnBackCount())

        composeTestRule.assertOnSendFailure()

        composeTestRule.dismissFailureDialog()

        composeTestRule.assertOnForm()

        assertEquals(1, testSetup.getOnBackCount())
    }

    @Test
    @MediumTest
    fun close_on_send_failure() {
        val testSetup =
            newTestSetup(
                SendStage.SendFailure("Test error message"),
                runBlocking { ZecSendFixture.new() }
            )

        assertEquals(0, testSetup.getOnBackCount())

        composeTestRule.assertOnSendFailure()

        composeTestRule.dismissFailureDialog()

        composeTestRule.assertOnForm()

        assertEquals(1, testSetup.getOnBackCount())
    }

    @Test
    @MediumTest
    fun scanner_button_on_form_hit() {
        val testSetup = newTestSetup()

        assertEquals(0, testSetup.getOnScannerCount())

        composeTestRule.clickScanner()

        assertEquals(1, testSetup.getOnScannerCount())
    }

    // TODO [#1260]: Cover Send.Form screen UI with tests
    // TODO [#1260]: https://github.com/Electric-Coin-Company/zashi-android/issues/1260
    @Test
    @MediumTest
    @Ignore("Currently disabled. Will be implemented as part of #1260")
    fun input_arguments_to_form() {
        newTestSetup(
            sendStage = SendStage.Form,
            zecSend = null
        )

        composeTestRule.assertOnForm()

        // We use that the assertTextEquals searches in SemanticsProperties.EditableText too, although to be able to
        // compare its editable value to an exact match we need to pass all its texts
        composeTestRule.onNodeWithText(getStringResource(R.string.send_addressPlaceholder)).also {
            it.assertTextEquals(
                getStringResource(R.string.send_addressPlaceholder),
                WalletFixture.Alice.getAddresses(ZcashNetwork.Testnet).unified,
                includeEditableText = true
            )
        }
    }

    @Test
    @MediumTest
    fun device_has_camera_feature() {
        newTestSetup(
            sendStage = SendStage.Form,
            hasCameraFeature = true
        )

        composeTestRule.assertOnForm()

        composeTestRule.onNodeWithContentDescription(getStringResource(R.string.send_scan_content_description)).also {
            it.assertExists()
        }
    }

    @Test
    @MediumTest
    fun device_has_not_camera_feature() {
        newTestSetup(
            sendStage = SendStage.Form,
            hasCameraFeature = false
        )

        composeTestRule.assertOnForm()

        composeTestRule.onNodeWithContentDescription(getStringResource(R.string.send_scan_content_description)).also {
            it.assertDoesNotExist()
        }
    }
}
