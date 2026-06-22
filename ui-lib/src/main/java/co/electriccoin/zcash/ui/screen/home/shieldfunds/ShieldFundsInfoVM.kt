package co.electriccoin.zcash.ui.screen.home.shieldfunds

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.provider.ShieldFundsInfoProvider
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.ShieldFundsUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CheckboxState
import co.electriccoin.zcash.ui.design.util.TickerLocation.HIDDEN
import co.electriccoin.zcash.ui.design.util.asPrivacySensitive
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.util.CURRENCY_TICKER
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShieldFundsInfoVM(
    getSelectedWalletAccount: GetSelectedWalletAccountUseCase,
    private val shieldFundsInfoProvider: ShieldFundsInfoProvider,
    private val navigationRouter: NavigationRouter,
    private val shieldFunds: ShieldFundsUseCase,
) : ViewModel() {
    val state: StateFlow<ShieldFundsInfoState?> =
        combine(
            getSelectedWalletAccount.observe(),
            shieldFundsInfoProvider.observe(),
        ) { account, infoEnabled ->
            val transparentAmount = account?.transparent?.balance ?: Zatoshi(0)
            ShieldFundsInfoState(
                onBack = ::onBack,
                primaryButton =
                    ButtonState(
                        onClick = ::onShieldClick,
                        text = stringRes(R.string.home_info_transparent_balance_shield),
                        hapticFeedbackType = HapticFeedbackType.Confirm
                    ),
                secondaryButton =
                    ButtonState(
                        onClick = ::onNotNowClick,
                        text = stringRes(R.string.smartBanner_help_shield_notNow),
                    ),
                subtitle =
                    stringRes(
                        R.string.home_message_transparent_balance_subtitle,
                        stringRes(transparentAmount, HIDDEN).asPrivacySensitive(),
                        CURRENCY_TICKER
                    ),
                checkbox =
                    CheckboxState(
                        title = stringRes(R.string.smartBanner_help_shield_doNotShowAgain),
                        onClick = ::onCheckboxClick,
                        isChecked = !infoEnabled
                    )
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    private fun onCheckboxClick() = viewModelScope.launch { shieldFundsInfoProvider.flip() }

    private fun onNotNowClick() = navigationRouter.back()

    private fun onBack() = navigationRouter.back()

    private fun onShieldClick() = shieldFunds(closeCurrentScreen = true)
}
