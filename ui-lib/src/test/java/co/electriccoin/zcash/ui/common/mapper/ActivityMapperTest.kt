package co.electriccoin.zcash.ui.common.mapper

import cash.z.ecc.android.sdk.model.TransactionId
import cash.z.ecc.android.sdk.model.TransactionOverview
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.Zip318Kind
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapStatus
import co.electriccoin.zcash.ui.common.repository.ReceiveTransaction
import co.electriccoin.zcash.ui.common.repository.SendTransaction
import co.electriccoin.zcash.ui.common.repository.ShieldTransaction
import co.electriccoin.zcash.ui.common.repository.Transaction
import co.electriccoin.zcash.ui.common.repository.TransactionMetadata
import co.electriccoin.zcash.ui.common.repository.TransactionSwapMetadata
import co.electriccoin.zcash.ui.common.usecase.ActivityData
import io.mockk.every
import io.mockk.mockk
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActivityMapperTest {
    private val mapper = ActivityMapper()
    private val id = TransactionId.new(ByteArray(32) { 1 })
    private val amount = Zatoshi(100_000)
    private val overview =
        mockk<TransactionOverview> {
            every { zip318Kind } returns Zip318Kind.NOT_CLASSIFIED
        }

    @Test
    fun failedOnChainTransactionsOmitTheirAmount() {
        val transactions =
            listOf(
                failedSend,
                failedSend.copy(isRejected = true),
                ReceiveTransaction.Failed(id, amount, null, 0, emptyList(), overview, null),
                ShieldTransaction.Failed(id, amount, 0, null, emptyList(), null, overview, null)
            )

        transactions.forEach { transaction ->
            assertNull(mapActivity(activity(transaction)).value)
        }
    }

    @Test
    fun failedAndExpiredSwapAndCrossPayOmitTheirAmount() {
        listOf(SwapStatus.FAILED, SwapStatus.EXPIRED).forEach { status ->
            assertNull(mapActivity(ActivityData.BySwap(swap(status, intoZec = true))).value)
            listOf(SwapMode.EXACT_INPUT, SwapMode.EXACT_OUTPUT).forEach { mode ->
                val row = mapActivity(activity(successfulSend, swap(status, mode)))
                assertNull(row.value)
                val icon =
                    if (mode == SwapMode.EXACT_OUTPUT) {
                        R.drawable.ic_activity_pay_failed
                    } else {
                        R.drawable.ic_activity_send_failed
                    }
                assertEquals(icon, row.bigIcon)
            }
        }
    }

    @Test
    fun failedFundingTakesPrecedenceOverPendingProviderStatus() {
        val row = mapActivity(activity(failedSend, swap(SwapStatus.PENDING, SwapMode.EXACT_OUTPUT)))

        assertNull(row.value)
        assertEquals(R.drawable.ic_activity_pay_failed, row.bigIcon)
    }

    @Test
    fun otherStatusesKeepTheirAmount() {
        val pendingSend = SendTransaction.Pending(id, amount, null, 0, null, emptyList(), overview, null)
        assertNotNull(mapActivity(activity(pendingSend)).value)
        assertNotNull(mapActivity(activity(successfulSend)).value)

        listOf(SwapStatus.PENDING, SwapStatus.PROCESSING, SwapStatus.SUCCESS, SwapStatus.REFUNDED).forEach { status ->
            assertNotNull(mapActivity(ActivityData.BySwap(swap(status, intoZec = true))).value)
            assertNotNull(mapActivity(activity(successfulSend, swap(status, SwapMode.EXACT_OUTPUT))).value)
        }
    }

    private val failedSend = SendTransaction.Failed(id, amount, null, 0, null, emptyList(), overview, null)
    private val successfulSend = SendTransaction.Success(id, amount, null, 0, null, emptyList(), overview, null)

    private fun mapActivity(data: ActivityData) =
        mapper.createTransactionState(data, Instant.EPOCH, {}, {}, {})

    private fun activity(transaction: Transaction, swap: TransactionSwapMetadata? = null) =
        ActivityData.ByTransaction(transaction, TransactionMetadata(false, false, null, swap))

    private fun swap(
        status: SwapStatus,
        mode: SwapMode = SwapMode.EXACT_INPUT,
        intoZec: Boolean = false
    ) =
        TransactionSwapMetadata(
            depositAddress = "deposit",
            provider = "provider",
            totalFees = Zatoshi(0),
            totalFeesUsd = BigDecimal.ZERO,
            lastUpdated = Instant.EPOCH,
            origin = if (intoZec) SwapAssetTestFixture.simpleAsset() else SwapAssetTestFixture.zecSimpleAsset(),
            destination = if (intoZec) SwapAssetTestFixture.zecSimpleAsset() else SwapAssetTestFixture.simpleAsset(),
            mode = mode,
            status = status,
            amountOutFormatted = BigDecimal.ONE
        )
}
