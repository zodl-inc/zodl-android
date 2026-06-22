package co.electriccoin.zcash.ui.screen.resync.date

import android.app.Application
import androidx.lifecycle.ViewModel
import cash.z.ecc.android.sdk.SdkSynchronizer
import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.common.model.groupLce
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.GetResyncDataFromHeightUseCase
import co.electriccoin.zcash.ui.common.usecase.ResyncErrorMapperUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.BirthdayPickerState
import co.electriccoin.zcash.ui.screen.heightinfo.HeightInfoArgs
import co.electriccoin.zcash.ui.screen.resync.estimation.ResyncEstimationArgs
import co.electriccoin.zcash.ui.screen.resync.height.ResyncHeightArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.time.YearMonth
import java.time.ZoneId
import kotlin.time.toKotlinInstant

class ResyncDateVM(
    private val args: ResyncDateArgs,
    private val navigationRouter: NavigationRouter,
    private val application: Application,
    private val getResyncDataFromHeight: GetResyncDataFromHeightUseCase,
    private val errorStateMapper: ResyncErrorMapperUseCase,
) : ViewModel() {
    private val initLce = mutableLce<YearMonth>()
    private val estimateLce = mutableLce<Unit>()
    private val selectionOverride = MutableStateFlow<YearMonth?>(null)

    init {
        initLce.execute {
            getResyncDataFromHeight(BlockHeight.new(args.initialBlockHeight))
        }
    }

    val state =
        combine(
            initLce.state,
            selectionOverride,
            estimateLce.state,
        ) { init, override, estimate ->
            val yearMonth = override ?: init.success ?: return@combine null
            createState(yearMonth, isLoading = estimate.loading)
        }.withLce(groupLce(initLce, estimateLce), errorStateMapper::mapToState)
            .stateIn(this)

    private fun createState(
        selection: YearMonth,
        isLoading: Boolean,
    ) = BirthdayPickerState(
        title = stringRes(R.string.resync_title),
        message = stringRes(R.string.firstWalletTransactionSubtitleResync),
        logo = null,
        primaryButton =
            ButtonState(
                text = stringRes(R.string.wbh_next),
                isLoading = isLoading,
                onClick = { onEstimateClick(selection) },
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
        selection = selection
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
            val height =
                SdkSynchronizer.estimateBirthdayHeight(
                    context = application,
                    date = instant,
                    network = VersionInfo.NETWORK
                )
            navigationRouter.forward(ResyncEstimationArgs(uuid = args.uuid, blockHeight = height.value))
        }

    private fun onBack() = navigationRouter.back()

    private fun onEnterBlockHeightClick() = navigationRouter.forward(ResyncHeightArgs)

    private fun onInfoClick() = navigationRouter.forward(HeightInfoArgs)

    private fun onYearMonthChange(yearMonth: YearMonth) = selectionOverride.update { yearMonth }
}
