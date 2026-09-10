package co.electriccoin.zcash.ui.common.model.voting

/**
 * Whether the delegation proof (ZKP1) may be produced in the background while the user is still
 * answering questions.
 *
 * The proof needs only the account's Orchard FVK and the round's hotkey seed, both readable at
 * round-preparation time without authenticating the user, so it can start well before the user
 * reaches the confirmation screen. Compile-time kill switch: flipping it to `false` restores
 * on-demand proving without touching any call site.
 */
internal object VotingBackgroundProofPolicy {
    const val ENABLED = true
}
