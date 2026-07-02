package co.electriccoin.zcash.ui.screen.migration.setup

import androidx.lifecycle.ViewModel
import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.repository.MockOrchardBalanceRepository
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import co.electriccoin.zcash.ui.screen.ExternalUrl
import co.electriccoin.zcash.ui.screen.migration.notesplit.MigrationNoteSplitArgs
import co.electriccoin.zcash.ui.screen.migration.privacy.MigrationPrivacyArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.MathContext

class MigrationSetupVM(
    private val mockBalanceRepository: MockOrchardBalanceRepository,
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {

    private val accountLce = mutableLce<Zatoshi>()
    private val selectedMode = MutableStateFlow(MigrationMode.AUTOMATIC)
    private val isKeystoneAccount = getSelectedWalletAccount.observe().map { it is KeystoneAccount }

    init {
        accountLce.execute {
            Zatoshi(mockBalanceRepository.get())
        }
    }

    val state: StateFlow<LceState<MigrationSetupState>> =
        combine(
            accountLce.state, selectedMode, isKeystoneAccount, exchangeRateRepository.state
        ) { lce, mode, isKeystone, rate ->
            lce.success?.let { balance -> createState(balance, mode, isKeystone, rate) }
        }.withLce(accountLce, errorStateMapper::mapToState)
            .stateIn(this)

    private fun createState(
        balance: Zatoshi,
        mode: MigrationMode,
        isKeystone: Boolean,
        exchangeRateState: ExchangeRateState,
    ) = MigrationSetupState(
        orchardBalance = stringRes(balance),
        fiatBalance = fiatAmount(balance, exchangeRateState),
        isKeystone = isKeystone,
        mode = mode,
        onModeChange = { selectedMode.value = it },
        onFindOutMore = ::onFindOutMore,
        onConfirm = ::onConfirm,
        onBack = ::onBack,
    )

    private fun fiatAmount(zatoshi: Zatoshi, exchangeRateState: ExchangeRateState): StringResource? {
        val data = exchangeRateState as? ExchangeRateState.Data ?: return null
        val conversion = data.currencyConversion ?: return null
        return stringResByDynamicCurrencyNumber(
            amount =
                zatoshi
                    .convertZatoshiToZec()
                    .multiply(BigDecimal(conversion.priceOfZec), MathContext.DECIMAL128),
            ticker = data.expectedCurrency.symbol,
        )
    }

    // TODO: placeholder URL until the real "Find out more" migration explainer page exists.
    private fun onFindOutMore() = navigationRouter.forward(ExternalUrl("https://zodl.com/orchard-migration"))

    private fun onConfirm() = when (selectedMode.value) {
        MigrationMode.IMMEDIATE -> navigationRouter.forward(MigrationPrivacyArgs(MigrationMode.IMMEDIATE))
        MigrationMode.AUTOMATIC -> navigationRouter.forward(MigrationNoteSplitArgs)
    }

    private fun onBack() = navigationRouter.back()
}
