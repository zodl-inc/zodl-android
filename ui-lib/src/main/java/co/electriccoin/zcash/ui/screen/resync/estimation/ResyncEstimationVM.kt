package co.electriccoin.zcash.ui.screen.resync.estimation

import androidx.lifecycle.ViewModel
import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.groupLce
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.ConfirmResyncUseCase
import co.electriccoin.zcash.ui.common.usecase.CopyToClipboardUseCase
import co.electriccoin.zcash.ui.common.usecase.GetResyncDataFromHeightUseCase
import co.electriccoin.zcash.ui.common.usecase.ResyncErrorMapperUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.util.StringResourceColor
import co.electriccoin.zcash.ui.design.util.StyledStringStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import co.electriccoin.zcash.ui.design.util.styledStringResource
import co.electriccoin.zcash.ui.design.util.withStyle
import co.electriccoin.zcash.ui.screen.common.EstimatedBlockHeightState
import co.electriccoin.zcash.ui.screen.heightinfo.HeightInfoArgs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import java.time.YearMonth

class ResyncEstimationVM(
    private val args: ResyncEstimationArgs,
    private val navigationRouter: NavigationRouter,
    private val copyToClipboard: CopyToClipboardUseCase,
    private val getResyncDataFromHeight: GetResyncDataFromHeightUseCase,
    private val confirmResync: ConfirmResyncUseCase,
    private val errorStateMapper: ResyncErrorMapperUseCase,
) : ViewModel() {
    private val initLce = mutableLce<YearMonth>()
    private val confirmLce = mutableLce<Unit>()

    init {
        initLce.execute {
            getResyncDataFromHeight(BlockHeight.new(args.blockHeight))
        }
    }

    val state: StateFlow<LceState<EstimatedBlockHeightState>> =
        combine(
            initLce.state,
            confirmLce.state,
        ) { init, confirm ->
            val yearMonth = init.success ?: return@combine null
            createState(yearMonth, isLoading = confirm.loading)
        }.withLce(groupLce(initLce, confirmLce), errorStateMapper::mapToState)
            .stateIn(this)

    private fun createState(yearMonth: YearMonth, isLoading: Boolean): EstimatedBlockHeightState =
        EstimatedBlockHeightState(
            title = stringRes(R.string.resyncWallet_title),
            message =
                styledStringResource(
                    resource = R.string.resync_bd_estimation_message,
                    style =
                        StyledStringStyle(
                            color = StringResourceColor.TERTIARY,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        ),
                    stringRes(yearMonth).withStyle(
                        StyledStringStyle(
                            color = StringResourceColor.PRIMARY,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    ),
                    stringResByNumber(args.blockHeight, 0).withStyle(
                        style =
                            StyledStringStyle(
                                color = StringResourceColor.TERTIARY
                            )
                    )
                ),
            logo = null,
            dialogButton =
                IconButtonState(
                    icon = R.drawable.ic_help,
                    onClick = ::onInfoClick,
                ),
            onBack = ::onBack,
            blockHeightText = stringResByNumber(args.blockHeight, 0),
            copyButton =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.design.R.string.receive_copy),
                    icon = R.drawable.ic_copy,
                    onClick = ::onCopyClick
                ),
            primaryButton =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_confirm),
                    isLoading = isLoading,
                    onClick = ::onConfirmClick,
                ),
        )

    private fun onCopyClick() {
        copyToClipboard(
            value = args.blockHeight.toString()
        )
    }

    private fun onConfirmClick() =
        confirmLce.execute {
            confirmResync(BlockHeight.new(args.blockHeight))
        }

    private fun onInfoClick() = navigationRouter.forward(HeightInfoArgs)

    private fun onBack() = navigationRouter.back()
}
