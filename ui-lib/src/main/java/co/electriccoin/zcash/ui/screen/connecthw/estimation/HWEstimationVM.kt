package co.electriccoin.zcash.ui.screen.connecthw.estimation

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.guardLoading
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.CreateHWWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByNumber
import co.electriccoin.zcash.ui.screen.common.EstimatedBlockHeightState
import co.electriccoin.zcash.ui.screen.connecthw.brandingOf
import co.electriccoin.zcash.ui.screen.heightinfo.HeightInfoArgs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * Confirms the estimated birthday height and imports the enrolled hardware-wallet account at it; a
 * view-only import stores the height and goes on to name the account instead.
 */
class HWEstimationVM(
    private val args: HWEstimationArgs,
    private val createHWWalletAccount: CreateHWWalletAccountUseCase,
    private val navigationRouter: NavigationRouter,
    private val errorStateMapper: ErrorMapperUseCase,
    viewOnlyContent: ViewOnlyContent,
) : ViewModel() {
    private val branding = brandingOf(args.enrollment, viewOnlyContent)
    private val createAccountLce = mutableLce<Unit>()

    val state: StateFlow<LceState<EstimatedBlockHeightState>> =
        createAccountLce.state
            .map { lce -> createState(isLoading = lce.loading) }
            .withLce(createAccountLce, errorStateMapper::mapToState)
            .stateIn(this, LceState(content = createState(isLoading = false)))

    init {
        if (!createHWWalletAccount.isReady(args.enrollment)) {
            navigationRouter.backToRoot()
        }
    }

    private fun createState(
        isLoading: Boolean,
    ) = EstimatedBlockHeightState(
        title = null,
        logo = branding.logo,
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
                onClick = {},
            ),
        primaryButton =
            ButtonState(
                text = branding.connect,
                isEnabled = !isLoading,
                isLoading = isLoading,
                onClick = ::onConfirmClick,
                hapticFeedbackType = HapticFeedbackType.Confirm,
            ),
    )

    /**
     * A second tap while the import runs is ignored: restarting would cancel the running import
     * inside the SDK call, after the account exists but before it is selected.
     */
    private fun onConfirmClick() =
        createAccountLce.guardLoading {
            createAccountLce.execute {
                createHWWalletAccount(args.enrollment, BlockHeight.new(args.blockHeight))
            }
        }

    private fun onInfoClick() = navigationRouter.forward(HeightInfoArgs)

    private fun onBack() = createAccountLce.guardLoading { navigationRouter.back() }
}
