package co.electriccoin.zcash.ui.common.model.migration

/**
 * How many successive migration-engine RUNS the account's current residual balance is estimated
 * to need, for a Keystone account, per `estimate_migration_runs`/`OrchardMigrationSdk
 * .estimateMigrationRunCount()`. The engine caps each run at a fixed number of notes it will
 * migrate, so a large enough balance needs several distinct full propose→confirm→sign→execute
 * cycles instead of a single AUTOMATIC pass — this is how the Review screen communicates that
 * ("Round X of Y").
 *
 * A stateless, display-only preview computed fresh from the live estimate at Review time — never
 * persisted (the plan cache is gone; see `spec/2026-07-30-plan-cache-elimination-proposal.md`),
 * which is why `current` is always literally `1` ("this round, from here") rather than a running
 * campaign counter.
 */
data class MigrationKeystoneRound(
    val current: Int,
    val total: Int
)
