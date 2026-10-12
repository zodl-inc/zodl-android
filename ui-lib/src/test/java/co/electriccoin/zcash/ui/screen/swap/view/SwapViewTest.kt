package co.electriccoin.zcash.ui.screen.swap.view

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.AssetCardState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.fixture.ZashiMainTopAppBarStateFixture
import co.electriccoin.zcash.ui.screen.swap.SwapState
import co.electriccoin.zcash.ui.screen.swap.SwapState.AddressLocation.BOTTOM
import co.electriccoin.zcash.ui.screen.swap.SwapState.AddressLocation.TOP
import co.electriccoin.zcash.ui.screen.swap.SwapTag
import co.electriccoin.zcash.ui.screen.swap.SwapView
import co.electriccoin.zcash.ui.screen.swap.ui.SwapAmountTextFieldState
import co.electriccoin.zcash.ui.screen.swap.ui.SwapAmountTextState
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SwapViewTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var addressLocation by mutableStateOf(BOTTOM)

    private fun setContent() {
        composeTestRule.setContent {
            ZcashTheme {
                SwapView(
                    state = swapState(addressLocation),
                    appBarState = ZashiMainTopAppBarStateFixture.new()
                )
            }
        }
    }

    private fun changeModeButtonTop() =
        composeTestRule
            .onNodeWithTag(SwapTag.SWAP_CHANGE_MODE_BUTTON)
            .getUnclippedBoundsInRoot()
            .top

    @Test
    fun change_mode_button_keeps_its_position_when_direction_changes() {
        setContent()
        val fromZecTop = changeModeButtonTop()

        addressLocation = TOP
        composeTestRule.waitForIdle()
        val intoZecTop = changeModeButtonTop()

        addressLocation = BOTTOM
        composeTestRule.waitForIdle()

        assertEquals(fromZecTop, intoZecTop)
        assertEquals(fromZecTop, changeModeButtonTop())
    }

    @Test
    fun reserved_refund_address_space_is_not_exposed() {
        setContent()
        val refundLabel =
            ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getString(R.string.swapToZec_refundAddress)

        listOf(BOTTOM, TOP, BOTTOM).forEach { location ->
            addressLocation = location
            composeTestRule.waitForIdle()

            composeTestRule
                .onAllNodesWithTag(SwapTag.SWAP_ADDRESS_BOOK_BUTTON)
                .assertCountEquals(1)
            composeTestRule
                .onAllNodesWithTag(SwapTag.SWAP_SCAN_BUTTON)
                .assertCountEquals(1)
            composeTestRule
                .onAllNodesWithText(refundLabel)
                .assertCountEquals(if (location == TOP) 1 else 0)
        }
    }

    @Test
    fun amount_field_keeps_focus_when_direction_changes() {
        setContent()
        val amountInput = hasSetTextAction() and hasAnyAncestor(hasTestTag(SwapTag.SWAP_AMOUNT_FIELD))
        composeTestRule.onNode(amountInput).performClick()
        composeTestRule.onNode(amountInput).assertIsFocused()

        addressLocation = TOP
        composeTestRule.waitForIdle()
        composeTestRule.onNode(amountInput).assertIsFocused()

        addressLocation = BOTTOM
        composeTestRule.waitForIdle()
        composeTestRule.onNode(amountInput).assertIsFocused()
    }

    private fun swapState(location: SwapState.AddressLocation) =
        SwapState(
            swapInfoButton = IconButtonState(R.drawable.ic_help) {},
            amountTextField =
                SwapAmountTextFieldState(
                    title = stringRes("From"),
                    error = null,
                    token = assetCard("USDC"),
                    textFieldPrefix = null,
                    textField = NumberTextFieldState {},
                    secondaryText = stringRes("$0.00"),
                    max = null,
                    onSwapChange = {}
                ),
            slippage = ButtonState(stringRes("1%")),
            amountText =
                SwapAmountTextState(
                    token = assetCard("ZEC"),
                    title = stringRes("To"),
                    text = stringRes("0.00"),
                    secondaryText = stringRes("$0.00"),
                    subtitle = null
                ),
            infoItems = emptyList(),
            addressBookButton = IconButtonState(R.drawable.send_address_book) {},
            addressLocation = location,
            onAddressClick = if (location == TOP) ({}) else null,
            address = TextFieldState(stringRes("")) {},
            addressPlaceholder = stringRes("Address"),
            qrScannerButton = IconButtonState(R.drawable.qr_code_icon) {},
            infoFooter = null,
            errorFooter = null,
            primaryButton = ButtonState(stringRes("Get a quote")),
            onBack = {},
            changeModeButton = IconButtonState(R.drawable.ic_swap_change_mode) {}
        )

    private fun assetCard(token: String) =
        AssetCardState.Data(
            token = stringRes(token),
            bigIcon = null,
            smallIcon = null,
            isEnabled = false,
            onClick = {}
        )
}
