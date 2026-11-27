package co.electriccoin.zcash.ui.common.usecase

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.exception.InitializeException
import cash.z.ecc.android.sdk.model.AccountKeyOverlap
import cash.z.ecc.android.sdk.model.UnifiedFullViewingKey
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.model.ufvk.UfvkFixtures
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.PendingViewOnlyImport
import co.electriccoin.zcash.ui.common.repository.ViewOnlyPendingImportRepositoryImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * A submitted key becomes the pending import only when it is importable and the SDK finds no account
 * sharing a key component with it. Every overlap the SDK reports, an upgrade of an existing account
 * included, and a key the SDK reports as colliding with several accounts count as already imported.
 * When the lookup fails, or the synchronizer or the lookup does not answer in time, the check is left
 * to the import itself.
 */
class SubmitViewOnlyKeyUseCaseTest {
    private val repository = ViewOnlyPendingImportRepositoryImpl()

    private val key = UfvkFixtures.MAINNET_FULL

    @Test
    fun aKeyNoAccountSharesAComponentWithBecomesThePendingImport() =
        runTest {
            val synchronizer = synchronizerFinding(null)

            val result = useCase(synchronizer).submit("  input ")

            assertEquals(SubmitViewOnlyKeyResult.Accepted, result)
            assertEquals(PendingViewOnlyImport(ufvk = key, name = ""), repository.get())
            coVerify { synchronizer.findAccountSharingKeyComponents(UnifiedFullViewingKey(key)) }
        }

    @Test
    fun everyKindOfOverlapIsAlreadyImported() =
        runTest {
            AccountKeyOverlap.Kind.entries.forEach { kind ->
                repository.clear()

                val result = useCase(synchronizerFinding(AccountKeyOverlap(mockk(), kind))).submit("input")

                assertEquals(SubmitViewOnlyKeyResult.AlreadyImported, result, kind.name)
                assertNull(repository.get(), kind.name)
            }
        }

    @Test
    fun aKeyThatWouldUpgradeAnExistingAccountIsAlreadyImported() =
        runTest {
            val overlap = AccountKeyOverlap(mockk(), AccountKeyOverlap.Kind.WOULD_UPGRADE)

            val result = useCase(synchronizerFinding(overlap)).submit("input")

            assertEquals(SubmitViewOnlyKeyResult.AlreadyImported, result)
            assertNull(repository.get())
        }

    @Test
    fun aKeyCollidingWithSeveralAccountsIsAlreadyImported() =
        runTest {
            val synchronizer =
                mockk<Synchronizer> {
                    coEvery { findAccountSharingKeyComponents(any()) } throws
                        InitializeException.ImportAccountCollisionException(null)
                }

            val result = useCase(synchronizer).submit("input")

            assertEquals(SubmitViewOnlyKeyResult.AlreadyImported, result)
            assertNull(repository.get())
        }

    @Test
    fun aFailedLookupLeavesTheCheckToTheImport() =
        runTest {
            val synchronizer =
                mockk<Synchronizer> {
                    coEvery { findAccountSharingKeyComponents(any()) } throws
                        InitializeException.FindAccountSharingKeyComponentsException(IllegalStateException(key))
                }

            val result = useCase(synchronizer).submit("input")

            assertEquals(SubmitViewOnlyKeyResult.Accepted, result)
            assertEquals(PendingViewOnlyImport(ufvk = key, name = ""), repository.get())
        }

    @Test
    fun anyOtherLookupFailureLeavesTheCheckToTheImport() =
        runTest {
            val synchronizer =
                mockk<Synchronizer> {
                    coEvery { findAccountSharingKeyComponents(any()) } throws IllegalStateException()
                }

            val result = useCase(synchronizer).submit("input")

            assertEquals(SubmitViewOnlyKeyResult.Accepted, result)
        }

    @Test
    fun aSynchronizerThatNeverLoadsLeavesTheCheckToTheImport() =
        runTest {
            val result =
                SubmitViewOnlyKeyUseCase(
                    validateUfvk = validating(key),
                    synchronizerProvider =
                        mockk<SynchronizerProvider> { coEvery { getSynchronizer() } coAnswers { awaitCancellation() } },
                    pendingImportRepository = repository,
                ).submit("input")

            assertEquals(SubmitViewOnlyKeyResult.Accepted, result)
            assertEquals(PendingViewOnlyImport(ufvk = key, name = ""), repository.get())
        }

    @Test
    fun aLookupThatNeverCompletesLeavesTheCheckToTheImport() =
        runTest {
            val synchronizer =
                mockk<Synchronizer> {
                    coEvery { findAccountSharingKeyComponents(any()) } coAnswers { awaitCancellation() }
                }

            val result = useCase(synchronizer).submit("input")

            assertEquals(SubmitViewOnlyKeyResult.Accepted, result)
            assertEquals(PendingViewOnlyImport(ufvk = key, name = ""), repository.get())
        }

    @Test
    fun aCancelledLookupIsRethrown() =
        runTest {
            val synchronizer =
                mockk<Synchronizer> {
                    coEvery { findAccountSharingKeyComponents(any()) } throws CancellationException("cancelled")
                }

            assertFailsWith<CancellationException> { useCase(synchronizer).submit("input") }
            assertNull(repository.get())
        }

    @Test
    fun anInvalidKeyIsNotLookedUpOrKept() =
        runTest {
            val synchronizer = synchronizerFinding(null)

            val result = useCase(synchronizer, validation = UfvkValidation.Invalid).submit("input")

            assertEquals(SubmitViewOnlyKeyResult.Invalid, result)
            assertNull(repository.get())
            coVerify(exactly = 0) { synchronizer.findAccountSharingKeyComponents(any()) }
        }

    @Test
    fun aKeyTheSdkFailsToReadIsReportedAndNeitherLookedUpNorKept() =
        runTest {
            val synchronizer = synchronizerFinding(null)
            val failingValidation =
                mockk<ValidateUfvkUseCase> {
                    coEvery { this@mockk.invoke(any(), any()) } throws ViewOnlyKeyCheckException()
                }
            val useCase =
                SubmitViewOnlyKeyUseCase(
                    validateUfvk = failingValidation,
                    synchronizerProvider =
                        mockk<SynchronizerProvider> { coEvery { getSynchronizer() } returns synchronizer },
                    pendingImportRepository = repository,
                )

            assertFailsWith<ViewOnlyKeyCheckException> { useCase.submit("input") }
            assertNull(repository.get())
            coVerify(exactly = 0) { synchronizer.findAccountSharingKeyComponents(any()) }
        }

    private fun synchronizerFinding(overlap: AccountKeyOverlap?) =
        mockk<Synchronizer> { coEvery { findAccountSharingKeyComponents(any()) } returns overlap }

    private fun useCase(
        synchronizer: Synchronizer,
        validation: UfvkValidation = UfvkValidation.Valid(key),
    ) = SubmitViewOnlyKeyUseCase(
        validateUfvk = mockk { coEvery { this@mockk.invoke(any(), any()) } returns validation },
        synchronizerProvider = mockk<SynchronizerProvider> { coEvery { getSynchronizer() } returns synchronizer },
        pendingImportRepository = repository,
    )

    private fun validating(ufvk: String): ValidateUfvkUseCase =
        mockk { coEvery { this@mockk.invoke(any(), any()) } returns UfvkValidation.Valid(ufvk) }

    private suspend fun SubmitViewOnlyKeyUseCase.submit(input: String) = invoke(input, ZcashNetwork.Mainnet)
}
