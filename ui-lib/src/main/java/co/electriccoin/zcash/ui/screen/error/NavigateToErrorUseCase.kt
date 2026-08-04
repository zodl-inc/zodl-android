package co.electriccoin.zcash.ui.screen.error

import co.electriccoin.lightwallet.client.model.ResponseException
import co.electriccoin.lightwallet.client.model.UninitializedTorClientException
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.SubmitResult
import co.electriccoin.zcash.ui.common.model.SynchronizerError
import co.electriccoin.zcash.ui.common.repository.HomeMessageData
import co.electriccoin.zcash.ui.common.repository.KeystoneFirmwareBelowMinimumException
import co.electriccoin.zcash.ui.design.util.getCausesAsSequence

class NavigateToErrorUseCase(
    private val navigationRouter: NavigationRouter,
) {
    private var args: ErrorArgs? = null

    operator fun invoke(args: ErrorArgs, navigate: NavigationRouter.(Any) -> Unit = { forward(it) }) {
        this.args = args
        when (args) {
            is ErrorArgs.ShieldingError -> navigationRouter.navigate(ErrorDialog)
            is ErrorArgs.SyncError -> navigateToSyncError(args, navigate)
            is ErrorArgs.General -> navigationRouter.navigate(ErrorDialog)
            is ErrorArgs.ShieldingGeneralError -> navigationRouter.navigate(ErrorDialog)
            is ErrorArgs.SynchronizerTorInitError -> navigationRouter.navigate(ErrorDialog)
            is ErrorArgs.KeystoneFirmwareUpdateRequired -> navigationRouter.navigate(ErrorBottomSheet)
            is ErrorArgs.KeystoneAccountUnsupported -> navigationRouter.navigate(ErrorBottomSheet)
        }
    }

    /**
     * @return true if [message] is sync error and navigation to sync error screen was performed.
     */
    fun navigateToSyncError(message: HomeMessageData.Error): Boolean =
        if (isSyncError(message.synchronizerError)) {
            this.args = ErrorArgs.SyncError(message.synchronizerError)
            navigationRouter.forward(SyncErrorArgs)
            true
        } else {
            false
        }

    @Suppress("MagicNumber")
    private fun navigateToSyncError(args: ErrorArgs.SyncError, navigate: NavigationRouter.(Any) -> Unit) {
        if (isSyncError(args.synchronizerError)) {
            navigationRouter.navigate(SyncErrorArgs)
        } else {
            navigationRouter.navigate(ErrorBottomSheet)
        }
    }

    @Suppress("MagicNumber")
    private fun isSyncError(synchronizerError: SynchronizerError): Boolean =
        synchronizerError.cause
            ?.getCausesAsSequence()
            .orEmpty()
            .any { e ->
                when {
                    e is ResponseException && e.code in 500..599 -> true

                    e is ResponseException &&
                        e.code == 3200 &&
                        (500..599)
                            .any { e.message.orEmpty().contains("$it", true) }
                    -> true

                    e is ResponseException &&
                        e.code == 3200 &&
                        e.message.orEmpty().contains("Tonic error: transport error", true)
                    -> true

                    e is UninitializedTorClientException -> true

                    else -> false
                }
            }

    fun requireCurrentArgs() = args as ErrorArgs

    fun clear() {
        args = null
    }
}

sealed interface ErrorArgs {
    data class SyncError(
        val synchronizerError: SynchronizerError
    ) : ErrorArgs

    data class ShieldingError(
        val error: SubmitResult
    ) : ErrorArgs

    data class ShieldingGeneralError(
        val exception: Exception
    ) : ErrorArgs

    data class General(
        val exception: Exception
    ) : ErrorArgs

    data object SynchronizerTorInitError : ErrorArgs

    /**
     * The scanned Keystone-signed PCZT came from firmware below the minimum supported version
     * (MOB-1510). The exception's `detected` is `null` when the firmware is too old to stamp its
     * version at all.
     */
    data class KeystoneFirmwareUpdateRequired(
        val exception: KeystoneFirmwareBelowMinimumException
    ) : ErrorArgs

    /**
     * A scanned Keystone account can't be used by this app — typically its UFVK belongs to a
     * different network than the app is built for (e.g. a mainnet Keystone account scanned into a
     * testnet build), which surfaces as a derivation failure.
     */
    data class KeystoneAccountUnsupported(
        val exception: Exception
    ) : ErrorArgs
}
