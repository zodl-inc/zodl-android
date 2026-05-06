package co.electriccoin.zcash.ui.screen.voting.votingerror

import co.electriccoin.zcash.ui.R as UiR
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

object VotingErrorMapper {
    fun toUserFriendlyMessage(rawMessage: String): StringResource {
        val lower = rawMessage.lowercase()
        return when {
            lower.contains("nullifier") && lower.contains("spent") ->
                stringRes(UiR.string.vote_error_mapper_nullifier_spent)

            lower.contains("round") && (lower.contains("not active") || lower.contains("inactive") || lower.contains("closed")) ->
                stringRes(UiR.string.vote_error_mapper_round_closed)

            lower.contains("pir") || lower.contains("private information retrieval") ->
                stringRes(UiR.string.vote_error_mapper_pir_connection)

            lower.contains("insufficient") && lower.contains("fund") ->
                stringRes(UiR.string.vote_error_mapper_insufficient_funds)

            lower.contains("wallet") && lower.contains("sync") ->
                stringRes(UiR.string.vote_error_mapper_wallet_sync)

            lower.contains("network") || lower.contains("timeout") || lower.contains("connect") ->
                stringRes(UiR.string.vote_error_mapper_network)

            lower.contains("proof") || lower.contains("zkp") ->
                stringRes(UiR.string.vote_error_mapper_proof)

            lower.contains("config") || lower.contains("version") ->
                stringRes(UiR.string.vote_error_mapper_version)

            else -> rawMessage
                .takeIf { it.isNotBlank() }
                ?.let(::stringRes)
                ?: stringRes(UiR.string.vote_error_mapper_unknown)
        }
    }

    fun toConfigErrorTitle(rawMessage: String): StringResource =
        if (rawMessage.isWalletUpdateRequired()) {
            stringRes(UiR.string.vote_error_config_title)
        } else {
            stringRes(UiR.string.vote_error_voting_unavailable_title)
        }

    fun toConfigErrorMessage(rawMessage: String): StringResource =
        when {
            rawMessage.isWalletUpdateRequired() -> stringRes(UiR.string.vote_error_mapper_version)
            rawMessage.isNetworkError() -> stringRes(UiR.string.vote_error_mapper_network)
            else -> stringRes(UiR.string.vote_error_voting_unavailable_message)
        }

    private fun String.isWalletUpdateRequired(): Boolean {
        val lower = lowercase()
        return lower.contains("update") ||
            lower.contains("unsupported") ||
            lower.contains("requires a newer") ||
            lower.contains("does not support") ||
            lower.contains("version")
    }

    private fun String.isNetworkError(): Boolean {
        val lower = lowercase()
        return lower.contains("network") ||
            lower.contains("timeout") ||
            lower.contains("connect") ||
            lower.contains("fetch failed") ||
            lower.contains("http")
    }
}
