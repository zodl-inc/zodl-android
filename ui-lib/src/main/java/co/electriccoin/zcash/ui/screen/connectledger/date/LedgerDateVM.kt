package co.electriccoin.zcash.ui.screen.connectledger.date

import android.app.Application
import androidx.lifecycle.ViewModel
import cash.z.ecc.android.sdk.SdkSynchronizer
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.repository.LedgerPairingRepository
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.fixture.WalletFixture
import co.electriccoin.zcash.ui.screen.common.BirthdayPickerState
import co.electriccoin.zcash.ui.screen.connectledger.estimation.LedgerEstimationArgs
import co.electriccoin.zcash.ui.screen.connectledger.height.LedgerHeightArgs
import co.electriccoin.zcash.ui.screen.heightinfo.HeightInfoArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.time.YearMonth
import java.time.ZoneId
import kotlin.time.toKotlinInstant

/**
 * Picks the month a Ledger account first transacted in, then estimates its birthday height.
 *
 * The pending pairing lives in memory only, so a flow resumed after process death has nothing left
 * to import; the init block returns to the wallet root rather than offering a dead form.
 */
class LedgerDateVM(
    private val ledgerPairingRepository: LedgerPairingRepository,
    private val navigationRouter: NavigationRouter,
    private val application: Application,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val selection = MutableStateFlow(WalletFixture.SAPLING_ACTIVATION_YEAR_MONTH)
    private val estimateLce = mutableLce<Unit>()

    init {
        if (ledgerPairingRepository.get() == null) {
            navigationRouter.backToRoot()
        }
    }

    val state: StateFlow<LceState<BirthdayPickerState>> =
        combine(selection, estimateLce.state) { yearMonth, estimate ->
            createState(yearMonth, estimate.loading)
        }.withLce(estimateLce, errorStateMapper::mapToState)
            .stateIn(this, LceState(content = createState(selection.value, false)))

    private fun createState(yearMonth: YearMonth, isLoading: Boolean) =
        BirthdayPickerState(
            title = null,
            message = stringRes(R.string.firstWalletTransactionSubtitleHWWallet),
            logo = R.drawable.ic_ledger_wordmark,
            selection = yearMonth,
            primaryButton =
                ButtonState(
                    text = stringRes(R.string.general_next),
                    isLoading = isLoading,
                    isEnabled = !isLoading,
                    onClick = { onEstimateClick(yearMonth) },
                ),
            secondaryButton =
                ButtonState(
                    text = stringRes(R.string.ledger_enterManually),
                    onClick = ::onEnterBlockHeightClick,
                ),
            dialogButton =
                IconButtonState(
                    icon = R.drawable.ic_help,
                    onClick = ::onInfoClick,
                ),
            onBack = ::onBack,
            onYearMonthChange = ::onYearMonthChange,
            secondaryButtonTestTag = LedgerDateTag.ENTER_MANUALLY_BTN,
        )

    private fun onEstimateClick(yearMonth: YearMonth) =
        estimateLce.execute {
            val instant =
                yearMonth
                    .atDay(1)
                    .atStartOfDay()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toKotlinInstant()
            val bday =
                SdkSynchronizer.estimateBirthdayHeight(
                    context = application,
                    date = instant,
                    network = VersionInfo.NETWORK,
                )
            navigationRouter.forward(LedgerEstimationArgs(blockHeight = bday.value))
        }

    private fun onEnterBlockHeightClick() = navigationRouter.forward(LedgerHeightArgs)

    private fun onBack() = navigationRouter.back()

    private fun onInfoClick() = navigationRouter.forward(HeightInfoArgs)

    private fun onYearMonthChange(yearMonth: YearMonth) = selection.update { yearMonth }
}
