package co.electriccoin.zcash.ui.screen.home.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.provider.WalletBackupConsentStorageProvider
import co.electriccoin.zcash.ui.common.usecase.RemindWalletBackupLaterUseCase
import co.electriccoin.zcash.ui.common.usecase.WalletBackupData
import co.electriccoin.zcash.ui.common.usecase.WalletBackupMessageUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CheckboxState
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WalletBackupInfoViewModel(
    walletBackupMessageUseCase: WalletBackupMessageUseCase,
    walletBackupConsentStorageProvider: WalletBackupConsentStorageProvider,
    private val navigationRouter: NavigationRouter,
    private val remindWalletBackupLater: RemindWalletBackupLaterUseCase,
) : ViewModel() {
    private val isConsentChecked = MutableStateFlow(false)

    private val lockoutDuration =
        walletBackupMessageUseCase
            .observe()
            .filterIsInstance<WalletBackupData.Available>()
            .take(1)
            .map { it.lockoutDuration }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = null
            )

    val state: StateFlow<WalletBackupInfoState?> =
        combine(
            lockoutDuration.filterNotNull(),
            isConsentChecked,
            walletBackupConsentStorageProvider.observe().take(1)
        ) { lockout, isConsentChecked, isConsentSaved ->
            WalletBackupInfoState(
                onBack = ::onBack,
                secondaryButton =
                    ButtonState(
                        text = stringRes(R.string.general_remind_me_in, stringRes(lockout.res)),
                        onClick = ::onRemindMeLaterClick,
                        isEnabled = isConsentChecked || isConsentSaved
                    ),
                checkboxState =
                    CheckboxState(
                        isChecked = isConsentChecked,
                        onClick = ::onConsentClick,
                        title = stringRes(R.string.smartBanner_help_backup_acknowledge)
                    ).takeIf { !isConsentSaved },
                primaryButton =
                    ButtonState(
                        text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_ok),
                        onClick = ::onPrimaryClick
                    )
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    private fun onConsentClick() = isConsentChecked.update { !it }

    private fun onPrimaryClick() = navigationRouter.replace(WalletBackupDetail(true))

    private fun onRemindMeLaterClick() = viewModelScope.launch { remindWalletBackupLater(persistConsent = true) }

    private fun onBack() = navigationRouter.back()
}
