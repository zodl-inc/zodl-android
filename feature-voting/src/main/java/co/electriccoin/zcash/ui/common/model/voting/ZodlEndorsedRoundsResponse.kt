package co.electriccoin.zcash.ui.common.model.voting

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Base64

@Serializable
data class ZodlEndorsedRoundsResponse(
    @SerialName("vote_round_ids")
    val voteRoundIds: List<String> = emptyList()
) {
    fun roundIdsHex(): Set<String> =
        voteRoundIds
            .mapNotNull { encoded ->
                runCatching { Base64.getDecoder().decode(encoded) }
                    .getOrNull()
                    ?.takeIf { bytes -> bytes.size == ROUND_ID_BYTES }
                    ?.toLowerHex()
            }.toSet()

    /**
     * Not private: kotlinx.serialization puts the generated serializer() on the companion, and a
     * private companion makes `Json.decodeFromString<ZodlEndorsedRoundsResponse>()` from another
     * class fail at runtime with IllegalAccessError (library workaround).
     */
    companion object {
        private const val ROUND_ID_BYTES = 32
    }
}
