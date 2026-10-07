package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.repository.GiftCardLinkStoreImpl
import co.electriccoin.zcash.ui.common.repository.GiftCardRepositoryImpl
import co.electriccoin.zcash.ui.fixture.FakeGiftCardDataSource
import co.electriccoin.zcash.ui.fixture.GiftCardSummaryFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration

/**
 * [SweepOrphanedGiftCardWalletsUseCase] erases, before it returns, every card wallet left on the device that no
 * redemption is using.
 */
class SweepOrphanedGiftCardWalletsUseCaseTest {
    @Test
    fun erasesTheOrphanedCardWalletsBeforeReturning() =
        runTest {
            val orphans =
                listOf(
                    GiftCardSummaryFixture.storedWallet(),
                    GiftCardSummaryFixture.storedWallet(alias = "giftcard_fedcba9876543210fedcba9876543210")
                )
            val dataSource = FakeGiftCardDataSource(storedWallets = orphans)
            val repository =
                GiftCardRepositoryImpl(dataSource, GiftCardLinkStoreImpl()).also {
                    it.scope = backgroundScope
                    it.quietRecheckMinDuration = Duration.ZERO
                }

            SweepOrphanedGiftCardWalletsUseCase(repository)()

            assertEquals(orphans, dataSource.erased)
        }
}
