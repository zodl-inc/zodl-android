package co.electriccoin.zcash.ui.common.model.voting

fun RoundPhase?.canGenerateHotkey(): Boolean =
    this == null || this == RoundPhase.INITIALIZED

/**
 * Kept only for legacy DBs that persisted a round `phase` of HOTKEY/DELEGATION before 2026-08-10
 * (when `build_governance_pczt_for_bundle` stopped writing those ranks — see its doc comment).
 * New per-bundle "can I (re)build this bundle's PCZT" decisions must use [DelegationPhase] via
 * [co.electriccoin.zcash.ui.common.provider.VotingCryptoClient.delegationPhases], never this:
 * the round-level phase can't tell bundle 0 (already proved) apart from bundle 1 (not yet
 * constructed) in the same round, which is exactly the bug this used to cause.
 */
fun RoundPhase?.hasVoteReady(): Boolean =
    this != null && ordinal >= RoundPhase.VOTE_READY.ordinal

/**
 * True when constructing a bundle's governance PCZT failed because it was already built with
 * different data (the crate's `store_delegation_data` refuses to silently overwrite persisted
 * `pczt_sighash`/`padded_note_secrets`/`tx1_effects` for a bundle). This means the bundle's setup
 * is genuinely present and intact — the caller should treat the existing setup as authoritative,
 * not retry blindly. The sanctioned recovery from a *corrupted* setup (not this — this is the
 * healthy case) is [co.electriccoin.zcash.ui.common.provider.VotingCryptoClient.resetVotingSessionState].
 */
fun Throwable.isDelegationSetupOverwrite(): Boolean =
    message.orEmpty().contains("refusing to overwrite", ignoreCase = true)
