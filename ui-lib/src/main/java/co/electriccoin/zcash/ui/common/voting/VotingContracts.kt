package co.electriccoin.zcash.ui.common.voting

import androidx.navigation.NavGraphBuilder

/*
 * Seam between ui-lib (the app "core") and the feature-voting module. ui-lib never imports
 * feature-voting classes — it talks exclusively to these contracts, and the app module wires the
 * implementations in via Koin (`featureVotingModule`). Unlike migration, voting screens were
 * never behind a contract layer to begin with, so this is the first time this boundary exists —
 * shaped the same way MigrationContracts.kt shapes the migration seam (small, single-purpose
 * interfaces, one per call site) rather than one catch-all interface.
 */

/**
 * `HomeVM`'s two voting lifecycle hooks, run once per `uiLifecyclePipeline` collection alongside
 * (and ordered before) the restore-success dialog check — see `HomeVM.uiLifecyclePipeline`.
 * Both no-op when the coinholder-voting kill switch (`VOTING_ENABLED`, now living in
 * feature-voting) is off.
 */
interface VotingHomeHooks {
    /**
     * Recovers a vote submission interrupted mid-flight (Keystone signing, or the process was
     * killed between building the draft and finishing submission) by restoring the
     * review/confirm/sign (and scan, if that stage was reached) nav stack via
     * `NavigationRouter.replaceAll`. Returns true when a route was recovered and navigation was
     * replaced — `HomeVM` uses this to suppress the restore-success dialog for that one pass, so
     * a recovered voting route always wins over it.
     */
    suspend fun recoverPendingRouteIfNeeded(): Boolean

    /**
     * Re-enqueues share-tracking workers for any rounds the wallet finished submitting in a
     * prior launch that never got their worker scheduled (process death between
     * `markVoteSubmitted` and the scheduler call in `SubmitVotesUseCase`). Scoped to the
     * currently selected account.
     */
    suspend fun resumePendingShareTracking()
}

/** The settings-menu voting entry point (`MoreVM`'s "Coinholder Polling" list item). */
interface VotingSettingsEntry {
    /** Mirrors feature-voting's `VOTING_ENABLED` kill switch — gates the settings entry item. */
    val isEnabled: Boolean

    /** Routes to the how-to-vote explainer (first run for this wallet/Keystone combination). */
    fun navigateToHowToVote()

    /** Routes straight to coinholder polling (the explainer has already been seen). */
    fun navigateToCoinholderPolling()
}

/** Installs the voting destinations into the wallet nav graph. */
interface VotingNavContributor {
    fun contribute(navGraphBuilder: NavGraphBuilder)
}
