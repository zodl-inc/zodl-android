package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.exception.InitializeException
import cash.z.ecc.android.sdk.ledger.LedgerAccountPairing
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.LedgerPairingRepository
import co.electriccoin.zcash.ui.screen.connectledger.connected.LedgerConnectedArgs
import co.electriccoin.zcash.ui.screen.keepopen.KeepOpenArgs
import co.electriccoin.zcash.ui.screen.keepopen.KeepOpenFlow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * A pairing with no birthday imports from the chain tip and goes straight to the success screen; a
 * pairing with one has history to scan and detours through the "keep Zodl open" screen first.
 */
class CreateLedgerAccountUseCaseTest {
    private val createdAccount = Account.new(AccountUuid.new(ByteArray(16) { it.toByte() }))

    @Test
    fun aNewDeviceImportsFromTheTipAndGoesStraightToTheSuccessScreen() =
        runTest {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val accountDataSource = accountDataSource()
            val ledgerPairingRepository = pairingRepository()
            val useCase = useCase(accountDataSource, ledgerPairingRepository, navigationRouter)

            useCase(birthday = null)

            coVerify(exactly = 1) { accountDataSource.importLedgerAccount(any(), null) }
            coVerify(exactly = 1) { accountDataSource.selectAccount(createdAccount) }
            verify(exactly = 1) { ledgerPairingRepository.clear() }
            verify(exactly = 1) { navigationRouter.forward(LedgerConnectedArgs) }
        }

    @Test
    fun anActiveDeviceImportsFromItsBirthdayAndDetoursThroughKeepOpen() =
        runTest {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val accountDataSource = accountDataSource()
            val birthday = BlockHeight.new(2_500_000L)
            val useCase = useCase(accountDataSource, pairingRepository(), navigationRouter)

            useCase(birthday = birthday)

            coVerify(exactly = 1) { accountDataSource.importLedgerAccount(any(), birthday) }
            verify(exactly = 1) { navigationRouter.forward(KeepOpenArgs(KeepOpenFlow.LEDGER)) }
        }

    @Test
    fun aMissingPairingFailsInsteadOfImportingNothing() =
        runTest {
            val accountDataSource = accountDataSource()
            val ledgerPairingRepository =
                mockk<LedgerPairingRepository>(relaxed = true) {
                    every { get() } returns null
                }
            val useCase = useCase(accountDataSource, ledgerPairingRepository, mockk(relaxed = true))

            assertFailsWith<InitializeException.NoAccountLoaded> { useCase(birthday = null) }

            coVerify(exactly = 0) { accountDataSource.importLedgerAccount(any(), any()) }
        }

    private fun accountDataSource() =
        mockk<AccountDataSource>(relaxed = true) {
            coEvery { importLedgerAccount(any(), any()) } returns createdAccount
        }

    private fun pairingRepository() =
        mockk<LedgerPairingRepository>(relaxed = true) {
            every { get() } returns mockk<LedgerAccountPairing>(relaxed = true)
        }

    private fun useCase(
        accountDataSource: AccountDataSource,
        ledgerPairingRepository: LedgerPairingRepository,
        navigationRouter: NavigationRouter,
    ) = CreateLedgerAccountUseCase(
        accountDataSource = accountDataSource,
        ledgerPairingRepository = ledgerPairingRepository,
        synchronizerProvider = mockk<SynchronizerProvider>(relaxed = true),
        navigationRouter = navigationRouter,
    )
}
