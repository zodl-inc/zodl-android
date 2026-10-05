package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.MetadataRepository

class DeleteTransactionNoteUseCase(
    private val metadataRepository: MetadataRepository,
    private val navigationRouter: NavigationRouter
) {
    /**
     * Deletes the note, leaves the note sheet and returns whether the note was deleted.
     */
    suspend operator fun invoke(txId: String): Boolean {
        val isDeleted = metadataRepository.deleteTxNote(txId)
        navigationRouter.back()
        return isDeleted
    }
}
