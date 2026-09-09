package co.electriccoin.zcash.ui.screen.swap.quote

import cash.z.ecc.android.sdk.ext.convertZecToZatoshi
import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SwapMode.EXACT_INPUT
import co.electriccoin.zcash.ui.common.model.SwapMode.EXACT_OUTPUT
import co.electriccoin.zcash.ui.common.model.SwapMode.FLEX_INPUT
import co.electriccoin.zcash.ui.common.model.SwapProvider
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.isZCashAsset
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.SwapTokenAmountState
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByAddress
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import co.electriccoin.zcash.ui.design.util.stringResByDynamicNumber
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import co.electriccoin.zcash.ui.design.util.withStyle
import co.electriccoin.zcash.ui.screen.transactiondetail.swapQuoteChainIcon
import co.electriccoin.zcash.ui.screen.transactiondetail.swapQuoteTokenIcon
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

internal class SwapQuoteVMMapper {
    @Suppress("UseCheckOrError")
    fun createState(
        state: SwapQuoteInternalState,
        successQuotes: List<SwapQuote>,
        onBack: () -> Unit,
        onSubmitQuoteClick: () -> Unit,
        onNavigateToOnRampSwap: () -> Unit,
        onSelectProvider: (SwapProvider) -> Unit,
    ): SwapQuoteState.Success =
        with(state) {
            return SwapQuoteState.Success(
                title =
                    when {
                        quote.destinationAsset.isZCashAsset -> stringRes(R.string.swapToZec_review)
                        quote.mode in listOf(EXACT_INPUT, FLEX_INPUT) -> stringRes(R.string.swapAndPay_swapNow)
                        quote.mode == EXACT_OUTPUT -> stringRes(R.string.swapAndPay_payNow)
                        else -> throw IllegalStateException("Unknown swap mode")
                    },
                providerIcon = quote.provider.providerIcon(),
                rotateIcon = quote.mode == EXACT_OUTPUT,
                from = createFromState(),
                to = createToState(),
                items = createItems(),
                amount = createTotalAmountState(),
                comparison = createComparison(successQuotes, quote.provider, onSelectProvider),
                onBack = onBack,
                infoText = createInfoText(),
                primaryButton =
                    ButtonState(
                        text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_confirm),
                        onClick = {
                            if (quote.destinationAsset.isZCashAsset) {
                                onNavigateToOnRampSwap()
                            } else {
                                onSubmitQuoteClick()
                            }
                        }
                    )
            )
        }

    @Suppress("MagicNumber")
    private fun SwapQuoteInternalState.createInfoText(): StringResource? {
        if (quote.mode == EXACT_OUTPUT) return null
        val slippageUsd = quote.amountOutUsd.multiply(quote.slippage.divide(BigDecimal(100)))
        return stringRes(
            R.string.swapAndPay_swapQuoteSlippageWarn,
            stringResByDynamicCurrencyNumber(slippageUsd, FiatCurrency.USD.symbol),
            stringResByNumber(quote.slippage, minDecimals = 0) + stringRes("%")
        )
    }

    @Suppress("MagicNumber")
    private fun SwapQuoteInternalState.createItems(): List<SwapQuoteInfoItem> =
        listOfNotNull(
            SwapQuoteInfoItem(
                description =
                    when (quote.mode) {
                        EXACT_INPUT, FLEX_INPUT -> stringRes(R.string.swapAndPay_swapFrom)
                        EXACT_OUTPUT -> stringRes(R.string.swapAndPay_payFrom)
                    },
                title = stringRes(R.string.swapAndPay_quote_zashi).withStyle(),
                subtitle = null
            ).takeIf { !quote.destinationAsset.isZCashAsset },
            SwapQuoteInfoItem(
                description =
                    when (quote.mode) {
                        EXACT_INPUT, FLEX_INPUT -> stringRes(R.string.swapAndPay_swapTo)
                        EXACT_OUTPUT -> stringRes(R.string.swapAndPay_payTo)
                    },
                title = stringResByAddress(quote.destinationAddress.address),
                subtitle = null
            ).takeIf { !quote.destinationAsset.isZCashAsset },
            SwapQuoteInfoItem(
                description = stringRes(R.string.swapAndPay_totalFees),
                title =
                    if (quote.destinationAsset.isZCashAsset) {
                        stringResByDynamicCurrencyNumber(totalFees, quote.originAsset.tokenTicker)
                    } else {
                        stringRes(totalFeesZatoshi)
                    }.withStyle(),
                subtitle =
                    stringResByDynamicCurrencyNumber(totalFeesUsd, FiatCurrency.USD.symbol)
                        .takeIf {
                            quote.mode == EXACT_OUTPUT
                        }?.withStyle()
            ),
            if (quote.mode == EXACT_OUTPUT) {
                val slippage = quote.slippage.divide(BigDecimal("100"))
                val slippageUsd = quote.amountOutUsd.multiply(slippage)
                val slippageZatoshi =
                    quote.amountInFormatted
                        .multiply(
                            slippage,
                            MathContext.DECIMAL128
                        ).convertZecToZatoshi()

                SwapQuoteInfoItem(
                    description =
                        stringRes(
                            R.string.swapAndPay_maxSlippage,
                            stringResByNumber(quote.slippage, minDecimals = 0) + stringRes("%")
                        ),
                    title = stringRes(slippageZatoshi).withStyle(),
                    subtitle =
                        stringResByDynamicCurrencyNumber(slippageUsd, FiatCurrency.USD.symbol).withStyle()
                )
            } else {
                null
            }
        )

    private fun SwapQuoteInternalState.createTotalAmountState(): SwapQuoteInfoItem =
        SwapQuoteInfoItem(
            description = stringRes(R.string.swapAndPay_totalAmount),
            title = stringResByDynamicCurrencyNumber(total, quote.originAsset.tokenTicker).withStyle(),
            subtitle = stringResByDynamicCurrencyNumber(totalUsd, FiatCurrency.USD.symbol).withStyle()
        )

    private fun SwapQuoteInternalState.createFromState(): SwapTokenAmountState =
        SwapTokenAmountState(
            bigIcon = quote.originAsset.swapQuoteTokenIcon(),
            smallIcon = quote.originAsset.swapQuoteChainIcon(isShielded = true),
            amount = stringResByDynamicNumber(quote.amountInFormatted),
            fiatAmount = stringResByDynamicCurrencyNumber(quote.amountInUsd, FiatCurrency.USD.symbol),
            token = stringRes(quote.originAsset.tokenTicker),
            chain = quote.originAsset.chainName
        )

    private fun SwapQuoteInternalState.createToState(): SwapTokenAmountState =
        SwapTokenAmountState(
            bigIcon = quote.destinationAsset.swapQuoteTokenIcon(),
            smallIcon = quote.destinationAsset.swapQuoteChainIcon(isShielded = true),
            amount =
                stringResByDynamicNumber(
                    quote.amountOutFormatted.setScale(quote.destinationAsset.decimals, RoundingMode.DOWN),
                ),
            fiatAmount = stringResByDynamicCurrencyNumber(quote.amountOutUsd, FiatCurrency.USD.symbol),
            token = stringRes(quote.destinationAsset.tokenTicker),
            chain = quote.destinationAsset.chainName
        )

    /** Comparison rows, one per provider that returned a quote. Null (or a single quote) → no tabs. */
    private fun createComparison(
        quotes: List<SwapQuote>,
        selectedProvider: SwapProvider,
        onSelectProvider: (SwapProvider) -> Unit
    ): List<SwapProviderQuoteState>? {
        if (quotes.size < 2) return null
        return quotes.map { quote ->
            SwapProviderQuoteState(
                provider = quote.provider,
                icon = quote.provider.providerIcon(),
                name = quote.provider.displayName(),
                amount =
                    stringResByDynamicCurrencyNumber(
                        quote.amountOutFormatted.setScale(quote.destinationAsset.decimals, RoundingMode.DOWN),
                        quote.destinationAsset.tokenTicker
                    ),
                fiatAmount = stringResByDynamicCurrencyNumber(quote.amountOutUsd, FiatCurrency.USD.symbol),
                isSelected = quote.provider == selectedProvider,
                onClick = { onSelectProvider(quote.provider) }
            )
        }
    }

    private fun SwapProvider.providerIcon(): ImageResource =
        when (this) {
            SwapProvider.NEAR -> imageRes(R.drawable.ic_provider_near)
            SwapProvider.MAYA -> imageRes(R.drawable.ic_provider_maya)
        }

    private fun SwapProvider.displayName(): StringResource =
        when (this) {
            SwapProvider.NEAR -> stringRes(R.string.swapAndPay_providerNear)
            SwapProvider.MAYA -> stringRes(R.string.swapAndPay_providerMaya)
        }
}
