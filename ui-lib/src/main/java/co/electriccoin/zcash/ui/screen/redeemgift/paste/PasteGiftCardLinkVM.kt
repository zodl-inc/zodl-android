package co.electriccoin.zcash.ui.screen.redeemgift.paste

import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.usecase.ClearClipboardUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToRedeemGiftCardUseCase
import co.electriccoin.zcash.ui.common.usecase.ReadClipboardTextUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Lets the user paste or type a gift card link and opens the redeem screen for it. The link stays in this view model's
 * memory only: it never goes into a route or the saved instance state, and it is never logged.
 */
class PasteGiftCardLinkVM(
    private val navigateToRedeemGiftCard: NavigateToRedeemGiftCardUseCase,
    private val readClipboardText: ReadClipboardTextUseCase,
    private val clearClipboard: ClearClipboardUseCase,
    private val navigationRouter: NavigationRouter,
) : ViewModel() {
    /** The field's text as entered. */
    private val text = MutableStateFlow("")

    /** Whether anything was taken from the clipboard on this screen. */
    private var hasPasted = false

    private var hasContinued = false

    val state: StateFlow<PasteGiftCardLinkState> =
        text.map(::createState).stateIn(viewModel = this, initialValue = createState(text.value))

    /**
     * The gift card link in [text], trimmed, or `null` while [text] is not one.
     */
    private fun linkIn(text: String): String? = text.trim().takeIf(navigateToRedeemGiftCard::isGiftCardLink)

    private fun createState(text: String): PasteGiftCardLinkState {
        val isValid = linkIn(text) != null
        return PasteGiftCardLinkState.create(
            text = text,
            isValid = isValid,
            isInvalid = !isValid && !navigateToRedeemGiftCard.isGiftCardLinkStart(text),
            onValueChange = ::onValueChange,
            onPasteClick = ::onPasteClick,
            onClearClick = ::onClearClick,
            onContinueClick = ::onContinueClick,
            onBack = ::onBack
        )
    }

    private fun onValueChange(text: String) = this.text.update { text }

    private fun onPasteClick() {
        val pasted = readClipboardText()?.trim() ?: return
        hasPasted = true
        text.update { pasted }
    }

    private fun onClearClick() = text.update { "" }

    /**
     * Opens the redeem screen in place of this one, and clears the clipboard when anything was pasted from it on this
     * screen, so that the secret does not stay there even when the pasted text was edited afterwards. The field's text
     * is cleared too, so that the link does not linger in this view model's memory.
     */
    private fun onContinueClick() {
        if (hasContinued) return
        val link = linkIn(text.value) ?: return
        hasContinued = true
        navigateToRedeemGiftCard.replaceGiftCardScanWithRedeem(link)
        clearPasted()
        text.update { "" }
    }

    /**
     * Leaves without redeeming. The clipboard is cleared when anything was pasted from it on this screen, as on
     * Continue, so that backing out does not leave the secret there either.
     */
    private fun onBack() {
        if (!hasContinued) clearPasted()
        navigationRouter.back()
    }

    /** Clears the clipboard once, if anything was pasted from it on this screen. */
    private fun clearPasted() {
        if (!hasPasted) return
        hasPasted = false
        clearClipboard()
    }
}
