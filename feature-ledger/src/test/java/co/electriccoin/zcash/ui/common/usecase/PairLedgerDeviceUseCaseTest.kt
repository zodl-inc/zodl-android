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
import co.electriccoin.zcash.ui.common.repository.LedgerRepairTargetRepository
import co.electriccoin.zcash.ui.common.repository.LedgerRepairTargetRepositoryImpl
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
 * is stored with it. Pairing an account again, and pairing a second Ledger, report any other viewing
 * key as the wrong Ledger and import nothing.
 */
class PairLedgerDeviceUseCaseTest {
    private val bindingProvider = mockk<LedgerAccountBindingProvider>(relaxed = true)

    private val firstAccount = Zip32AccountIndex.new(0)

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

            val result = useCase(pairing, repository, existing = emptyList()).invoke(device, firstAccount)

            assertTrue(result is PairLedgerDeviceResult.Paired)
            assertSame(pairing, repository.get())
        }

    @Test
    fun aDeviceWhoseKeyIsAlreadyImportedReportsTheExistingAccountAndStashesNothing() =
        runTest {
            val pairing = pairing(ufvk = "ufvk-known")
            val alreadyAdded = ledgerAccount(ufvk = "ufvk-known", bound = true)
            val repository = LedgerPairingRepositoryImpl()

            val result = useCase(pairing, repository, existing = listOf(alreadyAdded)).invoke(device, firstAccount)

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

            val result = useCase(pairing, repository, existing = listOf(unbound)).invoke(device, firstAccount)

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

            val result = useCase(pairing, repository, existing = listOf(keystone)).invoke(device, firstAccount)

            assertTrue(result is PairLedgerDeviceResult.Paired)
        }

    @Test
    fun aSecondLedgerIsTheWrongLedgerAndStashesNothing() =
        runTest {
            val pairing = pairing(ufvk = "ufvk-second")
            val first = ledgerAccount(ufvk = "ufvk-first", bound = true)
            val repository = LedgerPairingRepositoryImpl()

            val result = useCase(pairing, repository, existing = listOf(first)).invoke(device, firstAccount)

            assertEquals(PairLedgerDeviceResult.WrongLedger, result)
            assertNull(repository.get())
            coVerify(exactly = 0) { bindingProvider.save(any(), any(), any()) }
        }

    @Test
    fun pairingTheTargetAgainWithItsOwnLedgerRebindsItAndClearsTheTarget() =
        runTest {
            val pairing = pairing(ufvk = "ufvk-known")
            val target = ledgerAccount(ufvk = "ufvk-known", bound = false)
            val repository = LedgerPairingRepositoryImpl()
            val repairTarget = repairTarget(target)

            val result =
                useCase(pairing, repository, existing = listOf(target), repairTarget = repairTarget)
                    .invoke(device, firstAccount)

            assertEquals(PairLedgerDeviceResult.Rebound(target), result)
            assertNull(repository.get())
            assertNull(repairTarget.get())
            val accountUuid = target.sdkAccount.accountUuid
            coVerify(exactly = 1) {
                bindingProvider.save(
                    accountUuid = accountUuid,
                    deviceIdentityEncoding = IDENTITY,
                    zip32AccountIndex = 0L,
                )
            }
        }

    @Test
    fun pairingABoundTargetAgainReplacesItsBindingBecauseTheStoredOneMayBeUnusable() =
        runTest {
            val pairing = pairing(ufvk = "ufvk-known")
            val target = ledgerAccount(ufvk = "ufvk-known", bound = true)
            val repository = LedgerPairingRepositoryImpl()

            val result =
                useCase(pairing, repository, existing = listOf(target), repairTarget = repairTarget(target))
                    .invoke(device, firstAccount)

            assertEquals(PairLedgerDeviceResult.Rebound(target), result)
            assertNull(repository.get())
            val accountUuid = target.sdkAccount.accountUuid
            coVerify(exactly = 1) {
                bindingProvider.save(
                    accountUuid = accountUuid,
                    deviceIdentityEncoding = IDENTITY,
                    zip32AccountIndex = 0L,
                )
            }
        }

    @Test
    fun pairingTheTargetAgainWithAnotherLedgerIsTheWrongLedgerAndImportsNothing() =
        runTest {
            val target = ledgerAccount(ufvk = "ufvk-known", bound = false)
            listOf(listOf(target), emptyList()).forEach { existing ->
                val repository = LedgerPairingRepositoryImpl()
                val repairTarget = repairTarget(target)

                val result =
                    useCase(pairing(ufvk = "ufvk-other"), repository, existing, repairTarget)
                        .invoke(device, firstAccount)

                assertEquals(PairLedgerDeviceResult.WrongLedger, result, "existing=${existing.size}")
                assertNull(repository.get())
                assertEquals(target.sdkAccount.accountUuid, repairTarget.get(), "Try again keeps the target")
            }
            coVerify(exactly = 0) { bindingProvider.save(any(), any(), any()) }
        }

    @Test
    fun theAccountTheUserChoseIsTheOneExported() =
        runTest {
            val dataSource = dataSource(pairing(ufvk = "ufvk-new"))

            useCase(dataSource, LedgerPairingRepositoryImpl(), existing = emptyList())
                .invoke(device, Zip32AccountIndex.new(7))

            coVerify(exactly = 1) { dataSource.pair(device, Zip32AccountIndex.new(7)) }
        }

    /**
     * Pairing an account again exports whichever account the user chose; an index whose viewing key
     * is not the target's is the wrong Ledger, as for any other device.
     */
    @Test
    fun pairingAnAccountAgainExportsTheChosenAccountAndAMismatchIsTheWrongLedger() =
        runTest {
            val target = ledgerAccount(ufvk = "ufvk-known", bound = false, zip32AccountIndex = null)
            val dataSource = dataSource(pairing(ufvk = "ufvk-index-4"))
            val repository = LedgerPairingRepositoryImpl()

            val result =
                useCase(dataSource, repository, listOf(target), repairTarget(target))
                    .invoke(device, Zip32AccountIndex.new(4))

            coVerify(exactly = 1) { dataSource.pair(device, Zip32AccountIndex.new(4)) }
            assertEquals(PairLedgerDeviceResult.WrongLedger, result)
            assertNull(repository.get())
            coVerify(exactly = 0) { bindingProvider.save(any(), any(), any()) }
        }

    /**
     * The binding stored when an account is paired again carries the index the device exported, not
     * the one the account had before.
     */
    @Test
    fun pairingAnAccountAgainStoresTheIndexItWasPairedAt() =
        runTest {
            val target = ledgerAccount(ufvk = "ufvk-known", bound = false, zip32AccountIndex = null)
            val dataSource = dataSource(pairing(ufvk = "ufvk-known", zip32AccountIndex = 4L))

            val result =
                useCase(dataSource, LedgerPairingRepositoryImpl(), listOf(target), repairTarget(target))
                    .invoke(device, Zip32AccountIndex.new(4))

            assertEquals(PairLedgerDeviceResult.Rebound(target), result)
            val accountUuid = target.sdkAccount.accountUuid
            coVerify(exactly = 1) {
                bindingProvider.save(
                    accountUuid = accountUuid,
                    deviceIdentityEncoding = IDENTITY,
                    zip32AccountIndex = 4L,
                )
            }
        }

    /**
     * A viewing key that belongs to a Ledger account other than the one being paired again is still
     * the wrong Ledger; neither account's binding is touched.
     */
    @Test
    fun pairingAnAccountAgainWithAnotherAccountsKeyIsTheWrongLedger() =
        runTest {
            val target = ledgerAccount(ufvk = "ufvk-target", bound = false)
            val other = ledgerAccount(ufvk = "ufvk-other", bound = true)
            val repairTarget = repairTarget(target)
            val existing = listOf(target, other)

            val result =
                useCase(pairing(ufvk = "ufvk-other"), LedgerPairingRepositoryImpl(), existing, repairTarget)
                    .invoke(device, Zip32AccountIndex.new(1))

            assertEquals(PairLedgerDeviceResult.WrongLedger, result)
            assertEquals(target.sdkAccount.accountUuid, repairTarget.get())
            coVerify(exactly = 0) { bindingProvider.save(any(), any(), any()) }
        }

    @Test
    fun aFreshPairingIsStashedWithTheIndexItWasPairedAt() =
        runTest {
            val pairing = pairing(ufvk = "ufvk-new", zip32AccountIndex = 7L)
            val repository = LedgerPairingRepositoryImpl()

            useCase(pairing, repository, existing = emptyList()).invoke(device, Zip32AccountIndex.new(7))

            assertEquals(Zip32AccountIndex.new(7), repository.get()?.binding?.zip32AccountIndex)
        }

    @Test
    fun anEarlierPairingIsReplacedRatherThanKept() =
        runTest {
            val repository = LedgerPairingRepositoryImpl()
            repository.set(pairing(ufvk = "ufvk-stale"))
            val fresh = pairing(ufvk = "ufvk-fresh")

            useCase(fresh, repository, existing = emptyList()).invoke(device, firstAccount)

            assertSame(fresh, repository.get())
        }

    private fun useCase(
        pairing: LedgerAccountPairing,
        repository: LedgerPairingRepository,
        existing: List<WalletAccount>,
        repairTarget: LedgerRepairTargetRepository = LedgerRepairTargetRepositoryImpl(),
    ) = useCase(dataSource(pairing), repository, existing, repairTarget)

    private fun useCase(
        dataSource: LedgerDeviceDataSource,
        repository: LedgerPairingRepository,
        existing: List<WalletAccount>,
        repairTarget: LedgerRepairTargetRepository = LedgerRepairTargetRepositoryImpl(),
    ) = PairLedgerDeviceUseCase(
        ledgerDeviceDataSource = dataSource,
        ledgerPairingRepository = repository,
        ledgerRepairTargetRepository = repairTarget,
        accountDataSource =
            mockk<AccountDataSource> {
                coEvery { getAllAccounts() } returns existing
            },
        ledgerAccountBindingProvider = bindingProvider,
    )

    private fun dataSource(pairing: LedgerAccountPairing) =
        mockk<LedgerDeviceDataSource> {
            coEvery { pair(any(), any()) } returns pairing
        }

    private fun pairing(
        ufvk: String,
        zip32AccountIndex: Long = 0L,
    ) =
        mockk<LedgerAccountPairing> {
            every { this@mockk.ufvk } returns UnifiedFullViewingKey(ufvk)
            every { binding } returns
                LedgerAccountBinding(
                    deviceIdentity = mockk<LedgerDeviceIdentity> { every { encoding } returns IDENTITY },
                    zip32AccountIndex = Zip32AccountIndex.new(zip32AccountIndex),
                )
        }

    private fun repairTarget(account: LedgerAccount) =
        LedgerRepairTargetRepositoryImpl().apply { set(account.sdkAccount.accountUuid, account.zip32AccountIndex) }

    /**
     * Each viewing key gets its own UUID, so accounts with different keys are different accounts.
     */
    private fun sdkAccount(ufvk: String) =
        mockk<Account> {
            every { accountUuid } returns AccountUuid.new(ByteArray(16) { (it + ufvk.hashCode()).toByte() })
            every { this@mockk.ufvk } returns ufvk
        }

    private fun ledgerAccount(
        ufvk: String,
        bound: Boolean,
        zip32AccountIndex: Zip32AccountIndex? = Zip32AccountIndex.new(0L),
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
            zip32AccountIndex = zip32AccountIndex,
        )
}

private const val IDENTITY = "tpk0-identity"
