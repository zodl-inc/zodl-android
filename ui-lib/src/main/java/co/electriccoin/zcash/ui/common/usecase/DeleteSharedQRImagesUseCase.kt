package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import co.electriccoin.zcash.spackle.getInternalCacheDirSuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Deletes the QR images [ShareImageUseCase] left behind for the receiving app. Runs on its own scope so a caller
 * can fire it from `ViewModel.onCleared`, where `viewModelScope` is already cancelled.
 */
class DeleteSharedQRImagesUseCase(
    private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    operator fun invoke(filePrefix: String) =
        scope.launch {
            runCatching {
                context
                    .getInternalCacheDirSuspend(CACHE_SUBDIR)
                    .listFiles { file -> file.name.startsWith(filePrefix) }
                    ?.forEach { it.delete() }
            }
        }
}
