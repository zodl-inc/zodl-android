package co.electriccoin.zcash.ui.common.model.voting

fun RoundPhase?.canGenerateHotkey(): Boolean =
    this == null || this == RoundPhase.INITIALIZED

fun RoundPhase?.canBuildGovernancePczt(): Boolean =
    this == null || ordinal <= RoundPhase.DELEGATION.ordinal

fun RoundPhase?.hasVoteReady(): Boolean =
    this != null && ordinal >= RoundPhase.VOTE_READY.ordinal

fun Throwable.isRoundPhaseRegression(): Boolean =
    message.orEmpty().contains("refusing to regress round phase", ignoreCase = true)
