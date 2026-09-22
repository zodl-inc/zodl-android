package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.exception.InitializeException
import cash.z.ecc.android.sdk.ledger.LedgerAccountPairing
import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.common.repository.LedgerPairingRepository
import co.electriccoin.zcash.ui.screen.connecthardware.HardwareWalletEnrollment
import com.keystone.module.ZcashAccount
import com.keystone.module.ZcashAccounts
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The single vendor branch of the shared enrollment flow: a Keystone enrollment parses its UR and
 * goes through the Keystone import, a Ledger enrollment goes straight to the Ledger one, and
 * neither is ready when what it needs is gone.
 */
class CreateHardwareWalletAccountUseCaseTest {
    private val birthday = BlockHeight.new(2_500_000L)

    @Test
    fun aKeystoneEnrollmentImportsThroughTheKeystoneUseCase() =
        runTest {
            val account = mockk<ZcashAccount>()
            val accounts = mockk<ZcashAccounts> { every { this@mockk.accounts } returns listOf(account) }
            val createKeystoneAccount = mockk<CreateKeystoneAccountUseCase>(relaxed = true)
            val createLedgerAccount = mockk<CreateLedgerAccountUseCase>(relaxed = true)
            val useCase =
                useCase(
                    accountsForUr = accounts,
                    createKeystoneAccount = createKeystoneAccount,
                    createLedgerAccount = createLedgerAccount,
                )

            useCase(HardwareWalletEnrollment.Keystone(UR), birthday)

            coVerify(exactly = 1) { createKeystoneAccount.invoke(accounts, account, birthday) }
            coVerify(exactly = 0) { createLedgerAccount.invoke(any()) }
        }

    @Test
    fun aLedgerEnrollmentImportsThroughTheLedgerUseCase() =
        runTest {
            val createKeystoneAccount = mockk<CreateKeystoneAccountUseCase>(relaxed = true)
            val createLedgerAccount = mockk<CreateLedgerAccountUseCase>(relaxed = true)
            val useCase =
                useCase(
                    createKeystoneAccount = createKeystoneAccount,
                    createLedgerAccount = createLedgerAccount,
                )

            useCase(HardwareWalletEnrollment.Ledger, birthday)

            coVerify(exactly = 1) { createLedgerAccount.invoke(birthday) }
            coVerify(exactly = 0) { createKeystoneAccount.invoke(any(), any(), any()) }
        }

    @Test
    fun aKeystoneUrWithNoAccountsFailsInsteadOfImportingNothing() =
        runTest {
            val accounts = mockk<ZcashAccounts> { every { this@mockk.accounts } returns emptyList() }
            val useCase = useCase(accountsForUr = accounts)

            assertFailsWith<InitializeException.NoAccountLoaded> {
                useCase(HardwareWalletEnrollment.Keystone(UR), birthday = null)
            }
        }

    @Test
    fun readinessFollowsWhatEachVendorNeeds() {
        val withAccounts = mockk<ZcashAccounts> { every { accounts } returns listOf(mockk()) }
        val withoutAccounts = mockk<ZcashAccounts> { every { accounts } returns emptyList() }

        assertTrue(useCase(accountsForUr = withAccounts).isReady(HardwareWalletEnrollment.Keystone(UR)))
        assertFalse(useCase(accountsForUr = withoutAccounts).isReady(HardwareWalletEnrollment.Keystone(UR)))
        assertFalse(
            useCase(parseThrows = true).isReady(HardwareWalletEnrollment.Keystone(UR))
        )

        assertTrue(useCase(pairing = mockk(relaxed = true)).isReady(HardwareWalletEnrollment.Ledger))
        assertFalse(useCase(pairing = null).isReady(HardwareWalletEnrollment.Ledger))
    }

    private fun useCase(
        accountsForUr: ZcashAccounts = mockk { every { accounts } returns listOf(mockk()) },
        parseThrows: Boolean = false,
        pairing: LedgerAccountPairing? = mockk(relaxed = true),
        createKeystoneAccount: CreateKeystoneAccountUseCase = mockk(relaxed = true),
        createLedgerAccount: CreateLedgerAccountUseCase = mockk(relaxed = true),
    ) = CreateHardwareWalletAccountUseCase(
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
        createLedgerAccount = createLedgerAccount,
        ledgerPairingRepository =
            mockk<LedgerPairingRepository> {
                every { get() } returns pairing
            },
    )
}

private const val UR = "ur:zcash-accounts/fixture"
