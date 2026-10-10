package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey

/**
 * Whether the user asked, on the Keep Zodl Open screen, for the screen to stay on until the sync that
 * screen announced has finished: a restore, a resync, or the import of a hardware-wallet account.
 *
 * Unlike [IsKeepScreenOnDuringRestoreProvider], which remembers the answer itself, this flag is cleared
 * once that sync completes, so a later routine sync does not keep the screen on.
 */
interface KeepScreenOnSyncSessionProvider : NullableBooleanStorageProvider

class KeepScreenOnSyncSessionProviderImpl(
    override val preferenceHolder: StandardPreferenceProvider,
) : BaseNullableBooleanStorageProvider(
        key = PreferenceKey("keep_screen_on_sync_session"),
    ),
    KeepScreenOnSyncSessionProvider
