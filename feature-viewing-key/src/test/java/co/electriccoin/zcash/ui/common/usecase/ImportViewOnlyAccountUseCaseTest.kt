package co.electriccoin.zcash.ui.common.usecase

import androidx.compose.ui.graphics.Color
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.BlockHeight
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccountAlreadyExistsException
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccountImportException
import co.electriccoin.zcash.ui.common.model.ViewOnlyPendingImportMissingException
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import co.electriccoin.zcash.ui.common.repository.PendingViewOnlyImport
import co.electriccoin.zcash.ui.common.repository.ViewOnlyPendingImportRepositoryImpl
import co.electriccoin.zcash.ui.screen.keepopen.KeepOpenArgs
import co.electriccoin.zcash.ui.screen.keepopen.KeepOpenFlow
import co.electriccoin.zcash.ui.screen.viewingkey.alreadyimported.AlreadyImportedVKArgs
import co.electriccoin.zcash.ui.screen.viewingkey.imported.ImportedVKArgs
import co.electriccoin.zcash.ui.screen.viewingkey.name.NameVKArgs
import co.electriccoin.zcash.viewingkey.ViewOnlyAccountImporterImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The import imports the pending key under its name, selects the new account, forgets the key and
 * restarts the synchronizer, then shows the success screen, through the keep-open screen when there
 * is history to scan. A key the wallet already holds ends on the "already imported" sheet; nothing
 * pending fails without touching the wallet; an SDK failure keeps the key so the import can be
 * retried. A caller cancelled mid-import still gets the account selected, but no navigation. A picked
 * colour is stored for the new account once the wallet lists it, and a store that hangs is given up
 * without holding back the import. The birthday is the one the pending import holds. The importer
 * ui-lib's birthday screens call stores that birthday with the pending import and opens the naming
 * step, which imports.
 */
class ImportViewOnlyAccountUseCaseTest {
    private val repository = ViewOnlyPendingImportRepositoryImpl()

    private val created = mockk<Account> { every { accountUuid } returns ViewOnlyAccountFixtures.viewOnlyUuid }

    private val accountDataSource =
        mockk<AccountDataSource>(relaxed = true) {
            coEvery { importViewOnlyAccount(any(), any(), any()) } returns created
        }

    private val synchronizerProvider = mockk<SynchronizerProvider>(relaxed = true)

    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)

    private val metadataRepository = mockk<MetadataRepository>(relaxed = true)

    private val useCase =
        ImportViewOnlyAccountUseCase(
            repository,
            accountDataSource,
            synchronizerProvider,
            navigationRouter,
            metadataRepository
        )

    @Test
    fun anAccountWithoutHistoryGoesStraightToTheSuccessScreen() =
        runTest {
            repository.set(PendingViewOnlyImport(ufvk = UFVK, name = "Savings"))

            useCase()

            coVerify(exactly = 1) { accountDataSource.importViewOnlyAccount(UFVK, "Savings", null) }
            coVerify(exactly = 1) { accountDataSource.selectAccount(created) }
            verify(exactly = 1) { synchronizerProvider.resetSynchronizer() }
            verify(exactly = 1) { navigationRouter.forward(ImportedVKArgs) }
            assertNull(repository.get())
            coVerify(exactly = 0) { metadataRepository.setAccountColor(any(), any()) }
        }

    @Test
    fun aPickedColorIsStoredForTheNewAccountBeforeTheSynchronizerRestarts() =
        runTest {
            val account = ViewOnlyAccountFixtures.viewOnly()
            every { accountDataSource.allAccounts } returns MutableStateFlow(listOf(account))
            repository.set(PendingViewOnlyImport(ufvk = UFVK, name = "Savings", color = Color(PICKED)))

            useCase()

            coVerifyOrder {
                accountDataSource.selectAccount(created)
                metadataRepository.setAccountColor(account, Color(PICKED))
                synchronizerProvider.resetSynchronizer()
            }
            verify(exactly = 1) { navigationRouter.forward(ImportedVKArgs) }
        }

    @Test
    fun aColorForAnAccountTheWalletNeverListsIsDroppedAndTheImportGoesOn() =
        runTest {
            every { accountDataSource.allAccounts } returns MutableStateFlow(emptyList())
            repository.set(PendingViewOnlyImport(ufvk = UFVK, name = "Savings", color = Color(PICKED)))

            useCase()

            coVerify(exactly = 0) { metadataRepository.setAccountColor(any(), any()) }
            verify(exactly = 1) { synchronizerProvider.resetSynchronizer() }
            verify(exactly = 1) { navigationRouter.forward(ImportedVKArgs) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aColorWriteThatNeverReturnsStillLetsTheImportFinish() =
        runTest {
            val account = ViewOnlyAccountFixtures.viewOnly()
            every { accountDataSource.allAccounts } returns MutableStateFlow(listOf(account))
            coEvery { metadataRepository.setAccountColor(any(), any()) } coAnswers { awaitCancellation() }
            repository.set(PendingViewOnlyImport(ufvk = UFVK, name = "Savings", color = Color(PICKED)))

            useCase()

            coVerify(exactly = 1) { metadataRepository.setAccountColor(account, Color(PICKED)) }
            coVerify(exactly = 1) { accountDataSource.selectAccount(created) }
            verify(exactly = 1) { synchronizerProvider.resetSynchronizer() }
            verify(exactly = 1) { navigationRouter.forward(ImportedVKArgs) }
            assertNull(repository.get())
            assertEquals(COLOR_BUDGET_MILLIS, currentTime)
        }

    @Test
    fun anAccountWithHistoryGoesThroughTheKeepOpenScreen() =
        runTest {
            val birthday = BlockHeight.new(2_000_000L)
            repository.set(PendingViewOnlyImport(ufvk = UFVK, name = "Savings", birthday = birthday))

            useCase()

            coVerify(exactly = 1) { accountDataSource.importViewOnlyAccount(UFVK, "Savings", birthday) }
            verify(exactly = 1) { navigationRouter.forward(KeepOpenArgs(KeepOpenFlow.VIEW_ONLY)) }
            assertNull(repository.get())
        }

    @Test
    fun aKeyTheWalletAlreadyHoldsEndsOnTheAlreadyImportedSheet() =
        runTest {
            repository.set(PendingViewOnlyImport(ufvk = UFVK, name = "Savings"))
            coEvery { accountDataSource.importViewOnlyAccount(any(), any(), any()) } throws
                ViewOnlyAccountAlreadyExistsException()

            useCase()

            verify(exactly = 1) { navigationRouter.forward(AlreadyImportedVKArgs) }
            coVerify(exactly = 0) { accountDataSource.selectAccount(any<Account>()) }
            verify(exactly = 0) { synchronizerProvider.resetSynchronizer() }
            assertNull(repository.get())
        }

    @Test
    fun anSdkFailureKeepsTheKeySoTheImportCanBeRetried() =
        runTest {
            repository.set(PendingViewOnlyImport(ufvk = UFVK, name = "Savings"))
            coEvery { accountDataSource.importViewOnlyAccount(any(), any(), any()) } throws
                ViewOnlyAccountImportException()

            val error = assertFailsWith<ViewOnlyAccountImportException> { useCase() }

            assertFalse(error.stackTraceToString().contains("uview"))
            assertEquals(UFVK, repository.get()?.ufvk)
            verify(exactly = 0) { navigationRouter.forward(any()) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aSecondCallWhileTheFirstImportsReturnsWithoutImportingAgain() =
        runTest {
            repository.set(PendingViewOnlyImport(ufvk = UFVK, name = "Savings"))
            val imported = CompletableDeferred<Account>()
            coEvery { accountDataSource.importViewOnlyAccount(any(), any(), any()) } coAnswers { imported.await() }

            val first = async { useCase() }
            val second = async { useCase() }
            runCurrent()
            imported.complete(created)
            first.await()
            second.await()

            coVerify(exactly = 1) { accountDataSource.importViewOnlyAccount(any(), any(), any()) }
            coVerify(exactly = 1) { accountDataSource.selectAccount(created) }
            verify(exactly = 1) { synchronizerProvider.resetSynchronizer() }
            verify(exactly = 1) { navigationRouter.forward(ImportedVKArgs) }
            verify(exactly = 0) { navigationRouter.forward(AlreadyImportedVKArgs) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aCancelledCallerStillSelectsTheImportedAccountButDoesNotNavigate() =
        runTest {
            repository.set(PendingViewOnlyImport(ufvk = UFVK, name = "Savings"))
            val imported = CompletableDeferred<Account>()
            coEvery { accountDataSource.importViewOnlyAccount(any(), any(), any()) } coAnswers { imported.await() }

            val caller = launch { useCase() }
            runCurrent()
            caller.cancel()
            imported.complete(created)
            caller.join()

            coVerify(exactly = 1) { accountDataSource.selectAccount(created) }
            verify(exactly = 1) { synchronizerProvider.resetSynchronizer() }
            assertNull(repository.get())
            verify(exactly = 0) { navigationRouter.forward(any()) }
        }

    @Test
    fun aSecondCallAfterAFailedImportRetriesIt() =
        runTest {
            repository.set(PendingViewOnlyImport(ufvk = UFVK, name = "Savings"))
            coEvery { accountDataSource.importViewOnlyAccount(any(), any(), any()) } throws
                ViewOnlyAccountImportException() andThen created

            assertFailsWith<ViewOnlyAccountImportException> { useCase() }
            useCase()

            coVerify(exactly = 2) { accountDataSource.importViewOnlyAccount(UFVK, "Savings", null) }
            verify(exactly = 1) { navigationRouter.forward(ImportedVKArgs) }
            assertNull(repository.get())
        }

    @Test
    fun nothingPendingFailsWithoutTouchingTheWallet() =
        runTest {
            assertFailsWith<ViewOnlyPendingImportMissingException> { useCase() }

            coVerify(exactly = 0) { accountDataSource.importViewOnlyAccount(any(), any(), any()) }
            verify(exactly = 0) { synchronizerProvider.resetSynchronizer() }
        }

    @Test
    fun theImporterReportsThePendingKey() {
        val importer = ViewOnlyAccountImporterImpl(repository, navigationRouter)
        assertFalse(importer.hasPendingImport())

        repository.set(PendingViewOnlyImport(ufvk = UFVK, name = ""))

        assertTrue(importer.hasPendingImport())
    }

    @Test
    fun theImporterStoresTheBirthdayAndOpensTheNamingStepWithoutImporting() {
        val importer = ViewOnlyAccountImporterImpl(repository, navigationRouter)
        repository.set(PendingViewOnlyImport(ufvk = UFVK, name = "Savings", color = Color(PICKED)))
        val birthday = BlockHeight.new(2_000_000L)

        importer.continueWithBirthday(birthday)

        assertEquals(
            PendingViewOnlyImport(ufvk = UFVK, name = "Savings", color = Color(PICKED), birthday = birthday),
            repository.get()
        )
        verify(exactly = 1) { navigationRouter.forward(NameVKArgs) }
        coVerify(exactly = 0) { accountDataSource.importViewOnlyAccount(any(), any(), any()) }
    }

    @Test
    fun theImporterFailsWhenNothingIsPending() {
        val importer = ViewOnlyAccountImporterImpl(repository, navigationRouter)

        assertFailsWith<ViewOnlyPendingImportMissingException> { importer.continueWithBirthday(null) }

        assertNull(repository.get())
        verify(exactly = 0) { navigationRouter.forward(any()) }
    }

    @Test
    fun thePendingImportNeverPrintsItsKey() {
        val birthday = BlockHeight.new(2_000_000L)

        val printed = PendingViewOnlyImport(ufvk = UFVK, name = "Savings", birthday = birthday).toString()

        assertFalse(printed.contains("uview"))
        assertTrue(printed.contains("birthday=$birthday"))
    }

    private companion object {
        const val UFVK = "uview1testkeythatmustneverbeprinted"
        const val PICKED = 0x80BE38F3
        const val COLOR_BUDGET_MILLIS = 5_000L
    }
}
