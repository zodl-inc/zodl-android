package co.electriccoin.zcash.ui.fixture

import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.model.GiftCardHandle
import co.electriccoin.zcash.ui.common.model.GiftCardOrigin
import co.electriccoin.zcash.ui.common.model.GiftCardStatus
import co.electriccoin.zcash.ui.common.model.GiftCardSummary
import co.electriccoin.zcash.ui.common.repository.GiftCardLinkPrefixes
import co.electriccoin.zcash.ui.common.repository.GiftCardRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * In-memory [GiftCardRepository] for previews and tests. It never touches the SDK and accepts any text that looks
 * like a gift card link.
 *
 * @param statuses what successive [check] calls report; the last one repeats once the list runs out.
 * @param parseError thrown by [parse] instead of returning [summary], when set.
 * @param checkError thrown by [check], when set.
 * @param redeemError thrown by [redeem], when set.
 *
 * [checkGate] and [redeemGate], when set, hold [check] and [redeem] until completed, to observe in-flight states.
 */
@Suppress("LongParameterList")
class FakeGiftCardRepository(
    var summary: GiftCardSummary = GiftCardSummaryFixture.new(),
    var statuses: List<GiftCardStatus> = listOf(GiftCardStatus.Ready(Zatoshi(GiftCardSummaryFixture.AMOUNT))),
    var parseError: Throwable? = null,
    var checkError: Throwable? = null,
    var redeemError: Throwable? = null,
    var txId: String = GiftCardSummaryFixture.TX_ID,
) : GiftCardRepository {
    val parsedLinks = mutableListOf<String>()
    val redeemedTo = mutableListOf<String>()
    val cleanedUp = mutableListOf<GiftCardHandle>()
    var checkCount = 0
        private set

    val progress = MutableStateFlow<Float?>(null)

    var checkGate: CompletableDeferred<Unit>? = null

    var redeemGate: CompletableDeferred<Unit>? = null

    override fun isGiftCardLink(value: String): Boolean = GiftCardLinkPrefixes.matches(value)

    override suspend fun parse(link: String): GiftCardSummary {
        parsedLinks += link
        parseError?.let { throw it }
        return summary
    }

    override fun observeCheckProgress(handle: GiftCardHandle): Flow<Float> = progress.filterNotNull()

    override suspend fun check(handle: GiftCardHandle): GiftCardStatus {
        val index = checkCount++
        checkGate?.await()
        checkError?.let { throw it }
        return statuses.getOrElse(index) { statuses.last() }
    }

    override suspend fun redeem(
        handle: GiftCardHandle,
        toAddress: String
    ): String {
        redeemedTo += toAddress
        redeemGate?.await()
        redeemError?.let { throw it }
        return txId
    }

    override fun cleanup(handle: GiftCardHandle) {
        cleanedUp += handle
    }
}

object GiftCardSummaryFixture {
    const val AMOUNT = 10_000_000L
    const val MESSAGE = "Welcome to Zcash Summit"
    const val TX_ID = "6d1c2c8a6a4c0b1f3f2c9b8e7d6a5f4e3d2c1b0a9f8e7d6c5b4a39281706f5e4"
    const val BIRTHDAY_HEIGHT = 3_100_000L

    fun new(
        handle: GiftCardHandle = GiftCardHandle("fixture"),
        origin: GiftCardOrigin = GiftCardOrigin.ZODL,
        birthdayHeight: Long = BIRTHDAY_HEIGHT,
        statedAmount: Zatoshi? = Zatoshi(AMOUNT),
        message: String? = MESSAGE,
    ) = GiftCardSummary(
        handle = handle,
        origin = origin,
        birthdayHeight = birthdayHeight,
        statedAmount = statedAmount,
        message = message
    )
}
