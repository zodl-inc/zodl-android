package co.electriccoin.zcash.ui.common.model

import cash.z.ecc.android.sdk.PaymentUriParser
import cash.z.ecc.android.sdk.model.Eip681PaymentRequest
import cash.z.ecc.android.sdk.model.InvalidPaymentUriException
import cash.z.ecc.android.sdk.model.PaymentUriRequest
import cash.z.ecc.android.sdk.model.UtxoPaymentUriRequest
import java.math.BigDecimal
import java.math.BigInteger

/**
 * A cross-chain payment request resolved from a validated [PaymentUriRequest] (SDK) into a
 * concrete, app-known [SwapAsset]. Covers Bitcoin and Litecoin on-chain transfers, EVM native
 * and ERC-20 transfers across the chains in [EVM_CHAINS], and Solana native/SPL-token transfers.
 * Solana interactive transaction-request links ([PaymentUriRequest.SolanaTransaction]) and
 * unrecognised EIP-681 requests are explicitly out of scope and rejected during parsing -- see
 * [CrossPayRequestParser.parse].
 */
data class CrossPayRequest(
    val address: String,
    val amount: Amount?,
    val assetReference: AssetReference
) {
    data class Amount(
        val value: BigDecimal,
        val isAtomic: Boolean
    )

    sealed interface AssetReference {
        data class Native(
            val chain: String
        ) : AssetReference

        data class EvmNative(
            val chainId: String?
        ) : AssetReference

        data class Contract(
            val chain: String?,
            val chainId: String?,
            val address: String
        ) : AssetReference
    }

    fun resolveAsset(assets: Collection<SwapAsset>, current: SwapAsset?): SwapAsset? {
        val candidates =
            when (val reference = assetReference) {
                is AssetReference.Native -> nativeAssets(assets, reference.chain)
                is AssetReference.EvmNative -> evmNativeAssets(assets, reference, current)
                is AssetReference.Contract -> contractAssets(assets, reference, current)
            }

        // Compared by assetId rather than full equality: SwapAsset implementations are data
        // classes, so a `current` captured from an older curated-assets snapshot (e.g. before a
        // field like contractAddress was populated) would otherwise fail structural equality
        // against the same conceptual asset in a freshly-loaded `candidates` list, breaking the
        // "no explicit chain -> keep current selection" fallback below.
        return current?.takeIf { c -> candidates.any { it.assetId == c.assetId } } ?: candidates.singleOrNull()
    }

    private fun nativeAssets(assets: Collection<SwapAsset>, chain: String) =
        assets.filter { it.matchesChain(chain) && it.tokenTicker.lowercase() in nativeTokens(chain) }

    /**
     * Resolves the chain a request should be evaluated against, or null to reject the request
     * outright (no candidates).
     *
     * - If [chainId] is present, it must resolve via [EVM_CHAINS] -- an explicit but unsupported
     *   chain id must be rejected, not silently treated the same as "no chain id at all" (which
     *   falls through to the [current]-derived fallback below); otherwise a request for an
     *   unsupported chain would resolve to whatever asset the user already had selected, which
     *   the user never asked for. Note this never falls through to [explicitChain]/[current] once
     *   [chainId] is present, even if it fails to resolve.
     * - Otherwise, [explicitChain] is used if present -- an explicit, already-trusted non-EVM
     *   chain from the parser (e.g. Solana's literal "sol"), which doesn't need EVM validation.
     * - Otherwise, falls back to [current]'s chain, but only when it is itself a recognized EVM
     *   chain -- a SOL/BTC/LTC asset selected as `current` must not silently match an EVM
     *   request just because `nativeTokens`'s permissive default matches any chain against
     *   itself.
     */
    private fun resolveTargetChain(
        chainId: String?,
        explicitChain: String?,
        current: SwapAsset?
    ): String? =
        if (chainId != null) {
            evmChain(chainId)
        } else {
            explicitChain ?: evmChainIfKnown(current?.chainTicker)
        }

    private fun evmNativeAssets(
        assets: Collection<SwapAsset>,
        reference: AssetReference.EvmNative,
        current: SwapAsset?
    ): List<SwapAsset> {
        val chain = resolveTargetChain(reference.chainId, explicitChain = null, current) ?: return emptyList()
        return nativeAssets(assets, chain)
    }

    private fun contractAssets(
        assets: Collection<SwapAsset>,
        reference: AssetReference.Contract,
        current: SwapAsset?
    ): List<SwapAsset> {
        val chain = resolveTargetChain(reference.chainId, reference.chain, current) ?: return emptyList()
        return assets.filter {
            it.matchesChain(chain) &&
                it.contractAddress?.let { address -> addressesMatch(address, reference.address, chain) } == true
        }
    }

    fun resolvedAmount(asset: SwapAsset?): BigDecimal? =
        amount?.let {
            if (it.isAtomic) {
                asset?.let { resolvedAsset -> it.value.movePointLeft(resolvedAsset.decimals) }
            } else {
                it.value
            }
        }

    private fun evmChain(chainId: String): String? = EVM_CHAINS[chainId]

    /**
     * Returns [chain] unchanged if it is a recognized EVM chain ticker, else null. Used to guard
     * the currently-selected-asset fallback in [resolveAsset]: [current] may be on any chain
     * (SOL, BTC, LTC, ...), and only an EVM one is a valid disambiguation for an EVM request
     * with no explicit chain id.
     */
    private fun evmChainIfKnown(chain: String?): String? =
        chain?.lowercase()?.takeIf { it in EVM_CHAINS.values }

    private fun nativeTokens(chain: String): Set<String> =
        NATIVE_TOKENS[chain.lowercase()] ?: setOf(chain.lowercase())

    private companion object {
        // Known duplication (MOB-1751 review): this table and NATIVE_TOKENS are hand-duplicated
        // in the iOS app's CrossPayRequest.swift. See
        // https://github.com/zodl-inc/zodl-android/pull/2457 and
        // https://github.com/zodl-inc/zodl-ios/pull/2002 for the tracked follow-up to collapse
        // this into one shared source.
        val EVM_CHAINS =
            mapOf(
                "1" to "eth",
                "10" to "op",
                "56" to "bsc",
                "137" to "pol",
                "196" to "xlayer",
                "8453" to "base",
                "42161" to "arb",
                "43114" to "avax"
            )
        val NATIVE_TOKENS =
            mapOf(
                "arb" to setOf("eth"),
                "base" to setOf("eth"),
                "bsc" to setOf("bnb"),
                // Optimism's own governance token (OP) is deliberately excluded here: a native
                // EIP-681 transfer (no function call, just a value) always means the chain's gas
                // token, which is ETH on Optimism, never OP. Including "op" let a native transfer
                // match either asset when both were curated, making resolution ambiguous
                // (candidates.count() > 1) for a request that's actually unambiguous.
                "op" to setOf("eth"),
                "pol" to setOf("matic", "pol"),
                "xlayer" to setOf("okb")
            )
    }
}

// File-scoped rather than members of CrossPayRequest: neither reads any CrossPayRequest instance
// state, and keeping them out of the class avoids pushing its member count over detekt's
// TooManyFunctions threshold.
private fun SwapAsset.matchesChain(chain: String): Boolean = chainTicker.equals(chain, true)

/**
 * Compares two addresses for the given [chain]'s address format: case-insensitively for EVM hex
 * addresses (a checksum's capitalization doesn't change the address), case-sensitively for
 * everything else -- notably Solana's base58 mint/public-key addresses, where case is semantically
 * significant and two strings differing only in case are different addresses.
 */
private fun addressesMatch(
    a: String,
    b: String,
    chain: String
): Boolean = if (chain == "sol") a == b else a.equals(b, ignoreCase = true)

/**
 * Distinguishes a string that simply isn't a recognized payment-request URI at all (treated
 * elsewhere as a literal address, e.g. a plain ZEC/BTC address with no scheme) from one that a
 * recognized scheme deliberately puts out of scope -- a Solana Pay interactive transaction-request
 * link, or an EIP-681 request using a method this app doesn't support. The latter must not be
 * silently accepted as a literal recipient address either: it's a URI, and one this parser
 * explicitly declined to honour, not something to attempt sending funds to as-is.
 */
sealed interface CrossPayParseResult {
    data class Request(
        val request: CrossPayRequest
    ) : CrossPayParseResult

    data object NotAPaymentRequest : CrossPayParseResult

    data object Rejected : CrossPayParseResult
}

object CrossPayRequestParser {
    suspend fun parse(value: String): CrossPayParseResult =
        try {
            PaymentUriParser.new().parse(value).toCrossPayParseResult()
        } catch (_: InvalidPaymentUriException) {
            CrossPayParseResult.NotAPaymentRequest
        }

    private fun PaymentUriRequest.toCrossPayParseResult(): CrossPayParseResult =
        when (this) {
            is PaymentUriRequest.Bitcoin -> {
                request.toCrossPayRequest("btc").toResult()
            }

            is PaymentUriRequest.Ethereum -> {
                request.toCrossPayParseResult()
            }

            is PaymentUriRequest.Litecoin -> {
                request.toCrossPayRequest("ltc").toResult()
            }

            is PaymentUriRequest.SolanaTransfer -> {
                val assetReference =
                    request.splToken?.let {
                        CrossPayRequest.AssetReference.Contract(
                            chain = "sol",
                            chainId = null,
                            address = it.value
                        )
                    } ?: CrossPayRequest.AssetReference.Native("sol")

                CrossPayRequest(
                    address = request.recipient.value,
                    amount =
                        request.amount
                            ?.value
                            ?.toBigDecimal()
                            ?.asDisplayAmount(),
                    assetReference = assetReference
                ).toResult()
            }

            is PaymentUriRequest.SolanaTransaction -> {
                CrossPayParseResult.Rejected
            }
        }

    private fun CrossPayRequest.toResult(): CrossPayParseResult = CrossPayParseResult.Request(this)

    private fun UtxoPaymentUriRequest.toCrossPayRequest(chain: String) =
        CrossPayRequest(
            address = address.value,
            amount = amount?.value?.toBigDecimal()?.asDisplayAmount(),
            assetReference = CrossPayRequest.AssetReference.Native(chain)
        )

    private fun Eip681PaymentRequest.toCrossPayParseResult(): CrossPayParseResult =
        when (this) {
            is Eip681PaymentRequest.Native -> {
                CrossPayRequest(
                    address = recipientAddress.value,
                    amount = valueHex.toAtomicAmount(),
                    assetReference = CrossPayRequest.AssetReference.EvmNative(chainId)
                ).toResult()
            }

            is Eip681PaymentRequest.Erc20 -> {
                CrossPayRequest(
                    address = recipientAddress.value,
                    amount = valueHex.toAtomicAmount(),
                    assetReference =
                        CrossPayRequest.AssetReference.Contract(
                            chain = null,
                            chainId = chainId,
                            address = tokenContractAddress.value
                        )
                ).toResult()
            }

            Eip681PaymentRequest.Unrecognised -> {
                CrossPayParseResult.Rejected
            }
        }

    private fun BigDecimal.asDisplayAmount() = CrossPayRequest.Amount(this, isAtomic = false)

    private fun String?.toAtomicAmount(): CrossPayRequest.Amount? =
        this?.let {
            // The SDK should always emit a well-formed "0x"-prefixed hex value here, but this
            // parses untrusted-input-derived data one layer removed from where that's actually
            // validated (a future SDK schema change, or a bug in the decode helpers there, would
            // otherwise surface as an uncaught NumberFormatException past this function's only
            // caller's narrow catch, silently stranding the caller with no state transition).
            // Treat a malformed value as "no amount" rather than propagating the exception.
            runCatching {
                CrossPayRequest.Amount(
                    value = BigInteger(removePrefix("0x"), HEX_RADIX).toBigDecimal(),
                    isAtomic = true
                )
            }.getOrNull()
        }

    private const val HEX_RADIX = 16
}
