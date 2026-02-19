package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

interface TokenNameProvider {
    fun getName(ticker: String): StringResource
}

class TokenNameProviderImpl : TokenNameProvider {
    override fun getName(ticker: String): StringResource =
        when (ticker.lowercase()) {
            "cbbtc", "wbtc", "xbtc", "btc" -> stringRes("Bitcoin")
            "weth", "eth" -> stringRes("Ethereum")
            "near" -> stringRes("Near")
            "sol" -> stringRes("Solana")
            "tron" -> stringRes("Tron")
            "xrp" -> stringRes("XRP")
            "zec" -> stringRes("Zcash")
            "op" -> stringRes("Optimism")
            "pol" -> stringRes("Polygon")
            "SUI" -> stringRes("SUI")
            "\$wif" -> stringRes("dogwifhat")
            else -> stringRes(ticker)
        }
}
