package co.electriccoin.zcash.ui.screen.scankeystone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.repository.KeystoneFirmwareBelowMinimumException
import co.electriccoin.zcash.ui.common.repository.ParsePCZTException
import co.electriccoin.zcash.ui.common.usecase.InvalidKeystonePCZTQRException
import co.electriccoin.zcash.ui.common.usecase.ParseKeystonePCZTUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import co.electriccoin.zcash.ui.screen.scan.ScanValidationState
import co.electriccoin.zcash.ui.screen.scankeystone.model.ScanKeystoneState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ScanKeystonePCZTViewModel(
    private val parseKeystonePCZT: ParseKeystonePCZTUseCase,
    private val navigateToError: NavigateToErrorUseCase,
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
            } catch (e: KeystoneFirmwareBelowMinimumException) {
                // MOB-1510: the QR decoded fine but the device firmware is too old — replace the
                // scan screen with the firmware-update sheet so closing it lands back on the sign
                // screen instead of the camera instantly re-scanning the same rejected QR.
                navigateToError(ErrorArgs.KeystoneFirmwareUpdateRequired(e)) { replace(it) }
            } catch (_: InvalidKeystonePCZTQRException) {
                validationState.update { ScanValidationState.INVALID }
            } catch (_: ParsePCZTException) {
                validationState.update { ScanValidationState.INVALID }
            } catch (_: Exception) {
                // do nothing
            }
        }
}
