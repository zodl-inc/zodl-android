package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.ui.common.repository.AddressBookRepository
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import co.electriccoin.zcash.ui.common.repository.Transaction
import co.electriccoin.zcash.ui.common.repository.TransactionMetadata
import co.electriccoin.zcash.ui.common.repository.TransactionRepository
import co.electriccoin.zcash.ui.common.repository.TransactionSwapMetadata
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * [GetTransactionDetailByIdUseCase] combines the transaction, its memos, the address-book contact,
 * the metadata, and — when the metadata records a swap — the live swap status into one
 * [DetailedTransactionData]. The swap status is keyed on the swap's deposit address, so metadata
 * churn caused by the status poll itself (status/lastUpdated writes) must NOT restart the poll.
 *
 * Uses `runBlocking` (real time) because the flow runs on `Dispatchers.Default` via `flowOn`, which
 * `runTest`'s virtual clock cannot drive.
 */
class GetTransactionDetailByIdUseCaseTest {
    private val transactionRepository = mockk<TransactionRepository>(relaxed = true)
    private val addressBookRepository = mockk<AddressBookRepository>(relaxed = true)
    private val metadataRepository = mockk<MetadataRepository>(relaxed = true)
    private val getSwapStatus = mockk<GetSwapStatusUseCase>()

    private val transaction =
        mockk<Transaction>(relaxed = true) {
            every { recipient } returns null
        }

    private val useCase =
        GetTransactionDetailByIdUseCase(
            transactionRepository = transactionRepository,
            addressBookRepository = addressBookRepository,
            metadataRepository = metadataRepository,
            getSwapStatus = getSwapStatus
        )

    init {
        every { transactionRepository.observeTransaction(TX_ID) } returns flowOf(transaction)
        coEvery { transactionRepository.getMemos(any()) } returns listOf("memo")
    }

    @Test
    fun combinesAllSourcesIntoDetailedTransactionData() =
        runBlocking {
            val swapData = SwapQuoteStatusData(isLoading = false)
            val swap = mockk<TransactionSwapMetadata> { every { depositAddress } returns "deposit" }
            val metadata = mockk<TransactionMetadata> { every { swapMetadata } returns swap }
            every { metadataRepository.observeTransactionMetadata(any()) } returns flowOf(metadata)
            every { getSwapStatus.observe(any()) } returns flowOf(swapData)

            val result = withTimeout(TIMEOUT_MS) { useCase.observe(TX_ID).first { it.memos != null } }

            assertEquals(transaction, result.transaction)
            assertEquals(listOf("memo"), result.memos)
            assertEquals(metadata, result.metadata)
            assertEquals(swapData, result.swap)
            assertNull(result.contact)
            verify { getSwapStatus.observe(swap) }
        }

    @Test
    fun swapIsNullAndStatusNotObservedWhenMetadataHasNoSwap() =
        runBlocking {
            val metadata = mockk<TransactionMetadata> { every { swapMetadata } returns null }
            every { metadataRepository.observeTransactionMetadata(any()) } returns flowOf(metadata)

            val result = withTimeout(TIMEOUT_MS) { useCase.observe(TX_ID).first { it.memos != null } }

            assertNull(result.swap)
            verify(exactly = 0) { getSwapStatus.observe(any()) }
        }

    @Test
    fun doesNotRestartSwapStatusPollWhenMetadataChangesButDepositAddressIsUnchanged() =
        runBlocking {
            // Two distinct metadata objects with the SAME deposit address — i.e. the status poll writing
            // updated status/lastUpdated back to metadata must not retrigger observe().
            val swap1 = mockk<TransactionSwapMetadata> { every { depositAddress } returns "deposit" }
            val swap2 = mockk<TransactionSwapMetadata> { every { depositAddress } returns "deposit" }
            val metadata1 = mockk<TransactionMetadata> { every { swapMetadata } returns swap1 }
            val metadata2 = mockk<TransactionMetadata> { every { swapMetadata } returns swap2 }
            val metadataState = MutableStateFlow(metadata1)
            every { metadataRepository.observeTransactionMetadata(any()) } returns metadataState
            every { getSwapStatus.observe(any()) } returns flowOf(SwapQuoteStatusData(isLoading = false))

            val emissions = mutableListOf<DetailedTransactionData>()
            val job = launch { useCase.observe(TX_ID).collect { emissions += it } }
            // The first swap status has been observed for metadata1.
            withTimeout(TIMEOUT_MS) { while (emissions.none { it.swap != null }) yield() }

            // Simulate the poll mutating the metadata (new object, same deposit address) and give the
            // pipeline real time to (incorrectly) re-observe before asserting it did not.
            metadataState.value = metadata2
            delay(SETTLE_MS)

            verify(exactly = 1) { getSwapStatus.observe(any()) }
            job.cancel()
        }

    private companion object {
        const val TX_ID = "tx"
        val TIMEOUT_MS = 10_000.milliseconds
        val SETTLE_MS = 500.milliseconds
    }
}
