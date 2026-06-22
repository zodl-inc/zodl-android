package co.electriccoin.zcash.ui.screen.scankeystone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.ParsePCZTException
import co.electriccoin.zcash.ui.common.usecase.InvalidKeystonePCZTQRException
import co.electriccoin.zcash.ui.common.usecase.ParseKeystonePCZTUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.scan.ScanValidationState
import co.electriccoin.zcash.ui.screen.scankeystone.model.ScanKeystoneState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ScanKeystonePCZTViewModel(
    private val parseKeystonePCZT: ParseKeystonePCZTUseCase
) : ViewModel() {
    val validationState = MutableStateFlow(ScanValidationState.NONE)

    val state =
        MutableStateFlow(
            ScanKeystoneState(
                progress = null,
                message = stringRes(R.string.coinVote_delegationSigning_scanInstructions),
            )
        )

    fun onScanned(result: String) =
        viewModelScope.launch {
            try {
                val scanResult = parseKeystonePCZT(result)
                state.update { it.copy(progress = scanResult.progress) }
            } catch (_: InvalidKeystonePCZTQRException) {
                validationState.update { ScanValidationState.INVALID }
            } catch (_: ParsePCZTException) {
                validationState.update { ScanValidationState.INVALID }
            } catch (_: Exception) {
                // do nothing
            }
        }
}
