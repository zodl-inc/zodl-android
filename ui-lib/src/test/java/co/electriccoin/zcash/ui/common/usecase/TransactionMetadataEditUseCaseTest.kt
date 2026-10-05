package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The note and bookmark use cases returning whether the edit was saved, and still leaving the
 * note sheet when it was not.
 */
class TransactionMetadataEditUseCaseTest {
    private val metadataRepository = mockk<MetadataRepository>()
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)

    @Test
    fun savingANoteReturnsTheRepositoryResultAndGoesBack() =
        runTest {
            val useCase = CreateOrUpdateTransactionNoteUseCase(metadataRepository, navigationRouter)
            coEvery { metadataRepository.createOrUpdateTxNote("tx1", "note") } returns false

            assertFalse(useCase("tx1", "  note  "))
            verify(exactly = 1) { navigationRouter.back() }

            coEvery { metadataRepository.createOrUpdateTxNote("tx1", "note") } returns true
            assertTrue(useCase("tx1", "note"))
        }

    @Test
    fun deletingANoteReturnsTheRepositoryResultAndGoesBack() =
        runTest {
            val useCase = DeleteTransactionNoteUseCase(metadataRepository, navigationRouter)
            coEvery { metadataRepository.deleteTxNote("tx1") } returns false

            assertFalse(useCase("tx1"))
            verify(exactly = 1) { navigationRouter.back() }

            coEvery { metadataRepository.deleteTxNote("tx1") } returns true
            assertTrue(useCase("tx1"))
        }

    @Test
    fun flippingABookmarkReturnsTheRepositoryResult() =
        runTest {
            val useCase = FlipTransactionBookmarkUseCase(metadataRepository)
            coEvery { metadataRepository.flipTxBookmark("tx1") } returns false

            assertFalse(useCase("tx1"))

            coEvery { metadataRepository.flipTxBookmark("tx1") } returns true
            assertTrue(useCase("tx1"))
        }
}
