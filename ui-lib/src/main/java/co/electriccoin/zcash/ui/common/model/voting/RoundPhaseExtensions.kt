package co.electriccoin.zcash.ui.common.model.voting

fun RoundPhase?.canGenerateHotkey(): Boolean =
    this == null || this == RoundPhase.INITIALIZED

fun RoundPhase?.canBuildGovernancePczt(): Boolean =
    this == null || ordinal <= RoundPhase.DELEGATION.ordinal
