package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey

/**
 * Tracks whether the one-time "Migration complete" home banner has already been shown, so it
 * doesn't reappear on every app open once acknowledged. Backed by regular (non-encrypted) app
 * storage, so it's wiped on uninstall along with everything else — a fresh install never shows
 * a stale completion banner it never actually earned.
 */
interface HasSeenMigrationCompleteStorageProvider : BooleanStorageProvider

class HasSeenMigrationCompleteStorageProviderImpl(
    override val preferenceHolder: StandardPreferenceProvider,
) : BaseBooleanStorageProvider(key = PreferenceKey("has_seen_migration_complete")),
    HasSeenMigrationCompleteStorageProvider
