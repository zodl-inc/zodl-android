package co.electriccoin.zcash.ui.common.provider

import android.content.Context
import co.electriccoin.zcash.ui.common.model.SwapBlockchain
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes

interface BlockchainProvider {
    fun getBlockchain(ticker: String): SwapBlockchain

    fun getHardcodedBlockchains(): List<SwapBlockchain>

    fun getZcashBlockchain(): SwapBlockchain
}

class BlockchainProviderImpl(
    private val context: Context
) : BlockchainProvider {
    @Suppress("CyclomaticComplexMethod")
    override fun getBlockchain(ticker: String): SwapBlockchain =
        SwapBlockchain(
            chainTicker = ticker,
            chainName =
                when (ticker.lowercase()) {
                    "adi" -> stringRes("Adi")
                    "aptos" -> stringRes("Aptos")
                    "arb" -> stringRes("Arbitrum")
                    "avax" -> stringRes("Avalanche")
                    "base" -> stringRes("Base")
                    "bera" -> stringRes("Bera")
                    "bch" -> stringRes("Bitcoin Cash")
                    "bsc" -> stringRes("Binance Smart Chain")
                    "btc" -> stringRes("Bitcoin")
                    "cardano" -> stringRes("Cardano")
                    "doge" -> stringRes("Doge")
                    "eth" -> stringRes("Ethereum")
                    "gnosis" -> stringRes("Gnosis")
                    "ltc" -> stringRes("Litecoin")
                    "monad" -> stringRes("Monad")
                    "near" -> stringRes("Near")
                    "op" -> stringRes("Optimism")
                    "plasma" -> stringRes("Plasma")
                    "pol" -> stringRes("Polygon")
                    "sol" -> stringRes("Solana")
                    "starknet" -> stringRes("Starknet")
                    "stellar" -> stringRes("Stellar")
                    "sui" -> stringRes("Sui")
                    "ton" -> stringRes("Ton")
                    "tron" -> stringRes("Tron")
                    "xrp" -> stringRes("XRP")
                    "xlayer" -> stringRes("X Layer")
                    "zec" -> stringRes("Zcash")
                    else -> stringRes(ticker.replaceFirstChar { it.uppercase() })
                },
            chainIcon = getChainIcon(ticker)
        )

    private fun getChainIcon(ticker: String): ImageResource {
        val id =
            context.resources.getIdentifier(
                "ic_chain_${ticker.lowercase()}",
                "drawable",
                context.packageName
            )

        return if (id == 0) imageRes(R.drawable.ic_chain_placeholder) else imageRes(id)
    }

    override fun getHardcodedBlockchains(): List<SwapBlockchain> =
        listOf(
            "aptos",
            "arb",
            "avax",
            "base",
            "bera",
            "bsc",
            "btc",
            "cardano",
            "doge",
            "eth",
            "gnosis",
            "near",
            "op",
            "pol",
            "sol",
            "stellar",
            "sui",
            "ton",
            "tron",
            "xrp",
        ).map { getBlockchain(it) }

    override fun getZcashBlockchain(): SwapBlockchain = getBlockchain("zec")
}
