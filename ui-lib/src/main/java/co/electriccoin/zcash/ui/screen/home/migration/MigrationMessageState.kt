package co.electriccoin.zcash.ui.screen.home.migration

import co.electriccoin.zcash.ui.screen.home.HomeMessageState

// ATTENTION covers both spec §6.2 (Migration Plan Update) and §6.3 (Transfer(s) Expired) — see
// MigrationMessageState.title for how the two get distinct copy despite sharing one phase/icon.
enum class MigrationBannerPhase { REQUIRED, IN_PROGRESS, COMPLETE, READY_TO_SEND, ATTENTION }

class MigrationMessageState(
    val phase: MigrationBannerPhase,
    val progressLabel: String?,
    // Only meaningful for MigrationBannerPhase.IN_PROGRESS — drives the circular progress ring
    // icon (Figma node 2780:4492) instead of a static badge icon.
    val progressPercent: Float? = null,
    // Overrides the phase's default title — used for MigrationBannerPhase.ATTENTION, whose exact
    // copy differs by AttentionReason ("Update migration plan" vs "Transfer 3–5 expired", per spec
    // §6.2/§6.3) despite sharing one phase/icon. Null for every other phase, which keep their fixed
    // per-phase title.
    val title: String? = null,
    val onClick: (() -> Unit)?,
    val onButtonClick: () -> Unit,
) : HomeMessageState
