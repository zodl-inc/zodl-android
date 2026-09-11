package co.electriccoin.zcash.ui.common.provider

import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

interface TokenNameProvider {
    fun getName(ticker: String): StringResource
}

class TokenNameProviderImpl : TokenNameProvider {
    // A flat ticker -> display-name lookup: the branch count is the point, not hidden complexity.
    // Mirrors BlockchainProviderImpl.getBlockchain, which suppresses this for the same reason.
    //
    // "gram" is the TON chain's native coin, renamed from Toncoin to Gram; it is mapped here only to
    // get title case, since the ticker fallback would render it as "GRAM".
    @Suppress("CyclomaticComplexMethod")
    override fun getName(ticker: String): StringResource =
        when (ticker.lowercase()) {
            "cbbtc", "wbtc", "xbtc", "btc" -> stringRes("Bitcoin")
            "weth", "eth" -> stringRes("Ethereum")
            "ada" -> stringRes("Cardano")
            "aleo" -> stringRes("Aleo")
            "doge" -> stringRes("Dogecoin")
            "eure" -> stringRes("Monerium EUR")
            "gno" -> stringRes("Gnosis")
            "gram" -> stringRes("Gram")
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
