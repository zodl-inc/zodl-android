package co.electriccoin.zcash.ui.screen.restore.tor

import androidx.lifecycle.ViewModel
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.SeedPhrase
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.LceState
import co.electriccoin.zcash.ui.common.model.mutableLce
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.model.withLce
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.RestoreWalletUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CheckboxState
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

class RestoreTorVM(
    private val args: RestoreTorArgs,
    private val navigationRouter: NavigationRouter,
    private val restoreWallet: RestoreWalletUseCase,
    private val errorStateMapper: ErrorMapperUseCase,
) : ViewModel() {
    private val isChecked = MutableStateFlow(false)
    private val restoreLce = mutableLce<Unit>()

    val state: StateFlow<LceState<RestoreTorState>> =
        combine(isChecked, restoreLce.state) { checked, restore ->
            createState(checked, restore.loading)
        }.withLce(restoreLce, errorStateMapper::mapToState)
            .stateIn(this, LceState(content = createState(isChecked.value, false)))

    private fun createState(isChecked: Boolean, isLoading: Boolean): RestoreTorState =
        RestoreTorState(
            checkbox =
                CheckboxState(
                    title = stringRes(R.string.restore_tor_checkbox_title),
                    subtitle = stringRes(R.string.torSettingsSheet_desc),
                    isChecked = isChecked,
                    onClick = { this.isChecked.update { !it } }
                ),
            primary =
                ButtonState(
                    text = stringRes(R.string.restore_bd_restore_btn),
                    isLoading = isLoading,
                    onClick = { onRestoreWalletClick(isChecked) },
                ),
            secondary =
                ButtonState(
                    text = stringRes(co.electriccoin.zcash.ui.design.R.string.general_cancel),
                    onClick = ::onBack,
                ),
            onBack = ::onBack
        )

    private fun onBack() = navigationRouter.back()

    private fun onRestoreWalletClick(isChecked: Boolean) =
        restoreLce.execute {
            restoreWallet(
                seedPhrase = SeedPhrase.new(args.seed.trim()),
                enableTor = isChecked,
                birthday = BlockHeight.new(args.blockHeight)
            )
        }
}
