package co.electriccoin.zcash.ui.screen.restore.date

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
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.fixture.WalletFixture
import co.electriccoin.zcash.ui.screen.common.BirthdayPickerState
import co.electriccoin.zcash.ui.screen.restore.estimation.RestoreEstimationArgs
import co.electriccoin.zcash.ui.screen.restore.info.SeedInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.time.YearMonth
import java.time.ZoneId
import kotlin.time.toKotlinInstant

class RestoreDateVM(
    private val args: RestoreDateArgs,
    private val navigationRouter: NavigationRouter,
    private val application: Application,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    @Suppress("MagicNumber")
    private val selection = MutableStateFlow(WalletFixture.SAPLING_ACTIVATION_YEAR_MONTH)
    private val estimateLce = mutableLce<Unit>()

    val state: StateFlow<LceState<BirthdayPickerState>> =
        combine(selection, estimateLce.state) { yearMonth, estimate ->
            createState(yearMonth, estimate.loading)
        }.withLce(estimateLce, errorStateMapper::mapToState)
            .stateIn(this, LceState(content = createState(selection.value, false)))

    private fun createState(selection: YearMonth, isLoading: Boolean) =
        BirthdayPickerState(
            title = stringRes(R.string.restore_title),
            message = stringRes(R.string.firstWalletTransactionSubtitleRestore),
            logo = null,
            primaryButton =
                ButtonState(
                    text = stringRes(R.string.wbh_next),
                    isLoading = isLoading,
                    onClick = ::onEstimateClick,
                ),
            secondaryButton = null,
            dialogButton =
                IconButtonState(
                    icon = R.drawable.ic_help,
                    onClick = ::onInfoButtonClick,
                ),
            onBack = ::onBack,
            onYearMonthChange = ::onYearMonthChange,
            selection = selection
        )

    private fun onEstimateClick() =
        estimateLce.execute {
            val instant =
                selection.value
                    .atDay(1)
                    .atStartOfDay()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toKotlinInstant()
            val bday =
                SdkSynchronizer.estimateBirthdayHeight(
                    context = application,
                    date = instant,
                    network = VersionInfo.NETWORK
                )
            navigationRouter.forward(RestoreEstimationArgs(seed = args.seed, blockHeight = bday.value))
        }

    private fun onBack() = navigationRouter.back()

    private fun onInfoButtonClick() = navigationRouter.forward(SeedInfo)

    private fun onYearMonthChange(yearMonth: YearMonth) = selection.update { yearMonth }
}
