package co.electriccoin.zcash.ui.common.model.migration

// Callers should prefer the live `OrchardMigrationSdk.migrationDustThresholdZatoshi()` value —
// this constant exists only as the fallback where no live SDK read is in play (e.g.
// `migrationMessageFor`'s defaulted parameter), so those call sites still have *a* real gate
// instead of a bare `> 0L`/`== MigrationState.Complete` check. Matches the Rust-layer
// `MIGRATION_DUST_THRESHOLD_ZATOSHI` in `migration.rs` as of this writing.
//
// Lives in ui-lib (not feature-migration) so ui-lib-level callers -- e.g.
// WalletViewModel.shouldShowIronwoodAnnouncement -- can share the same gate feature-migration
// uses, instead of each module inventing its own threshold.
const val MIGRATION_DUST_THRESHOLD_ZATOSHI = 10_000L

// The smallest Orchard balance the migration engine will actually migrate. Mirrors librustzcash's
// `RESIDUAL_MIGRATION_MIN` in `zcash_pool_migration/src/denomination.rs` (0.01 ZEC): below this,
// `proposeMigrationTransfers` returns `NothingToMigrate`. A residue in the gap between the dust
// threshold and this minimum is un-migratable via the ordinary migration flow — it must be treated
// as "migration completed" and offered the residue flow (lock / migrate-anyway) instead of a
// "Migrate now" prompt that would fail with `NothingToMigrate`.
const val MIGRATION_RESIDUAL_MIN_ZATOSHI = 1_000_000L
