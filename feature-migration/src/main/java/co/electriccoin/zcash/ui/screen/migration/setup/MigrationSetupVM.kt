package co.electriccoin.zcash.ui.screen.migration.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.migration.MigrationGateInfo
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.requireSpendable
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.provider.MigrationUpgradeNoticeStorageProvider
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetMigrationGateInfoUseCase
import co.electriccoin.zcash.ui.common.usecase.GetMigrationPrivacyOrReviewDestinationUseCase
import co.electriccoin.zcash.ui.common.usecase.GetOrchardBalanceUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicCurrencyNumber
import co.electriccoin.zcash.ui.screen.ExternalUrl
import co.electriccoin.zcash.ui.screen.migration.howitworks.MigrationHowItWorksArgs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Duration.Companion.seconds
import co.electriccoin.zcash.ui.design.R as DesignR

class MigrationSetupVM(
    private val getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val getOrchardBalance: GetOrchardBalanceUseCase,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
    private val getMigrationPrivacyOrReviewDestination: GetMigrationPrivacyOrReviewDestinationUseCase,
    private val getMigrationGateInfo: GetMigrationGateInfoUseCase,
    private val migrationUpgradeNotice: MigrationUpgradeNoticeStorageProvider,
) : ViewModel() {
    private val accountLce = mutableLce<Zatoshi>()
    private val selectedMode = MutableStateFlow(MigrationMode.AUTOMATIC)

    /** Null until the SDK has answered. The screen is loading then, so the scheduled option never flashes enabled. */
    private val gate = MutableStateFlow<MigrationSetupGate?>(null)
    private val isKeystoneAccount = getSelectedWalletAccount.observe().map { it is KeystoneAccount }

    init {
        accountLce.execute {
            getSelectedWalletAccount().requireSpendable()
            getOrchardBalance()
        }
        viewModelScope.launch { refreshGateWhileSyncing() }
        viewModelScope.launch { runCatching { migrationUpgradeNotice.clearSelected() } }
    }

    private val content: Flow<MigrationSetupState?> =
        combine(
            accountLce.state,
            selectedMode,
            isKeystoneAccount,
            exchangeRateRepository.state,
            gate,
        ) { lce, mode, isKeystone, rate, currentGate ->
            lce.success?.let { balance ->
                currentGate?.let {
                    createState(balance, effectiveMigrationMode(mode, it), isKeystone, rate, it)
                }
            }
        }

    val state: StateFlow<LceState<MigrationSetupState>> =
        combine(content, accountLce.loading, accountLce.error, gate) { setup, isLoading, error, currentGate ->
            LceState(
                content = setup,
                isLoading = isLoading || currentGate == null,
                error = error?.let(errorStateMapper::mapToState),
            )
        }.stateIn(this)

    /** Asks again while the wallet has no chain tip, so the screen follows the first sync instead of staying stale. */
    private suspend fun refreshGateWhileSyncing() {
        do {
            val info = getMigrationGateInfo()
            gate.value = info.toSetupGate()
            if (info.isWalletTipUnknown) delay(GATE_RETRY_DELAY)
        } while (info.isWalletTipUnknown)
    }

    private fun createState(
        balance: Zatoshi,
        mode: MigrationMode,
        isKeystone: Boolean,
        exchangeRateState: ExchangeRateState,
        currentGate: MigrationSetupGate,
    ) = MigrationSetupState(
        orchardBalance = stringRes(balance),
        fiatBalance = fiatAmount(balance, exchangeRateState),
        isKeystone = isKeystone,
        mode = mode,
        onModeChange = { selectedMode.value = it },
        onFindOutMore = ::onFindOutMore,
        onConfirm = ::onConfirm,
        onBack = ::onBack,
        gate = currentGate,
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

    private fun onFindOutMore() =
        navigationRouter.forward(ExternalUrl("https://support.zodl.com/article/42-moving-your-funds-to-ironwood"))

    private fun onConfirm() {
        val currentGate = gate.value ?: return
        when (effectiveMigrationMode(selectedMode.value, currentGate)) {
            MigrationMode.IMMEDIATE -> {
                viewModelScope.launch {
                    navigationRouter.forward(
                        getMigrationPrivacyOrReviewDestination(mode = MigrationMode.IMMEDIATE)
                    )
                }
            }

            MigrationMode.AUTOMATIC -> {
                navigationRouter.forward(MigrationHowItWorksArgs)
            }
        }
    }

    private fun onBack() = navigationRouter.back()

    private companion object {
        val GATE_RETRY_DELAY = 3.seconds
    }
}

/**
 * The option the screen acts on: the single-transaction one while the scheduled lane is paused for
 * NU7, otherwise what the user picked.
 */
internal fun effectiveMigrationMode(
    selected: MigrationMode,
    gate: MigrationSetupGate,
): MigrationMode =
    when (gate) {
        is MigrationSetupGate.ScheduledPaused -> MigrationMode.IMMEDIATE
        MigrationSetupGate.Open -> selected
    }

/**
 * Maps what the SDK's NU7 gate allows to the setup screen's gate. Only the date is shown: the estimate can be
 * weeks away, so a time of day would claim precision it does not have.
 */
internal fun MigrationGateInfo.toSetupGate(): MigrationSetupGate {
    val date =
        estimatedActivation?.let {
            stringRes(
                DateTimeFormatter
                    .ofLocalizedDate(FormatStyle.MEDIUM)
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochSecond(it.epochSeconds))
            )
        }
    return when {
        isWalletTipUnknown -> {
            MigrationSetupGate.ScheduledPaused(stringRes(DesignR.string.migrationTipUnavailable_title))
        }

        !isScheduledLaneAvailable -> {
            MigrationSetupGate.ScheduledPaused(
                if (date == null) {
                    stringRes(DesignR.string.migrationSetup_automaticPausedSubtitleNoDate)
                } else {
                    stringRes(DesignR.string.migrationSetup_automaticPausedSubtitle, date)
                }
            )
        }

        else -> {
            MigrationSetupGate.Open
        }
    }
}
