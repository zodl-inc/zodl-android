package co.electriccoin.zcash.ui.common.usecase

import androidx.navigation.NavBackStackEntry
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.MigrationSweepTransactionProposal
import co.electriccoin.zcash.ui.common.datasource.ShieldTransactionProposal
import co.electriccoin.zcash.ui.common.migration.MigrationNavigator
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.repository.ZashiProposalRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CancelProposalFlowUseCaseTest {
    @Test
    fun migrationSweepProposalNavigatesBackToMigrationReviewInsteadOfSend() =
        runTest {
            val proposal = MigrationSweepTransactionProposal(Zatoshi(500_000L), mockk<Proposal>())
            val keystoneProposalRepository =
                mockk<KeystoneProposalRepository>(relaxed = true) {
                    coEvery { getTransactionProposal() } returns proposal
                }
            val router = FakeNavigationRouter()
            val migrationNavigator = FakeMigrationNavigator()
            val useCase =
                CancelProposalFlowUseCase(
                    zashiProposalRepository = mockk<ZashiProposalRepository>(relaxed = true),
                    keystoneProposalRepository = keystoneProposalRepository,
                    navigationRouter = router,
                    observeClearSend = mockk<ObserveClearSendUseCase>(relaxed = true),
                    accountDataSource =
                        mockk<AccountDataSource> {
                            coEvery { getSelectedAccount() } returns mockk<KeystoneAccount>(relaxed = true)
                        },
                    swapRepository = mockk<SwapRepository>(relaxed = true),
                    migrationNavigator = migrationNavigator,
                )

            useCase()

            coVerify(exactly = 1) { keystoneProposalRepository.clear() }
            assertEquals(0, router.backToCalls.size)
            assertEquals(1, migrationNavigator.backToReviewCalls)
        }

    @Test
    fun shieldProposalNavigatesBackInsteadOfSend() =
        runTest {
            val proposal = ShieldTransactionProposal(mockk<Proposal>())
            val keystoneProposalRepository =
                mockk<KeystoneProposalRepository>(relaxed = true) {
                    coEvery { getTransactionProposal() } returns proposal
                }
            val router = FakeNavigationRouter()
            val useCase =
                CancelProposalFlowUseCase(
                    zashiProposalRepository = mockk<ZashiProposalRepository>(relaxed = true),
                    keystoneProposalRepository = keystoneProposalRepository,
                    navigationRouter = router,
                    observeClearSend = mockk<ObserveClearSendUseCase>(relaxed = true),
                    accountDataSource =
                        mockk<AccountDataSource> {
                            coEvery { getSelectedAccount() } returns mockk<KeystoneAccount>(relaxed = true)
                        },
                    swapRepository = mockk<SwapRepository>(relaxed = true),
                    migrationNavigator = FakeMigrationNavigator(),
                )

            useCase()

            coVerify(exactly = 1) { keystoneProposalRepository.clear() }
            assertEquals(0, router.backToCalls.size)
            assertEquals(1, router.backCalls)
        }

    private class FakeMigrationNavigator : MigrationNavigator {
        var backToReviewCalls = 0

        override fun backToMigrationReview() {
            backToReviewCalls++
        }

        override fun forwardToRestartMigration() {
            // no-op fake
        }
    }

    private class FakeNavigationRouter : NavigationRouter {
        val backToCalls = mutableListOf<KClass<*>>()
        var backCalls = 0

        override fun forward(vararg routes: Any) = Unit

        override fun replace(vararg routes: Any) = Unit

        override fun replaceAll(vararg routes: Any) = Unit

        override fun back() {
            backCalls++
        }

        override fun backTo(route: KClass<*>) {
            backToCalls += route
        }

        override fun custom(block: (NavBackStackEntry?) -> NavigationCommand?) = Unit

        override fun backToRoot() = Unit

        override fun observePipeline(): Flow<BaseNavigationCommand> = emptyFlow()
    }
}
