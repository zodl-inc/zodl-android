package co.electriccoin.zcash.ui.screen.viewingkey.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.usecase.SubmitViewOnlyKeyResult
import co.electriccoin.zcash.ui.common.usecase.SubmitViewOnlyKeyUseCase
import co.electriccoin.zcash.ui.common.usecase.ViewOnlyKeyCheckException
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationState
import co.electriccoin.zcash.ui.screen.connecthw.HWWalletEnrollment
import co.electriccoin.zcash.ui.screen.connecthw.date.HWDateArgs
import co.electriccoin.zcash.ui.screen.error.ErrorArgs
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import co.electriccoin.zcash.ui.screen.scan.ImageToQrCodeResult
import co.electriccoin.zcash.ui.screen.scan.ScanValidationState
import co.electriccoin.zcash.ui.screen.viewingkey.alreadyimported.AlreadyImportedVKArgs
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

/**
 * Scans a full viewing key QR code, or picks an image of one. A code that is not an importable key
 * opens the invalid-code sheet, and the camera's further results are ignored while it is up. An
 * importable key replaces the scanner with the birthday date screen, so back from there returns to
 * the paste screen. The key is never logged.
 *
 * When the SDK fails to read a key, nothing is known about it, so the invalid-code sheet would be
 * wrong: the scanner stops taking results, returns to the paste screen and opens the general error
 * screen there, from where the user can scan again.
 *
 * Checking a key suspends, so the scanned-once guard runs under a mutex. Every check runs under one
 * parent job that back cancels, so a check still running when the user leaves navigates nowhere.
 */
internal class ScanVKVM(
    private val submitViewOnlyKey: SubmitViewOnlyKeyUseCase,
    private val navigationRouter: NavigationRouter,
    private val navigateToError: NavigateToErrorUseCase,
) : ViewModel() {
    private val validation = MutableStateFlow(ScanValidationState.NONE)

    private val isInvalidQrSheetVisible = MutableStateFlow(false)

    private val mutex = Mutex()

    private var hasBeenScannedSuccessfully = false

    private var hasCheckFailed = false

    private var sheetJob: Job? = null

    private val checkJob = SupervisorJob(viewModelScope.coroutineContext.job)

    val state: StateFlow<ScanVKState> =
        validation
            .map(::createState)
            .stateIn(viewModel = this, initialValue = createState(validation.value))

    val invalidQrSheet: StateFlow<ZashiConfirmationState?> =
        isInvalidQrSheetVisible
            .map { isVisible ->
                InvalidVKQrSheet
                    .create(
                        onPasteClick = ::onPasteClick,
                        onScanAgainClick = ::hideInvalidQrSheet,
                    ).takeIf { isVisible }
            }.stateIn(viewModel = this, initialValue = null)

    private fun createState(validation: ScanValidationState) =
        ScanVKState.create(validation = validation, onBack = ::onBack)

    fun onScanned(result: String) = viewModelScope.launch(checkJob) { handle(result) }

    fun onImageScanned(result: ImageToQrCodeResult) =
        viewModelScope.launch(checkJob) {
            when (result) {
                is ImageToQrCodeResult.SingleCode -> handle(result.text)
                ImageToQrCodeResult.MultipleCodes -> setValidation(ScanValidationState.SEVERAL_CODES_FOUND)
                ImageToQrCodeResult.NoCode -> setValidation(ScanValidationState.INVALID_IMAGE)
            }
        }

    private suspend fun handle(text: String) =
        mutex.withLock {
            if (hasBeenScannedSuccessfully || hasCheckFailed || isInvalidQrSheetVisible.value) return@withLock
            val result =
                try {
                    submitViewOnlyKey(text)
                } catch (e: ViewOnlyKeyCheckException) {
                    hasCheckFailed = true
                    navigationRouter.back()
                    navigateToError(ErrorArgs.General(e))
                    return@withLock
                }
            when (result) {
                SubmitViewOnlyKeyResult.Accepted -> {
                    hasBeenScannedSuccessfully = true
                    setValidation(ScanValidationState.VALID)
                    navigationRouter.replace(HWDateArgs(HWWalletEnrollment.ViewOnly))
                }

                SubmitViewOnlyKeyResult.AlreadyImported -> {
                    hasBeenScannedSuccessfully = true
                    navigationRouter.replace(AlreadyImportedVKArgs)
                }

                SubmitViewOnlyKeyResult.Invalid -> {
                    setValidation(ScanValidationState.NONE)
                    isInvalidQrSheetVisible.update { true }
                }
            }
        }

    private fun hideInvalidQrSheet() = isInvalidQrSheetVisible.update { false }

    /**
     * Hides the sheet, then returns to the paste screen, which is what opened the scanner.
     */
    private fun onPasteClick() {
        if (sheetJob?.isActive == true) return
        sheetJob =
            viewModelScope.launch {
                hideInvalidQrSheet()
                delay(350.milliseconds)
                navigationRouter.back()
            }
    }

    /**
     * Leaves the scanner: the running check is cancelled first, so it cannot navigate afterwards.
     */
    private fun onBack() {
        checkJob.cancel()
        navigationRouter.back()
    }

    private fun setValidation(validation: ScanValidationState) = this.validation.update { validation }
}
