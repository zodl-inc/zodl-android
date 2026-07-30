package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.exception.CompactBlockProcessorException
import co.electriccoin.zcash.ui.design.util.getCausesAsSequence

/**
 * A sync failure caused by ZODL and the lightwalletd server disagreeing about the state of the
 * Zcash network.
 *
 * These failures are permanent for as long as the pairing lasts: sync can never make progress, so
 * retrying is not a remedy. The remedies are updating ZODL or switching to a different server.
 *
 * Either side may be the stale one, and none of the values carried here are ordered, so nothing in
 * this type can be used to decide whether it is ZODL or the server that is behind.
 */
sealed interface ServerCompatibilityError {
    /**
     * The name of the underlying SDK exception. Shown to the user as the error type and included in
     * support reports so that a report can be tied back to a specific SDK failure.
     */
    val type: String

    data class ConsensusBranch(
        val clientBranchId: String,
        val serverBranchId: String
    ) : ServerCompatibilityError {
        override val type: String
            get() = CompactBlockProcessorException.MismatchedConsensusBranch::class.simpleName.orEmpty()
    }

    data class Network(
        val clientNetwork: String?,
        val serverNetwork: String?
    ) : ServerCompatibilityError {
        override val type: String
            get() = CompactBlockProcessorException.MismatchedNetwork::class.simpleName.orEmpty()
    }

    data class SaplingActivationHeight(
        val clientHeight: Long,
        val serverHeight: Long
    ) : ServerCompatibilityError {
        override val type: String
            get() = CompactBlockProcessorException.MismatchedSaplingActivationHeight::class.simpleName.orEmpty()
    }
}

/**
 * Returns the server-compatibility failure behind this error, or null if this error has some other
 * cause. The whole cause chain is searched, because the SDK wraps these exceptions before they
 * reach the app.
 */
fun SynchronizerError.toServerCompatibilityError(): ServerCompatibilityError? =
    cause
        ?.getCausesAsSequence()
        .orEmpty()
        .firstNotNullOfOrNull { it.toServerCompatibilityErrorOrNull() }

private fun Throwable.toServerCompatibilityErrorOrNull(): ServerCompatibilityError? =
    when (this) {
        is CompactBlockProcessorException.MismatchedConsensusBranch -> {
            ServerCompatibilityError.ConsensusBranch(
                clientBranchId = clientBranchId,
                serverBranchId = serverBranchId
            )
        }

        is CompactBlockProcessorException.MismatchedNetwork -> {
            ServerCompatibilityError.Network(
                clientNetwork = clientNetwork,
                serverNetwork = serverNetwork
            )
        }

        is CompactBlockProcessorException.MismatchedSaplingActivationHeight -> {
            ServerCompatibilityError.SaplingActivationHeight(
                clientHeight = clientHeight,
                serverHeight = serverHeight
            )
        }

        else -> {
            null
        }
    }
