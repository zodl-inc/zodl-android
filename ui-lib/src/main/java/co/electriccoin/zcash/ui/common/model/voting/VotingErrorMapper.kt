package co.electriccoin.zcash.ui.common.model.voting

import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

class VotingErrorMapper {
    @Suppress("CyclomaticComplexMethod")
    fun toUserFriendlyMessage(rawMessage: String): StringResource {
        val lower = rawMessage.lowercase()
        return when {
            lower.contains("nullifier") && lower.contains("spent") -> {
                stringRes(R.string.vote_error_mapper_nullifier_spent)
            }

            lower.contains("round") &&
                (lower.contains("not active") || lower.contains("inactive") || lower.contains("closed")) -> {
                stringRes(R.string.vote_error_mapper_round_closed)
            }

            lower.contains("pir") || lower.contains("private information retrieval") -> {
                stringRes(R.string.vote_error_mapper_pir_connection)
            }

            lower.contains("insufficient") && lower.contains("fund") -> {
                stringRes(R.string.vote_error_mapper_insufficient_funds)
            }

            lower.contains("wallet") && lower.contains("sync") -> {
                stringRes(R.string.vote_error_mapper_wallet_sync)
            }

            lower.contains("network") || lower.contains("timeout") || lower.contains("connect") -> {
                stringRes(R.string.vote_error_mapper_network)
            }

            lower.contains("proof") || lower.contains("zkp") -> {
                stringRes(R.string.vote_error_mapper_proof)
            }

            lower.contains("config") || lower.contains("version") -> {
                stringRes(R.string.vote_error_mapper_version)
            }

            else -> {
                if (rawMessage.isBlank()) {
                    stringRes(R.string.vote_error_mapper_unknown)
                } else {
                    stringRes(rawMessage)
                }
            }
        }
    }
}
