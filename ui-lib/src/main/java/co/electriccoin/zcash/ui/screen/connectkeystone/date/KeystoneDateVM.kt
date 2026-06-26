package co.electriccoin.zcash.ui.screen.connectkeystone.date

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
import co.electriccoin.zcash.ui.screen.connectkeystone.estimation.KeystoneEstimationArgs
import co.electriccoin.zcash.ui.screen.connectkeystone.height.KeystoneHeightArgs
import co.electriccoin.zcash.ui.screen.heightinfo.HeightInfoArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.time.YearMonth
import java.time.ZoneId
import kotlin.time.toKotlinInstant

class KeystoneDateVM(
    private val args: KeystoneDateArgs,
    private val navigationRouter: NavigationRouter,
    private val application: Application,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val selection = MutableStateFlow(WalletFixture.SAPLING_ACTIVATION_YEAR_MONTH)
    private val estimateLce = mutableLce<Unit>()

    val state: StateFlow<LceState<BirthdayPickerState>> =
        combine(selection, estimateLce.state) { yearMonth, estimate ->
            createState(yearMonth, estimate.loading)
        }.withLce(estimateLce, errorStateMapper::mapToState)
            .stateIn(this, LceState(content = createState(selection.value, false)))

    private fun createState(yearMonth: YearMonth, isLoading: Boolean) =
        BirthdayPickerState(
            title = null,
            message = stringRes(R.string.firstWalletTransactionSubtitleHWWallet),
            logo = co.electriccoin.zcash.ui.design.R.drawable.image_keystone,
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
                    text = stringRes(R.string.keystone_addHWWallet_enterManually),
                    onClick = ::onEnterBlockHeightClick,
                ),
            dialogButton =
                IconButtonState(
                    icon = R.drawable.ic_help,
                    onClick = ::onInfoClick,
                ),
            onBack = ::onBack,
            onYearMonthChange = ::onYearMonthChange,
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
            navigationRouter.forward(
                KeystoneEstimationArgs(
                    ur = args.ur,
                    blockHeight = bday.value,
                )
            )
        }

    private fun onEnterBlockHeightClick() = navigationRouter.forward(KeystoneHeightArgs(args.ur))

    private fun onBack() = navigationRouter.back()

    private fun onInfoClick() = navigationRouter.forward(HeightInfoArgs)

    private fun onYearMonthChange(yearMonth: YearMonth) = selection.update { yearMonth }
}
