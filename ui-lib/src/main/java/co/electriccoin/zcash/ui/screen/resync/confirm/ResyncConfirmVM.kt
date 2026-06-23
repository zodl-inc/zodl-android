package co.electriccoin.zcash.ui.screen.resync.confirm

import androidx.lifecycle.ViewModel
import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.groupLce
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.usecase.ConfirmResyncUseCase
import co.electriccoin.zcash.ui.common.usecase.GetResyncDataFromHeightUseCase
import co.electriccoin.zcash.ui.common.usecase.ResyncErrorMapperUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.util.StringResourceColor
import co.electriccoin.zcash.ui.design.util.StyledStringStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import co.electriccoin.zcash.ui.design.util.styledStringResource
import co.electriccoin.zcash.ui.design.util.withStyle
import co.electriccoin.zcash.ui.screen.resync.date.ResyncDateArgs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import java.time.YearMonth
import java.util.UUID

class ResyncConfirmVM(
    persistableWalletProvider: PersistableWalletProvider,
    private val navigationRouter: NavigationRouter,
    private val confirmResync: ConfirmResyncUseCase,
    private val getResyncDataFromHeight: GetResyncDataFromHeightUseCase,
    private val errorStateMapper: ResyncErrorMapperUseCase,
) : ViewModel() {
    private val initLce = mutableLce<BlockHeight>()
    private val confirmLce = mutableLce<Unit>()

    init {
        initLce.execute {
            persistableWalletProvider.requirePersistableWallet().birthday ?: error("Wallet birthday is null")
        }
    }

    private val yearMonthFlow =
        initLce.state
            .mapNotNull { it.success }
            .map { getResyncDataFromHeight(it) }

    val state: StateFlow<LceState<ResyncConfirmState>> =
        combine(
            initLce.state,
            yearMonthFlow,
            confirmLce.state,
        ) { init, yearMonth, confirm ->
            val height = init.success ?: return@combine null
            createState(height = height, yearMonth = yearMonth, isLoading = confirm.loading)
        }.withLce(groupLce(initLce, confirmLce), errorStateMapper::mapToState)
            .stateIn(this)

    private fun createState(
        height: BlockHeight,
        yearMonth: YearMonth,
        isLoading: Boolean,
    ): ResyncConfirmState =
        ResyncConfirmState(
            title = stringRes(R.string.resyncWallet_title),
            subtitle = stringRes(R.string.resyncWallet_confirmTitle),
            message = stringRes(R.string.confirm_resync_subtitle),
            onBack = ::onBack,
            confirm =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_confirm),
                    isLoading = isLoading,
                    onClick = {
                        onConfirmClick(height)
                    }
                ),
            change =
                ButtonState(
                    stringRes(R.string.resyncWallet_change),
                    onClick = {
                        onChangeClick(height)
                    }
                ),
            changeInfo =
                styledStringResource(
                    resource = R.string.confirm_resync_info,
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
                    stringResByNumber(height.value, 0).withStyle(
                        style =
                            StyledStringStyle(
                                color = StringResourceColor.TERTIARY
                            )
                    )
                ),
        )

    private fun onBack() = navigationRouter.back()

    private fun onChangeClick(height: BlockHeight) {
        val uuid = UUID.randomUUID().toString()
        val args = ResyncDateArgs(uuid = uuid, initialBlockHeight = height.value)
        navigationRouter.forward(args)
    }

    private fun onConfirmClick(height: BlockHeight) {
        confirmLce.execute {
            confirmResync(height)
        }
    }
}
