package co.electriccoin.zcash.ui.screen.voting.votingerror

import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import java.util.Locale
import co.electriccoin.zcash.ui.R as UiR

object VotingErrorMapper {
    /**
     * Maps a delegation-phase error to a user-facing message, intercepting the
     * `total_weight must yield at least 1 ballot` failure to surface the user's
     * snapshot weight against the ballot divisor — matches iOS's
     * `coinVote.delegation.insufficientSnapshotBalance` short-circuit in
     * `VotingStore+Delegation.swift`'s `delegationProofFailed` reducer. Falls
     * back to the generic single-arg mapper for all other errors.
     *
     * Pass null for either parameter when the value is unavailable; the generic
     * mapper handles the fallthrough.
     */
    fun toUserFriendlyMessage(
        rawMessage: String,
        eligibleWeightZatoshi: Long?,
        ballotDivisorZatoshi: Long?
    ): StringResource {
        if (eligibleWeightZatoshi != null &&
            ballotDivisorZatoshi != null &&
            rawMessage.lowercase().contains("total_weight must yield at least 1 ballot")
        ) {
            return stringRes(
                UiR.string.vote_error_mapper_insufficient_snapshot_balance,
                eligibleWeightZatoshi.toZecLabel(),
                ballotDivisorZatoshi.toZecLabel()
            )
        }
        return toUserFriendlyMessage(rawMessage)
    }

    fun toUserFriendlyMessage(rawMessage: String): StringResource {
        val lower = rawMessage.lowercase()
        return when {
            lower.contains("no voting server accepted share") ||
                lower.contains("no reachable vote servers") ||
                lower.contains("all configured vote servers failed") -> {
                stringRes(UiR.string.vote_error_mapper_no_reachable_vote_servers)
            }

            lower.contains("nullifier") && lower.contains("spent") -> {
                stringRes(UiR.string.vote_error_mapper_nullifier_spent)
            }

            lower.contains("vote round not found") -> {
                stringRes(UiR.string.vote_error_mapper_round_not_found)
            }

            lower.contains("round") && (lower.contains("not active") || lower.contains("inactive") || lower.contains("closed")) -> {
                stringRes(UiR.string.vote_error_mapper_round_closed)
            }

            lower.contains("no active voting round") -> {
                stringRes(UiR.string.vote_error_mapper_round_closed)
            }

            lower.contains("pir proof root mismatch") ||
                lower.contains("no pir server matches") -> {
                stringRes(UiR.string.vote_error_mapper_pir_snapshot_mismatch)
            }

            lower.contains("pir proof verification failed") -> {
                stringRes(UiR.string.vote_error_mapper_pir_invalid_proof)
            }

            lower.contains("pir server connect failed") ||
                lower.contains("pir parallel fetch failed") -> {
                stringRes(UiR.string.vote_error_mapper_pir_unavailable)
            }

            lower.contains("no pir endpoints are configured") -> {
                stringRes(UiR.string.vote_error_mapper_pir_endpoints_missing)
            }

            lower.contains("commitment tree did not grow") -> {
                stringRes(UiR.string.vote_error_mapper_commitment_tree_not_grown)
            }

            lower.contains("invalid commitment tree anchor height") -> {
                stringRes(UiR.string.vote_error_mapper_invalid_anchor_height)
            }

            lower.contains("invalid zero-knowledge proof") -> {
                stringRes(UiR.string.vote_error_mapper_invalid_proof)
            }

            lower.contains("delegation bundle build failed") ||
                lower.contains("create_proof failed") -> {
                stringRes(UiR.string.vote_error_mapper_proof_generation_failed)
            }

            lower.contains("notreestate") ||
                lower.contains("no tree state") -> {
                stringRes(UiR.string.vote_error_mapper_no_tree_state)
            }

            lower.contains("http 5") -> {
                stringRes(UiR.string.vote_error_mapper_http_5)
            }

            lower.contains("grpcstatus") ||
                lower.contains("rpc timed out") ||
                lower.contains("transport became inactive") -> {
                stringRes(UiR.string.vote_error_mapper_lightwalletd_unavailable)
            }

            lower.contains("pir") || lower.contains("private information retrieval") -> {
                stringRes(UiR.string.vote_error_mapper_pir_connection)
            }

            lower.contains("insufficient") && lower.contains("fund") -> {
                stringRes(UiR.string.vote_error_mapper_insufficient_funds)
            }

            lower.contains("wallet") && lower.contains("sync") -> {
                stringRes(UiR.string.vote_error_mapper_wallet_sync)
            }

            lower.contains("network") || lower.contains("timeout") || lower.contains("connect") -> {
                stringRes(UiR.string.vote_error_mapper_network)
            }

            lower.contains("proof") || lower.contains("zkp") -> {
                stringRes(UiR.string.vote_error_mapper_proof)
            }

            lower.contains("config") || lower.contains("version") -> {
                stringRes(UiR.string.vote_error_mapper_version)
            }

            else -> {
                stringRes(UiR.string.vote_error_mapper_unknown)
            }
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

    // Snapshot balances and the ballot divisor are quantized in 0.125 ZEC
    // increments, so three decimals is exact for both values.
    private fun Long.toZecLabel(): String =
        String.format(Locale.US, "%.3f", this / ZATOSHI_PER_ZEC)

    private const val ZATOSHI_PER_ZEC = 100_000_000.0
}
