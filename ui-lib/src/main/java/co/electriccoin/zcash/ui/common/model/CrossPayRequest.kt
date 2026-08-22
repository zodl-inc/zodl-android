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

        return current?.takeIf(candidates::contains) ?: candidates.singleOrNull()
    }

    private fun nativeAssets(assets: Collection<SwapAsset>, chain: String) =
        assets.filter {
            it.chainTicker.equals(chain, true) && it.tokenTicker.lowercase() in nativeTokens(chain)
        }

    private fun evmNativeAssets(
        assets: Collection<SwapAsset>,
        reference: AssetReference.EvmNative,
        current: SwapAsset?
    ): List<SwapAsset> {
        val chain = reference.chainId?.let(::evmChain)
        // An explicit but unsupported chain id must be rejected, not silently treated the same
        // as "no chain id at all" -- otherwise a request for an unsupported chain resolves to
        // whatever asset the user already had selected, which the user never asked for.
        if (reference.chainId != null && chain == null) return emptyList()
        // Only fall back to the currently-selected asset's chain when it is actually an EVM
        // chain -- a SOL/BTC/LTC asset selected as `current` must not silently match a native
        // EVM request just because nativeTokens' permissive default matches any chain against
        // itself.
        val resolvedChain = chain ?: evmChainIfKnown(current?.chainTicker)
        return resolvedChain?.let { nativeAssets(assets, it) }.orEmpty()
    }

    private fun contractAssets(
        assets: Collection<SwapAsset>,
        reference: AssetReference.Contract,
        current: SwapAsset?
    ): List<SwapAsset> {
        // reference.chain is an explicit, already-trusted non-EVM chain (e.g. Solana's literal
        // "sol") from the parser -- it doesn't need the same EVM-chain validation as the
        // current-derived fallback used when chainId is absent (every ERC-20 request: EIP-681
        // has no `chain` string, only a numeric chainId).
        val chain = reference.chainId?.let(::evmChain) ?: reference.chain ?: evmChainIfKnown(current?.chainTicker)
        if (reference.chainId != null && chain == null) return emptyList()
        if (chain == null) return emptyList()
        return assets.filter {
            it.chainTicker.equals(chain, true) &&
                it.contractAddress?.equals(reference.address, true) == true
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
                "op" to setOf("eth", "op"),
                "pol" to setOf("matic", "pol"),
                "xlayer" to setOf("okb")
            )
    }
}

object CrossPayRequestParser {
    suspend fun parse(value: String): CrossPayRequest? =
        try {
            PaymentUriParser.new().parse(value).toCrossPayRequest()
        } catch (_: InvalidPaymentUriException) {
            null
        }

    private fun PaymentUriRequest.toCrossPayRequest(): CrossPayRequest? =
        when (this) {
            is PaymentUriRequest.Bitcoin -> {
                request.toCrossPayRequest("btc")
            }

            is PaymentUriRequest.Ethereum -> {
                request.toCrossPayRequest()
            }

            is PaymentUriRequest.Litecoin -> {
                request.toCrossPayRequest("ltc")
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
                )
            }

            is PaymentUriRequest.SolanaTransaction -> {
                null
            }
        }

    private fun UtxoPaymentUriRequest.toCrossPayRequest(chain: String) =
        CrossPayRequest(
            address = address.value,
            amount = amount?.value?.toBigDecimal()?.asDisplayAmount(),
            assetReference = CrossPayRequest.AssetReference.Native(chain)
        )

    private fun Eip681PaymentRequest.toCrossPayRequest(): CrossPayRequest? =
        when (this) {
            is Eip681PaymentRequest.Native -> {
                CrossPayRequest(
                    address = recipientAddress.value,
                    amount = valueHex.toAtomicAmount(),
                    assetReference = CrossPayRequest.AssetReference.EvmNative(chainId)
                )
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
                )
            }

            Eip681PaymentRequest.Unrecognised -> {
                null
            }
        }

    private fun BigDecimal.asDisplayAmount() = CrossPayRequest.Amount(this, isAtomic = false)

    private fun String?.toAtomicAmount(): CrossPayRequest.Amount? =
        this?.let {
            CrossPayRequest.Amount(
                value = BigInteger(removePrefix("0x"), HEX_RADIX).toBigDecimal(),
                isAtomic = true
            )
        }

    private const val HEX_RADIX = 16
}
