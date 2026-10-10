package co.electriccoin.zcash.ui.common.appbar

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.preference.StandardPreferenceProvider
import co.electriccoin.zcash.preference.model.entry.BooleanPreferenceDefault
import co.electriccoin.zcash.ui.BuildConfig
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.DistributionDimension
import co.electriccoin.zcash.ui.common.model.HWWalletAccount
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.provider.GetVersionInfoProvider
import co.electriccoin.zcash.ui.common.repository.ConfigurationRepository
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.preference.StandardPreferenceKeys
import co.electriccoin.zcash.ui.screen.accountlist.AccountListArgs
import co.electriccoin.zcash.ui.screen.integrations.IntegrationsArgs
import co.electriccoin.zcash.ui.screen.more.MoreArgs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ZashiTopAppBarVM(
    getWalletAccountsUseCase: GetWalletAccountsUseCase,
    private val standardPreferenceProvider: StandardPreferenceProvider,
    private val navigationRouter: NavigationRouter,
    private val getVersionInfo: GetVersionInfoProvider,
    private val configurationRepository: ConfigurationRepository,
) : ViewModel() {
    private val isHideBalances: StateFlow<Boolean?> = booleanStateFlow(StandardPreferenceKeys.IS_HIDE_BALANCES)

    val state =
        combine(
            getWalletAccountsUseCase.observe(),
            isHideBalances,
        ) { accounts, isHideBalances ->
            createState(accounts, isHideBalances)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue =
                createState(
                    accounts = getWalletAccountsUseCase.observe().value,
                    isHideBalances = isHideBalances.value
                )
        )

    private fun createState(accounts: List<WalletAccount>?, isHideBalances: Boolean?): ZashiMainTopAppBarState {
        val current = accounts?.firstOrNull { it.isSelected }
        return ZashiMainTopAppBarState(
            accountSwitchState =
                accounts?.let {
                    AccountSwitchState(
                        onAccountTypeClick = ::onAccountTypeClicked,
                        accountType =
                            when (current) {
                                is KeystoneAccount -> ZashiMainTopAppBarState.AccountType.KEYSTONE
                                is LedgerAccount -> ZashiMainTopAppBarState.AccountType.LEDGER
                                else -> ZashiMainTopAppBarState.AccountType.ZASHI
                            },
                    )
                },
            balanceVisibilityButton =
                IconButtonState(
                    icon =
                        if (isHideBalances == true) {
                            R.drawable.ic_app_bar_balances_hide
                        } else {
                            R.drawable.ic_app_bar_balances_show
                        },
                    onClick = ::onShowOrHideBalancesClicked,
                    contentDescription = stringRes(co.electriccoin.zcash.ui.R.string.hide_balances_content_description),
                    hapticFeedbackType =
                        if (isHideBalances == true) {
                            HapticFeedbackType.ToggleOn
                        } else {
                            HapticFeedbackType.ToggleOff
                        }
                ),
            moreButton =
                IconButtonState(
                    icon = co.electriccoin.zcash.ui.R.drawable.ic_home_more,
                    onClick = { onInfoClick(accounts) },
                    onDoubleClick = { navigationRouter.forward(MoreArgs) }.takeIf { BuildConfig.DEBUG },
                    contentDescription = stringRes(R.string.general_more)
                )
        )
    }

    private fun onAccountTypeClicked() = navigationRouter.forward(AccountListArgs)

    private fun onInfoClick(accounts: List<WalletAccount>?) =
        viewModelScope.launch {
            if (getVersionInfo().distribution == DistributionDimension.FOSS) {
                val isFlexaAvailable = configurationRepository.isFlexaAvailable()
                val isHWWalletConnected = accounts.orEmpty().any { it is HWWalletAccount }
                if (!isFlexaAvailable && isHWWalletConnected) {
                    navigationRouter.forward(MoreArgs)
                } else {
                    navigationRouter.forward(IntegrationsArgs)
                }
            } else {
                navigationRouter.forward(IntegrationsArgs)
            }
        }

    private fun onShowOrHideBalancesClicked() =
        viewModelScope.launch {
            setBooleanPreference(StandardPreferenceKeys.IS_HIDE_BALANCES, isHideBalances.filterNotNull().first().not())
        }

    private fun booleanStateFlow(default: BooleanPreferenceDefault): StateFlow<Boolean?> =
        flow<Boolean?> {
            emitAll(default.observe(standardPreferenceProvider()))
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            null
        )

    private fun setBooleanPreference(
        default: BooleanPreferenceDefault,
        newState: Boolean
    ) {
        viewModelScope.launch {
            default.putValue(standardPreferenceProvider(), newState)
        }
    }
}
