package co.electriccoin.zcash.ui.screen.transactionnote.viewmodel

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.repository.TransactionMetadata
import co.electriccoin.zcash.ui.common.usecase.CreateOrUpdateTransactionNoteUseCase
import co.electriccoin.zcash.ui.common.usecase.DeleteTransactionNoteUseCase
import co.electriccoin.zcash.ui.common.usecase.GetTransactionMetadataUseCase
import co.electriccoin.zcash.ui.screen.transactionnote.TransactionNote
import co.electriccoin.zcash.ui.screen.transactionnote.model.TransactionNoteState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The note sheet running one save at a time and showing loading on the button that started it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionNoteViewModelTest {
    private lateinit var dispatcher: TestDispatcher
    private val saveResult = CompletableDeferred<Boolean>()
    private val createOrUpdate =
        mockk<CreateOrUpdateTransactionNoteUseCase> {
            coEvery { this@mockk.invoke(any(), any()) } coAnswers { saveResult.await() }
        }
    private val delete =
        mockk<DeleteTransactionNoteUseCase> {
            coEvery { this@mockk.invoke(any()) } coAnswers { saveResult.await() }
        }

    @BeforeTest
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aSecondTapWhileSavingStartsNoOtherSave() =
        runTest(dispatcher) {
            val vm = startedVm()

            requireNotNull(vm.state().secondaryButton).onClick()
            advanceUntilIdle()
            requireNotNull(vm.state().secondaryButton).onClick()
            requireNotNull(vm.state().negative).onClick()
            advanceUntilIdle()

            coVerify(exactly = 1) { createOrUpdate(TX_ID, NOTE) }
            coVerify(exactly = 0) { delete(any()) }
        }

    @Test
    fun thePressedButtonShowsLoadingUntilTheSaveReturns() =
        runTest(dispatcher) {
            val vm = startedVm()

            requireNotNull(vm.state().negative).onClick()
            advanceUntilIdle()

            assertTrue(requireNotNull(vm.state().negative).isLoading)
            assertFalse(requireNotNull(vm.state().secondaryButton).isLoading)

            saveResult.complete(true)
            advanceUntilIdle()

            assertFalse(requireNotNull(vm.state().negative).isLoading)
            requireNotNull(vm.state().negative).onClick()
            advanceUntilIdle()
            coVerify(exactly = 2) { delete(TX_ID) }
        }

    private fun TestScope.startedVm(): TransactionNoteViewModel {
        val vm =
            TransactionNoteViewModel(
                transactionNote = TransactionNote(TX_ID),
                navigationRouter = mockk<NavigationRouter>(relaxed = true),
                getTransactionNote =
                    mockk<GetTransactionMetadataUseCase> {
                        coEvery { this@mockk.invoke(TX_ID) } returns
                            TransactionMetadata(isBookmarked = false, isRead = false, note = NOTE, swapMetadata = null)
                    },
                createOrUpdateTransactionNote = createOrUpdate,
                deleteTransactionNote = delete
            )
        backgroundScope.launch { vm.state.collect { } }
        advanceUntilIdle()
        requireNotNull(vm.state().secondaryButton) { "The found note should show the save button" }
        return vm
    }

    private fun TransactionNoteViewModel.state(): TransactionNoteState = state.value

    private companion object {
        const val TX_ID = "tx1"
        const val NOTE = "note"
    }
}
