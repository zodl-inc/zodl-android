package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.repository.MetadataRepository

class FlipTransactionBookmarkUseCase(
    private val metadataRepository: MetadataRepository,
) {
    /**
     * Returns whether the bookmark change was saved.
     */
    suspend operator fun invoke(txId: String): Boolean = metadataRepository.flipTxBookmark(txId)
}
