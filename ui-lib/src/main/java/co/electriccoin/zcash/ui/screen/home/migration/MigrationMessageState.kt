package co.electriccoin.zcash.ui.screen.home.migration

import co.electriccoin.zcash.ui.screen.home.HomeMessageState

class MigrationMessageState(
    val isInProgress: Boolean,
    val progressLabel: String?,
    val onClick: (() -> Unit)?,
    val onButtonClick: () -> Unit,
) : HomeMessageState
