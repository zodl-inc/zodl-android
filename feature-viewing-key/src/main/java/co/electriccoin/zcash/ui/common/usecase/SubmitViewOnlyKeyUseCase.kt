package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.exception.InitializeException
import cash.z.ecc.android.sdk.model.UnifiedFullViewingKey
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.model.VersionInfo
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.PendingViewOnlyImport
import co.electriccoin.zcash.ui.common.repository.ViewOnlyPendingImportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Takes the viewing key the user pasted or scanned: an importable key the wallet does not hold yet
 * becomes the pending import, whose birthday and then name the next screens ask for. The key goes
 * to memory only.
 *
 * A key counts as held already when the SDK finds an account sharing any Orchard, Sapling (at the
 * incoming-viewing-key level) or transparent component with it, not only when it is the same text.
 * Every kind of overlap is refused, including a key that would upgrade an existing account: the SDK
 * would otherwise silently widen that account with a key that contains it, or refuse a key that
 * overlaps one, and a view-only import is only ever meant to add a new account.
 */
internal class SubmitViewOnlyKeyUseCase(
    private val validateUfvk: ValidateUfvkUseCase,
    private val synchronizerProvider: SynchronizerProvider,
    private val pendingImportRepository: ViewOnlyPendingImportRepository,
) {
    /**
     * @throws ViewOnlyKeyCheckException if the SDK failed to read the key; nothing is kept then.
     */
    suspend operator fun invoke(
        input: String,
        network: ZcashNetwork = VersionInfo.NETWORK,
    ): SubmitViewOnlyKeyResult =
        when (val validation = validateUfvk(input, network)) {
            UfvkValidation.Invalid -> {
                SubmitViewOnlyKeyResult.Invalid
            }

            is UfvkValidation.Valid -> {
                if (isAlreadyImported(validation.ufvk)) {
                    SubmitViewOnlyKeyResult.AlreadyImported
                } else {
                    pendingImportRepository.set(PendingViewOnlyImport(ufvk = validation.ufvk, name = ""))
                    SubmitViewOnlyKeyResult.Accepted
                }
            }
        }

    /**
     * An early answer for the user, not the guard: the import repeats the lookup right before it
     * runs and refuses any overlap there. A key matching several accounts is reported by the SDK as
     * a collision, which is a key not to import. When the lookup fails, or getting the synchronizer
     * and the lookup together take longer than [LOOKUP_TIMEOUT], the key is accepted and the import's
     * own check decides. The SDK's exception is dropped without its message.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun isAlreadyImported(ufvk: String): Boolean =
        try {
            withTimeoutOrNull(LOOKUP_TIMEOUT) {
                synchronizerProvider
                    .getSynchronizer()
                    .findAccountSharingKeyComponents(UnifiedFullViewingKey(ufvk)) != null
            } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: InitializeException.ImportAccountCollisionException) {
            true
        } catch (e: Exception) {
            false
        }

    private companion object {
        val LOOKUP_TIMEOUT = 5.seconds
    }
}

internal enum class SubmitViewOnlyKeyResult {
    Accepted,
    Invalid,
    AlreadyImported,
}
