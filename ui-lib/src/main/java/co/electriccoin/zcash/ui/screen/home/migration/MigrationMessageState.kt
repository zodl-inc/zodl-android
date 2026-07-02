package co.electriccoin.zcash.ui.screen.home.migration

import co.electriccoin.zcash.ui.screen.home.HomeMessageState

enum class MigrationBannerPhase { REQUIRED, IN_PROGRESS, COMPLETE }

class MigrationMessageState(
    val phase: MigrationBannerPhase,
    val progressLabel: String?,
    // Only meaningful for MigrationBannerPhase.IN_PROGRESS — drives the circular progress ring
    // icon (Figma node 2780:4492) instead of a static badge icon.
    val progressPercent: Float? = null,
    val onClick: (() -> Unit)?,
    val onButtonClick: () -> Unit,
) : HomeMessageState
