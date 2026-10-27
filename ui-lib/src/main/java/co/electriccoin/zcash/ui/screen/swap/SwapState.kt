package co.electriccoin.zcash.ui.screen.swap

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.AssetCardState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ChipButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.NumberTextFieldState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.component.listitem.SimpleListItemState
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import co.electriccoin.zcash.ui.screen.swap.ui.SwapAmountTextFieldState
import co.electriccoin.zcash.ui.screen.swap.ui.SwapAmountTextState

internal data class SwapState(
    val swapInfoButton: IconButtonState,
    val amountTextField: SwapAmountTextFieldState,
    val slippage: ButtonState,
    val amountText: SwapAmountTextState,
    val infoItems: List<SimpleListItemState>,
    val addressContact: ChipButtonState? = null,
    val addressBookButton: IconButtonState,
    val addressLocation: AddressLocation,
    val onAddressClick: (() -> Unit)?,
    val address: TextFieldState,
    val addressPlaceholder: StringResource,
    val qrScannerButton: IconButtonState,
    val infoFooter: StringResource?,
    val errorFooter: SwapErrorFooterState?,
    val primaryButton: ButtonState?,
    val onBack: () -> Unit,
    /** Null when the selected account cannot swap from ZEC or has not loaded yet; the divider then runs unbroken. */
    val changeModeButton: IconButtonState?,
) {
    enum class AddressLocation {
        TOP,
        BOTTOM
    }

    companion object {
        /**
         * A view-only account swapping into ZEC: the ZEC side is fixed as the destination, so there
         * is no max button, no change-mode button and the address field sits on top.
         */
        val previewViewOnly =
            SwapState(
                swapInfoButton = IconButtonState(R.drawable.ic_help) {},
                amountTextField =
                    SwapAmountTextFieldState(
                        title = stringRes("From"),
                        error = null,
                        token =
                            AssetCardState.Data(
                                token = stringRes("USDT"),
                                bigIcon = null,
                                smallIcon = null,
                                isEnabled = true,
                                onClick = {}
                            ),
                        textFieldPrefix = null,
                        textField = NumberTextFieldState {},
                        secondaryText = stringResByDynamicCurrencyNumber(100, "$"),
                        max = null,
                        onSwapChange = {}
                    ),
                slippage =
                    ButtonState(
                        stringRes("1%"),
                        trailingIcon = R.drawable.ic_swap_slippage
                    ),
                amountText =
                    SwapAmountTextState(
                        token =
                            AssetCardState.Data(
                                token = stringRes("ZEC"),
                                bigIcon = null,
                                smallIcon = null,
                                isEnabled = false,
                                onClick = {}
                            ),
                        title = stringRes("To"),
                        text = stringResByDynamicCurrencyNumber(2.47123, "ZEC"),
                        secondaryText = stringResByDynamicCurrencyNumber(101, "$"),
                        subtitle = null
                    ),
                infoItems =
                    listOf(
                        SimpleListItemState(
                            title = stringRes("Rate"),
                            text = stringRes("1 ZEC = 51.74 USDC")
                        )
                    ),
                addressBookButton =
                    IconButtonState(
                        icon = R.drawable.send_address_book,
                        onClick = {}
                    ),
                addressLocation = AddressLocation.TOP,
                onAddressClick = {},
                address = TextFieldState(stringRes("")) {},
                addressPlaceholder = stringRes(co.electriccoin.zcash.ui.design.R.string.general_enter_address),
                qrScannerButton =
                    IconButtonState(
                        icon = R.drawable.qr_code_icon,
                        onClick = {}
                    ),
                errorFooter = null,
                primaryButton =
                    ButtonState(
                        stringRes("Get a quote")
                    ),
                infoFooter = null,
                onBack = {},
                changeModeButton = null
            )
    }
}

data class SwapErrorFooterState(
    val title: StringResource,
    val subtitle: StringResource,
)

internal data class SwapCancelState(
    val icon: ImageResource,
    val title: StringResource,
    val subtitle: StringResource,
    val negativeButton: ButtonState,
    val positiveButton: ButtonState,
    override val onBack: () -> Unit,
) : ModalBottomSheetState
