package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.exception.InitializeException
import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.ledger.LedgerAccountImporter
import co.electriccoin.zcash.ui.common.model.LedgerPairingMissingException
import co.electriccoin.zcash.ui.screen.connecthw.HWWalletEnrollment
import com.keystone.module.ZcashAccount
import com.keystone.module.ZcashAccounts
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The single vendor branch of the shared enrollment flow: a Keystone enrollment parses its UR and
 * goes through the Keystone import, a Ledger enrollment goes straight to the Ledger one and returns
 * to the root when its pairing went missing, and neither is ready when what it needs is gone.
 */
class CreateHWWalletAccountUseCaseTest {
    private val birthday = BlockHeight.new(2_500_000L)

    @Test
    fun aKeystoneEnrollmentImportsThroughTheKeystoneUseCase() =
        runTest {
            val account = mockk<ZcashAccount>()
            val accounts = mockk<ZcashAccounts> { every { this@mockk.accounts } returns listOf(account) }
            val createKeystoneAccount = mockk<CreateKeystoneAccountUseCase>(relaxed = true)
            val ledgerAccountImporter = mockk<LedgerAccountImporter>(relaxed = true)
            val useCase =
                useCase(
                    accountsForUr = accounts,
                    createKeystoneAccount = createKeystoneAccount,
                    ledgerAccountImporter = ledgerAccountImporter,
                )

            useCase(HWWalletEnrollment.Keystone(UR), birthday)

            coVerify(exactly = 1) { createKeystoneAccount.invoke(accounts, account, birthday) }
            coVerify(exactly = 0) { ledgerAccountImporter.importAccount(any()) }
        }

    @Test
    fun aLedgerEnrollmentImportsThroughTheLedgerUseCase() =
        runTest {
            val createKeystoneAccount = mockk<CreateKeystoneAccountUseCase>(relaxed = true)
            val ledgerAccountImporter = mockk<LedgerAccountImporter>(relaxed = true)
            val useCase =
                useCase(
                    createKeystoneAccount = createKeystoneAccount,
                    ledgerAccountImporter = ledgerAccountImporter,
                )

            useCase(HWWalletEnrollment.Ledger, birthday)

            coVerify(exactly = 1) { ledgerAccountImporter.importAccount(birthday) }
            coVerify(exactly = 0) { createKeystoneAccount.invoke(any(), any(), any()) }
        }

    @Test
    fun aLedgerPairingLostBeforeTheImportReturnsToTheRootInsteadOfFailing() =
        runTest {
            val ledgerAccountImporter =
                mockk<LedgerAccountImporter>(relaxed = true) {
                    coEvery { importAccount(any()) } throws LedgerPairingMissingException()
                }
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val useCase = useCase(ledgerAccountImporter = ledgerAccountImporter, navigationRouter = navigationRouter)

            useCase(HWWalletEnrollment.Ledger, birthday)

            verify(exactly = 1) { navigationRouter.backToRoot() }
        }

    @Test
    fun aSuccessfulLedgerImportStaysOnTheFlow() =
        runTest {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)

            useCase(navigationRouter = navigationRouter)(HWWalletEnrollment.Ledger, birthday)

            verify(exactly = 0) { navigationRouter.backToRoot() }
        }

    @Test
    fun aKeystoneUrWithNoAccountsFailsInsteadOfImportingNothing() =
        runTest {
            val accounts = mockk<ZcashAccounts> { every { this@mockk.accounts } returns emptyList() }
            val useCase = useCase(accountsForUr = accounts)

            assertFailsWith<InitializeException.NoAccountLoaded> {
                useCase(HWWalletEnrollment.Keystone(UR), birthday = null)
            }
        }

    @Test
    fun readinessFollowsWhatEachVendorNeeds() {
        val withAccounts = mockk<ZcashAccounts> { every { accounts } returns listOf(mockk()) }
        val withoutAccounts = mockk<ZcashAccounts> { every { accounts } returns emptyList() }

        assertTrue(useCase(accountsForUr = withAccounts).isReady(HWWalletEnrollment.Keystone(UR)))
        assertFalse(useCase(accountsForUr = withoutAccounts).isReady(HWWalletEnrollment.Keystone(UR)))
        assertFalse(
            useCase(parseThrows = true).isReady(HWWalletEnrollment.Keystone(UR))
        )

        assertTrue(useCase(hasPendingPairing = true).isReady(HWWalletEnrollment.Ledger))
        assertFalse(useCase(hasPendingPairing = false).isReady(HWWalletEnrollment.Ledger))
    }

    private fun useCase(
        accountsForUr: ZcashAccounts = mockk { every { accounts } returns listOf(mockk()) },
        parseThrows: Boolean = false,
        hasPendingPairing: Boolean = true,
        createKeystoneAccount: CreateKeystoneAccountUseCase = mockk(relaxed = true),
        ledgerAccountImporter: LedgerAccountImporter = mockk(relaxed = true),
        navigationRouter: NavigationRouter = mockk(relaxed = true),
    ) = CreateHWWalletAccountUseCase(
        parseKeystoneUrToZashiAccounts =
            mockk {
                if (parseThrows) {
                    every { this@mockk.invoke(any()) } throws
                        InvalidKeystoneSignInQRException(RuntimeException("bad ur"))
                } else {
                    every { this@mockk.invoke(any()) } returns accountsForUr
                }
            },
        createKeystoneAccount = createKeystoneAccount,
        ledgerAccountImporter =
            ledgerAccountImporter.also {
                every { it.hasPendingPairing() } returns hasPendingPairing
            },
        navigationRouter = navigationRouter,
    )
}

private const val UR = "ur:zcash-accounts/fixture"
