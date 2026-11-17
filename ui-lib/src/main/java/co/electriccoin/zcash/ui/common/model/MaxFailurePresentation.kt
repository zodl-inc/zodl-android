package co.electriccoin.zcash.ui.common.model

import co.electriccoin.zcash.ui.common.datasource.MaxSendAmountFailure

/*
 * What Send, Swap and Pay tell the user around the Max chip (Figma "Send Max: Round 2"): one line above the main
 * button, either a neutral hint or the Max failure, and a sheet when the wallet has too many notes. The line itself
 * is [co.electriccoin.zcash.ui.design.component.MaxFeedback], drawn by `ZashiMaxFeedbackSlot`.
 *
 * Max being unavailable because of the balance (syncing, confirming, transparent-only, no funds) has no line: the
 * chip is disabled and the Spendable pill explains the gap.
 */

/** How a failed Max request is shown. */
enum class MaxFailurePresentation {
    /** One-line error row, cleared by the screen on a Max retap, an amount edit or an asset/address change. */
    INLINE,

    /** Blocking too-many-notes sheet. */
    SHEET
}

val MaxSendAmountFailure.presentation: MaxFailurePresentation
    get() =
        when (this) {
            MaxSendAmountFailure.TOO_MANY_NOTES -> MaxFailurePresentation.SHEET

            MaxSendAmountFailure.INVALID_RECIPIENT,
            MaxSendAmountFailure.SYNC_REQUIRED,
            MaxSendAmountFailure.OTHER -> MaxFailurePresentation.INLINE
        }
