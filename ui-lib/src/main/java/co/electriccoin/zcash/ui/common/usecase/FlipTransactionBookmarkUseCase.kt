package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.repository.MetadataRepository

class FlipTransactionBookmarkUseCase(
    private val metadataRepository: MetadataRepository,
    private val showError: ShowErrorUseCase
) {
    /**
     * Returns whether the bookmark change was saved, showing the generic error when it was not.
     */
    suspend operator fun invoke(txId: String): Boolean =
        metadataRepository.flipTxBookmark(txId).also { isSaved ->
            if (!isSaved) showError()
        }
}
