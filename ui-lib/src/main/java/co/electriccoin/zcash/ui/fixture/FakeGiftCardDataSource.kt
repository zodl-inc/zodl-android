package co.electriccoin.zcash.ui.fixture

import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.zcash.ui.common.datasource.GiftCardDataSource
import co.electriccoin.zcash.ui.common.datasource.ParsedGiftCard
import co.electriccoin.zcash.ui.common.datasource.StoredCardWallet
import co.electriccoin.zcash.ui.common.model.GiftCardHandle
import co.electriccoin.zcash.ui.common.model.GiftCardOrigin
import co.electriccoin.zcash.ui.common.model.GiftCardRedemption
import co.electriccoin.zcash.ui.common.model.GiftCardStatus
import co.electriccoin.zcash.ui.common.model.GiftCardSummary
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [GiftCardDataSource] for tests. It never touches the SDK; every parse gets a fresh handle.
 *
 * @param statuses what successive [check] calls report; the last one repeats once the list runs out.
 * @param parseError thrown by [parse] instead of returning a card, when set.
 * @param checkError thrown by [check], when set.
 * @param redeemError thrown by [redeem], when set.
 * @param walletAlias the card wallet alias every parse reports.
 *
 * [checkGate] and [redeemGate], when set, hold [check] and [redeem] until completed, to observe in-flight states.
 */
class FakeGiftCardDataSource(
    var summary: GiftCardSummary = GiftCardSummaryFixture.new(),
    var statuses: List<GiftCardStatus> = listOf(GiftCardSummaryFixture.readyStatus()),
    var parseError: Throwable? = null,
    var checkError: Throwable? = null,
    var redeemError: Throwable? = null,
    var redemption: GiftCardRedemption =
        GiftCardRedemption(txId = GiftCardSummaryFixture.TX_ID, received = Zatoshi(GiftCardSummaryFixture.RECEIVED)),
    var walletAlias: String = GiftCardSummaryFixture.WALLET_ALIAS,
    var storedWallets: List<StoredCardWallet> = emptyList(),
) : GiftCardDataSource {
    val parsedLinks = mutableListOf<String>()
    val checkedHandles = mutableListOf<GiftCardHandle>()
    val redeemedTo = mutableListOf<String>()
    val closed = mutableListOf<GiftCardHandle>()
    val erased = mutableListOf<StoredCardWallet>()
    var checkCount = 0
        private set

    var checkGate: CompletableDeferred<Unit>? = null

    var redeemGate: CompletableDeferred<Unit>? = null

    override suspend fun parse(link: String): ParsedGiftCard {
        parsedLinks += link
        parseError?.let { throw it }
        val handle = GiftCardHandle("fixture-${parsedLinks.size}")
        return ParsedGiftCard(summary = summary.copy(handle = handle), walletAlias = walletAlias)
    }

    override suspend fun check(handle: GiftCardHandle): GiftCardStatus {
        val index = checkCount++
        checkedHandles += handle
        checkGate?.await()
        checkError?.let { throw it }
        return statuses.getOrElse(index) { statuses.last() }
    }

    override suspend fun redeem(
        handle: GiftCardHandle,
        toAddress: String
    ): GiftCardRedemption {
        redeemedTo += toAddress
        redeemGate?.await()
        redeemError?.let { throw it }
        return redemption
    }

    override fun close(handle: GiftCardHandle) {
        closed += handle
    }

    override suspend fun findStoredCardWallets(): List<StoredCardWallet> = storedWallets

    override suspend fun eraseCardWallet(wallet: StoredCardWallet) {
        erased += wallet
    }
}

object GiftCardSummaryFixture {
    const val AMOUNT = 10_000_000L
    const val FEE = 10_000L
    const val RECEIVED = AMOUNT - FEE
    const val MESSAGE = "Welcome to Zcash Summit"
    const val TX_ID = "6d1c2c8a6a4c0b1f3f2c9b8e7d6a5f4e3d2c1b0a9f8e7d6c5b4a39281706f5e4"
    const val BIRTHDAY_HEIGHT = 3_100_000L
    const val WALLET_ALIAS = "giftcard_0123456789abcdef0123456789abcdef"

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

    fun readyStatus() = GiftCardStatus.Ready(spendable = Zatoshi(AMOUNT), redeemable = Zatoshi(RECEIVED))

    fun pendingStatus() = GiftCardStatus.Pending(Zatoshi(AMOUNT))

    fun storedWallet(alias: String = WALLET_ALIAS) = StoredCardWallet(network = ZcashNetwork.Mainnet, alias = alias)
}
