package co.electriccoin.zcash.ui.screen.balances

import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.fixture.ObserveFiatCurrencyResultFixture

data class BalanceWidgetState(
    val showDust: Boolean,
    val totalBalance: Zatoshi?,
    val button: BalanceButtonState?,
    val exchangeRate: ExchangeRateState?,
    /** Tapping the balance opens the per-pool breakdown; `null` disables the tap. */
    val onBalanceClick: (() -> Unit)? = null,
) {
    companion object {
        val loadingPreview =
            BalanceWidgetState(
                totalBalance = null,
                exchangeRate = null,
                button = null,
                showDust = true
            )

        val emptyPreview =
            BalanceWidgetState(
                totalBalance = Zatoshi(0),
                exchangeRate = ObserveFiatCurrencyResultFixture.new(),
                button = null,
                showDust = true
            )

        val completePreview =
            BalanceWidgetState(
                totalBalance = Zatoshi(1234567891234567L),
                button =
                    BalanceButtonState(
                        icon = R.drawable.ic_help,
                        text = stringRes("text"),
                        amount = Zatoshi(1000),
                        onClick = {}
                    ),
                exchangeRate = ObserveFiatCurrencyResultFixture.new(),
                showDust = true
            )
    }
}
