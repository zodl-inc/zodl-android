package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.model.UnifiedFullViewingKey
import cash.z.ecc.android.sdk.model.UnifiedFullViewingKeyComponents
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.tool.DerivationTool
import co.electriccoin.zcash.ui.common.model.VersionInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decides whether text the user pasted or scanned is a unified full viewing key this wallet can
 * import as a view-only account.
 *
 * Accepted is only a key for the wallet's own network (`uview1` on mainnet, `uviewtest1` on
 * testnet) that the SDK decodes as valid for that network and that carries both an Orchard and a
 * transparent P2PKH component. An incoming viewing key (`uivk`), any other text, a key for the
 * other network and a key missing either component are rejected: the account list combines one
 * address flow per account and waits for every one of them, and the Orchard or transparent address
 * of a key without that component would never arrive, which would stall every account in the
 * wallet.
 *
 * Every whitespace character in the input, line breaks and zero-width spaces included, is dropped
 * first, since a key copied from an email or a PDF is often broken across lines. What remains has
 * to be all lower case or all upper case, as the key encoding requires; a mixed-case key is
 * rejected. An accepted key is returned in its canonical lower-case form.
 *
 * The SDK's reading of the key is the source of truth for its validity; the steps here only
 * normalise the text and pre-filter what cannot be a key for the network before the SDK is asked.
 *
 * The input is never logged or echoed. The SDK answers an invalid key with no components, so an
 * exception from it is a fault, not a verdict on the key: it is reported as
 * [ViewOnlyKeyCheckException], without the SDK's exception or message, never as an invalid key.
 */
internal class ValidateUfvkUseCase(
    private val ufvkDescriber: UfvkDescriber,
) {
    /**
     * @throws ViewOnlyKeyCheckException if the SDK failed to read the key.
     */
    suspend operator fun invoke(
        input: String,
        network: ZcashNetwork = VersionInfo.NETWORK,
    ): UfvkValidation {
        val compact = input.filterNot { it.isWhitespace() || it in INVISIBLE_CHARACTERS }
        val isSingleCase = compact == compact.lowercase() || compact == compact.uppercase()
        val candidate = compact.lowercase()
        val components =
            if (isSingleCase && candidate.startsWith(keyPrefixOf(network))) {
                describe(candidate, network)
            } else {
                null
            }
        val isValid = components != null && components.hasOrchard && components.hasP2pkh
        return if (isValid) UfvkValidation.Valid(candidate) else UfvkValidation.Invalid
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun describe(
        ufvk: String,
        network: ZcashNetwork,
    ): UnifiedFullViewingKeyComponents? =
        try {
            ufvkDescriber.describe(ufvk, network)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ViewOnlyKeyCheckException()
        }

    companion object {
        private const val MAINNET_HRP = "uview"
        private const val TESTNET_HRP = "uviewtest"
        private const val SEPARATOR = "1"

        /**
         * Zero-width characters that [Char.isWhitespace] does not cover but copied text often
         * carries: zero-width space, non-joiner and joiner, word joiner and the byte-order mark.
         */
        private val INVISIBLE_CHARACTERS = setOf('\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF')

        /** How every full viewing key for [network] starts, such as `uview1` on mainnet. */
        fun keyPrefixOf(network: ZcashNetwork) = hrpOf(network) + SEPARATOR

        /** The human-readable part of a full viewing key for [network], such as `uview` on mainnet. */
        private fun hrpOf(network: ZcashNetwork) = if (network == ZcashNetwork.Mainnet) MAINNET_HRP else TESTNET_HRP
    }
}

/**
 * The outcome of [ValidateUfvkUseCase].
 */
internal sealed interface UfvkValidation {
    /**
     * [ufvk] is importable. [toString] leaves the key out.
     */
    class Valid internal constructor(
        val ufvk: String,
    ) : UfvkValidation {
        override fun toString() = "Valid(ufvk=***)"
    }

    data object Invalid : UfvkValidation
}

/**
 * Checking a viewing key failed in the SDK, so whether it is valid is unknown. Deliberately carries
 * neither the SDK's exception as its cause nor its message: both could echo the key back, and the
 * error screen prints and emails the stack trace.
 */
internal class ViewOnlyKeyCheckException : Exception("The viewing key could not be checked.")

/**
 * The SDK's reading of a viewing key: which components it carries, or `null` when it is not a valid
 * key for the network. Decoding the key also checks that the keys inside are valid curve points. An
 * exception means the key could not be read at all, which says nothing about the key. A seam so that
 * the use case can be tested without the native library.
 */
internal interface UfvkDescriber {
    suspend fun describe(
        ufvk: String,
        network: ZcashNetwork,
    ): UnifiedFullViewingKeyComponents?
}

internal class SdkUfvkDescriber : UfvkDescriber {
    /**
     * The SDK returns `null` for an invalid key and throws only on a fault, so an exception is passed
     * on to [ValidateUfvkUseCase], which reports it without its message.
     */
    override suspend fun describe(
        ufvk: String,
        network: ZcashNetwork,
    ): UnifiedFullViewingKeyComponents? =
        withContext(Dispatchers.Default) {
            DerivationTool.getInstance().describeUnifiedFullViewingKey(UnifiedFullViewingKey(ufvk), network)
        }
}
