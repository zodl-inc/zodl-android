package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.PreferenceKey
import java.util.Base64

interface VotingHotkeySeedProvider {
    suspend fun get(accountUuid: String): ByteArray?

    suspend fun store(
        accountUuid: String,
        seed: ByteArray
    )
}

class VotingHotkeySeedProviderImpl(
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider
) : VotingHotkeySeedProvider {
    override suspend fun get(accountUuid: String): ByteArray? =
        encryptedPreferenceProvider()
            .getString(key(accountUuid))
            ?.let { encoded -> Base64.getDecoder().decode(encoded) }

    override suspend fun store(
        accountUuid: String,
        seed: ByteArray
    ) {
        require(seed.size >= MIN_SEED_BYTES) {
            "Voting hotkey seed must be at least $MIN_SEED_BYTES bytes"
        }
        encryptedPreferenceProvider().putString(
            key = key(accountUuid),
            value = Base64.getEncoder().encodeToString(seed)
        )
    }

    private fun key(accountUuid: String) =
        PreferenceKey(
            "voting_hotkey_seed_${accountUuid.lowercase()}"
        )

    private companion object {
        const val MIN_SEED_BYTES = 32
    }
}
