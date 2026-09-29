package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.ledger.LedgerAccountBinding
import cash.z.ecc.android.sdk.ledger.LedgerAccountPairing
import cash.z.ecc.android.sdk.ledger.LedgerBluetoothDevice
import cash.z.ecc.android.sdk.ledger.LedgerDeviceIdentity
import cash.z.ecc.android.sdk.ledger.LedgerDeviceModel
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.UnifiedFullViewingKey
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.LedgerDeviceDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.provider.LedgerAccountBindingProvider
import co.electriccoin.zcash.ui.common.repository.LedgerPairingRepository
import co.electriccoin.zcash.ui.common.repository.LedgerPairingRepositoryImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pairing stashes the result for the birthday screens, unless the wallet already holds an account
 * for that viewing key — in which case nothing is stashed, and the caller is told to offer the
 * existing account instead, or, when that account has lost its Ledger binding, the pairing's binding
 * is stored with it.
 */
class PairLedgerDeviceUseCaseTest {
    private val bindingProvider = mockk<LedgerAccountBindingProvider>(relaxed = true)

    private val device =
        LedgerBluetoothDevice(
            model = LedgerDeviceModel.NANO_X,
            name = "Ledger Device",
            identifier = "AA:BB",
            rssi = -40,
        )

    @Test
    fun aFreshDeviceHasItsPairingStashedForTheBirthdayScreens() =
        runTest {
            val pairing = pairing(ufvk = "ufvk-new")
            val repository = LedgerPairingRepositoryImpl()

            val result = useCase(pairing, repository, existing = emptyList()).invoke(device)

            assertTrue(result is PairLedgerDeviceResult.Paired)
            assertSame(pairing, repository.get())
        }

    @Test
    fun aDeviceWhoseKeyIsAlreadyImportedReportsTheExistingAccountAndStashesNothing() =
        runTest {
            val pairing = pairing(ufvk = "ufvk-known")
            val alreadyAdded = ledgerAccount(ufvk = "ufvk-known", bound = true)
            val repository = LedgerPairingRepositoryImpl()

            val result = useCase(pairing, repository, existing = listOf(alreadyAdded)).invoke(device)

            assertEquals(PairLedgerDeviceResult.AlreadyAdded(alreadyAdded), result)
            assertNull(repository.get())
            coVerify(exactly = 0) { bindingProvider.save(any(), any(), any()) }
        }

    @Test
    fun anImportedAccountWithoutABindingIsReboundToTheDeviceAndStashesNothing() =
        runTest {
            val pairing = pairing(ufvk = "ufvk-known")
            val unbound = ledgerAccount(ufvk = "ufvk-known", bound = false)
            val repository = LedgerPairingRepositoryImpl()

            val result = useCase(pairing, repository, existing = listOf(unbound)).invoke(device)

            assertEquals(PairLedgerDeviceResult.Rebound(unbound), result)
            assertNull(repository.get())
            val accountUuid = unbound.sdkAccount.accountUuid
            coVerify(exactly = 1) {
                bindingProvider.save(
                    accountUuid = accountUuid,
                    deviceIdentityEncoding = IDENTITY,
                    zip32AccountIndex = 0L,
                )
            }
        }

    @Test
    fun aKeystoneAccountWithTheSameKeyIsNotADuplicateLedger() =
        runTest {
            val pairing = pairing(ufvk = "ufvk-shared")
            val keystone =
                KeystoneAccount(
                    sdkAccount = sdkAccount(ufvk = "ufvk-shared"),
                    unifiedAddress = "u",
                    transparentAddress = "t",
                    orchardBalance = null,
                    ironwoodBalance = null,
                    transparentBalance = null,
                    isSelected = false,
                )
            val repository = LedgerPairingRepositoryImpl()

            val result = useCase(pairing, repository, existing = listOf(keystone)).invoke(device)

            assertTrue(result is PairLedgerDeviceResult.Paired)
        }

    @Test
    fun anEarlierPairingIsReplacedRatherThanKept() =
        runTest {
            val repository = LedgerPairingRepositoryImpl()
            repository.set(pairing(ufvk = "ufvk-stale"))
            val fresh = pairing(ufvk = "ufvk-fresh")

            useCase(fresh, repository, existing = emptyList()).invoke(device)

            assertSame(fresh, repository.get())
        }

    private fun useCase(
        pairing: LedgerAccountPairing,
        repository: LedgerPairingRepository,
        existing: List<WalletAccount>,
    ) = PairLedgerDeviceUseCase(
        ledgerDeviceDataSource =
            mockk<LedgerDeviceDataSource> {
                coEvery { pair(any(), any()) } returns pairing
            },
        ledgerPairingRepository = repository,
        accountDataSource =
            mockk<AccountDataSource> {
                coEvery { getAllAccounts() } returns existing
            },
        ledgerAccountBindingProvider = bindingProvider,
    )

    private fun pairing(ufvk: String) =
        mockk<LedgerAccountPairing> {
            every { this@mockk.ufvk } returns UnifiedFullViewingKey(ufvk)
            every { binding } returns
                LedgerAccountBinding(
                    deviceIdentity = mockk<LedgerDeviceIdentity> { every { encoding } returns IDENTITY },
                    zip32AccountIndex = Zip32AccountIndex.new(0L),
                )
        }

    private fun sdkAccount(ufvk: String) =
        mockk<Account> {
            every { accountUuid } returns AccountUuid.new(ByteArray(16) { it.toByte() })
            every { this@mockk.ufvk } returns ufvk
        }

    private fun ledgerAccount(
        ufvk: String,
        bound: Boolean,
    ) =
        LedgerAccount(
            sdkAccount = sdkAccount(ufvk),
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = false,
            deviceIdentity = if (bound) IDENTITY else null,
            zip32AccountIndex = Zip32AccountIndex.new(0L),
        )
}

private const val IDENTITY = "tpk0-identity"
