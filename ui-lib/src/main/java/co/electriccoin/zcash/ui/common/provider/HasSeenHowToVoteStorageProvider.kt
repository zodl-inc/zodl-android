package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey

interface HasSeenHowToVoteStorageProvider : BooleanStorageProvider

class HasSeenHowToVoteStorageProviderImpl(
    override val preferenceHolder: StandardPreferenceProvider,
) : BaseBooleanStorageProvider(key = PreferenceKey("has_seen_how_to_vote")),
    HasSeenHowToVoteStorageProvider
