package co.electriccoin.zcash.ui.common.datasource

import android.app.Application
import cash.z.ecc.android.sdk.GiftCardRedeemer
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import cash.z.ecc.android.sdk.model.GiftCard
import cash.z.ecc.android.sdk.model.GiftCardLinkError
import cash.z.ecc.android.sdk.model.MemoContent
import cash.z.ecc.android.sdk.model.PersistableWallet
import cash.z.ecc.android.sdk.model.RecipientAddress
import cash.z.ecc.android.sdk.model.TransactionSubmitResult
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.GiftCardException
import co.electriccoin.zcash.ui.common.model.GiftCardHandle
import co.electriccoin.zcash.ui.common.model.GiftCardOrigin
import co.electriccoin.zcash.ui.common.model.GiftCardRedemption
import co.electriccoin.zcash.ui.common.model.GiftCardStatus
import co.electriccoin.zcash.ui.common.provider.IsTorEnabledStorageProvider
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import cash.z.ecc.android.sdk.exception.GiftCardException as SdkGiftCardException
import cash.z.ecc.android.sdk.model.GiftCardOrigin as SdkGiftCardOrigin

/**
 * [GiftCardDataSourceImpl] against a mocked SDK: link errors and statuses map to the app's types, the card wallet
 * uses the main wallet's network, endpoint and Tor setting, redemptions are recorded in the main wallet, redeemers
 * are closed exactly once and the card's key is wiped when it is closed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GiftCardDataSourceImplTest {
    private val application =
        mockk<Application>(relaxed = true) {
            every { getString(R.string.redeemGift_memo) } returns MEMO_LABEL
        }

    private val persistableWalletProvider = mockk<PersistableWalletProvider>()

    private val mainSynchronizer = mockk<Synchronizer>()

    private val synchronizerProvider =
        mockk<SynchronizerProvider> {
            every { synchronizer } returns MutableStateFlow(mainSynchronizer)
        }

    private val isTorEnabledStorageProvider = mockk<IsTorEnabledStorageProvider>()

    private val redeemer = mockk<GiftCardRedeemer>()

    private val replacementRedeemer = mockk<GiftCardRedeemer>()

    private val recipient = mockk<RecipientAddress>()

    private var newRedeemerCalls = 0

    private val parsedCard by lazy { card(ZcashNetwork.Mainnet) }

    @BeforeTest
    fun setUp() {
        mockkObject(GiftCard.Companion)
        mockkObject(GiftCardRedeemer.Companion)
        mockkObject(RecipientAddress.Companion)
        mockkObject(Synchronizer.Companion)

        coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(ZcashNetwork.Mainnet)
        coEvery { GiftCard.parse(LINK) } returns parsedCard
        coEvery { isTorEnabledStorageProvider.get() } returns true
        every { GiftCardRedeemer.new(any(), any(), any(), any(), any(), any()) } answers {
            listOf(redeemer, replacementRedeemer)[newRedeemerCalls++]
        }
        listOf(redeemer, replacementRedeemer).forEach {
            every { it.alias } returns ALIAS
            coEvery { it.close() } returns Unit
        }
        coEvery { RecipientAddress.new(ADDRESS, ZcashNetwork.Mainnet) } returns recipient
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun parseCreatesTheRedeemerForTheMainWalletsNetworkAndEndpoint() =
        runTest {
            val parsed = dataSource().parse(LINK)
            val summary = parsed.summary

            assertEquals(ALIAS, parsed.walletAlias)
            assertEquals(GiftCardOrigin.VIZOR, summary.origin)
            assertEquals(BIRTHDAY, summary.birthdayHeight)
            assertEquals(Zatoshi(AMOUNT), summary.statedAmount)
            assertEquals(MESSAGE, summary.message)
            verify(exactly = 1) {
                GiftCardRedeemer.new(
                    context = any(),
                    card = any(),
                    network = ZcashNetwork.Mainnet,
                    lightWalletEndpoint = ENDPOINT,
                    isTorEnabled = any(),
                    alias = any()
                )
            }
        }

    @Test
    fun theCardWalletUsesTorWhenTheMainWalletDoes() =
        runTest {
            dataSource().parse(LINK)

            verify(exactly = 1) { GiftCardRedeemer.new(any(), any(), any(), any(), isTorEnabled = true, alias = any()) }
        }

    @Test
    fun theCardWalletConnectsDirectlyWhenTorIsOff() =
        runTest {
            coEvery { isTorEnabledStorageProvider.get() } returns false

            dataSource().parse(LINK)

            verify(exactly = 1) {
                GiftCardRedeemer.new(any(), any(), any(), any(), isTorEnabled = false, alias = any())
            }
        }

    @Test
    fun anUnsetTorSettingConnectsDirectlyLikeTheMainWallet() =
        runTest {
            coEvery { isTorEnabledStorageProvider.get() } returns null

            dataSource().parse(LINK)

            verify(exactly = 1) {
                GiftCardRedeemer.new(any(), any(), any(), any(), isTorEnabled = false, alias = any())
            }
        }

    @Test
    fun theFreshRedeemerAfterAnUnsubmittedRedeemAlsoUsesTor() =
        runTest {
            val dataSource = dataSource(this)
            val handle = dataSource.parse(LINK).summary.handle
            coEvery { redeemer.redeem(recipient, any(), any()) } returns
                GiftCardRedeemer.Redemption(
                    fee = Zatoshi(FEE),
                    results = listOf(TransactionSubmitResult.NotAttempted(TX_ID))
                )

            assertFailsWith<GiftCardException.SubmitFailed> { dataSource.redeem(handle, ADDRESS) }

            verify(exactly = 2) { GiftCardRedeemer.new(any(), any(), any(), any(), isTorEnabled = true, alias = any()) }
        }

    @Test
    fun eachParseGetsItsOwnHandle() =
        runTest {
            coEvery { GiftCard.parse(OTHER_LINK) } returns card(ZcashNetwork.Mainnet)
            every { replacementRedeemer.alias } returns OTHER_ALIAS
            val dataSource = dataSource()

            assertNotEquals(dataSource.parse(LINK).summary.handle, dataSource.parse(OTHER_LINK).summary.handle)
        }

    @Test
    fun malformedLinkIsInvalid() =
        runTest {
            val cause = SdkGiftCardException.InvalidLink(GiftCardLinkError.MissingField)
            coEvery { GiftCard.parse(LINK) } throws cause

            val e = assertFailsWith<GiftCardException.InvalidLink> { dataSource().parse(LINK) }

            assertSame(cause, e.cause)
            assertEquals(0, newRedeemerCalls)
        }

    @Test
    fun linkForAnotherNetworkIsWrongNetwork() =
        runTest {
            coEvery { GiftCard.parse(LINK) } throws
                SdkGiftCardException.InvalidLink(GiftCardLinkError.NetworkMismatch)

            assertFailsWith<GiftCardException.WrongNetwork> { dataSource().parse(LINK) }
        }

    @Test
    fun cardForAnotherNetworkThanTheWalletIsWrongNetwork() =
        runTest {
            coEvery { GiftCard.parse(LINK) } returns card(ZcashNetwork.Testnet)

            assertFailsWith<GiftCardException.WrongNetwork> { dataSource().parse(LINK) }
            assertEquals(0, newRedeemerCalls)
        }

    @Test
    fun withoutAWalletRedemptionIsNotAvailable() =
        runTest {
            coEvery { persistableWalletProvider.getPersistableWallet() } returns null

            assertFailsWith<GiftCardException.NotAvailable> { dataSource().parse(LINK) }
        }

    @Test
    fun checkMapsTheCardsStatus() =
        runTest {
            val dataSource = dataSource()
            val handle = dataSource.parse(LINK).summary.handle
            val balance =
                GiftCardRedeemer.Balance(total = Zatoshi(300), spendable = Zatoshi(200), pending = Zatoshi(100))

            coEvery { redeemer.check() } returns GiftCardRedeemer.Status.Ready(balance, fee = Zatoshi(150))
            assertEquals(
                GiftCardStatus.Ready(spendable = Zatoshi(200), redeemable = Zatoshi(50)),
                dataSource.check(handle)
            )

            coEvery { redeemer.check() } returns GiftCardRedeemer.Status.Pending(balance)
            assertEquals(GiftCardStatus.Pending(Zatoshi(100)), dataSource.check(handle))

            coEvery { redeemer.check() } returns GiftCardRedeemer.Status.Empty
            assertEquals(GiftCardStatus.Empty, dataSource.check(handle))
        }

    @Test
    fun aCardHoldingNoMoreThanTheFeeIsEmpty() =
        runTest {
            val dataSource = dataSource()
            val handle = dataSource.parse(LINK).summary.handle
            coEvery { redeemer.check() } returns GiftCardRedeemer.Status.Empty

            assertEquals(GiftCardStatus.Empty, dataSource.check(handle))
        }

    @Test
    fun aCardWhoseWalletIsInUseIsReportedAsInUse() =
        runTest {
            val dataSource = dataSource()
            val handle = dataSource.parse(LINK).summary.handle
            val cause = SdkGiftCardException.InUse()
            coEvery { redeemer.check() } throws cause

            val e = assertFailsWith<GiftCardException.InUse> { dataSource.check(handle) }
            assertSame(cause, e.cause)
        }

    @Test
    fun aFailedCardWalletSyncReachesTheScreenAsACheckFailure() =
        runTest {
            val dataSource = dataSource()
            val handle = dataSource.parse(LINK).summary.handle
            val cause = SdkGiftCardException.SyncFailed(null)
            coEvery { redeemer.check() } throws cause

            assertSame(cause, assertFailsWith<GiftCardException.CheckFailed> { dataSource.check(handle) }.cause)
        }

    /**
     * The SDK's redeem can throw after the redemption's transaction was created and stored in the card wallet, where
     * it holds the card's notes: checking that wallet again would show the card as empty or pending while its funds
     * are still on it. The card must start over with a fresh redeemer, without being wiped.
     */
    @Test
    fun aRedeemThatThrowsStartsOverWithAFreshRedeemer() =
        runTest {
            val dataSource = dataSource(this)
            val handle = dataSource.parse(LINK).summary.handle
            val cause = IllegalStateException("no connection to the server")
            coEvery { redeemer.redeem(recipient, any(), any()) } throws cause

            val failure = assertFailsWith<GiftCardException.SubmitFailed> { dataSource.redeem(handle, ADDRESS) }
            assertSame(cause, failure.cause)
            advanceUntilIdle()

            coVerify(exactly = 1) { redeemer.close() }
            verify(exactly = 0) { parsedCard.wipe() }
            assertEquals(2, newRedeemerCalls)
            coEvery { replacementRedeemer.check() } returns GiftCardRedeemer.Status.Empty
            assertEquals(GiftCardStatus.Empty, dataSource.check(handle))
        }

    @Test
    fun aCancellationFromInsideTheRedeemerAlsoStartsOver() =
        runTest {
            val dataSource = dataSource(this)
            val handle = dataSource.parse(LINK).summary.handle
            coEvery { redeemer.redeem(recipient, any(), any()) } throws CancellationException("foreign")

            assertFailsWith<GiftCardException.SubmitFailed> { dataSource.redeem(handle, ADDRESS) }
            advanceUntilIdle()

            coVerify(exactly = 1) { redeemer.close() }
            assertEquals(2, newRedeemerCalls)
        }

    @Test
    fun aRedeemerThatCannotBeReplacedLeavesTheCardWithItsRedeemer() =
        runTest {
            val dataSource = dataSource(this)
            val handle = dataSource.parse(LINK).summary.handle
            coEvery { redeemer.redeem(recipient, any(), any()) } throws IllegalStateException("submit")
            coEvery { isTorEnabledStorageProvider.get() } throws IllegalStateException("preferences")

            assertFailsWith<GiftCardException.SubmitFailed> { dataSource.redeem(handle, ADDRESS) }
            advanceUntilIdle()

            coVerify(exactly = 0) { redeemer.close() }
            coEvery { redeemer.check() } returns GiftCardRedeemer.Status.Empty
            assertEquals(GiftCardStatus.Empty, dataSource.check(handle))
        }

    @Test
    fun aCancelledReplacementIsNotMistakenForAFailedOne() =
        runTest {
            val dataSource = dataSource(this)
            val handle = dataSource.parse(LINK).summary.handle
            coEvery { redeemer.redeem(recipient, any(), any()) } throws IllegalStateException("submit")
            coEvery { isTorEnabledStorageProvider.get() } throws CancellationException("cancelled")

            assertFailsWith<CancellationException> { dataSource.redeem(handle, ADDRESS) }
            assertEquals(1, newRedeemerCalls)
        }

    @Test
    fun aCheckWaitsForAnEarlierCloseOfTheSameCardWallet() =
        runTest {
            val events = mutableListOf<String>()
            coEvery { redeemer.close() } coAnswers {
                delay(CLOSE_DURATION_MS)
                events += "closed"
            }
            coEvery { replacementRedeemer.check() } coAnswers {
                events += "checked"
                GiftCardRedeemer.Status.Empty
            }
            val dataSource = dataSource(this)
            dataSource.close(dataSource.parse(LINK).summary.handle)
            val handle = dataSource.parse(LINK).summary.handle

            dataSource.check(handle)

            assertEquals(listOf("closed", "checked"), events)
        }

    @Test
    fun redeemBeforeACheckIsNotChecked() =
        runTest {
            val dataSource = dataSource()
            val handle = dataSource.parse(LINK).summary.handle
            coEvery { redeemer.redeem(recipient, any(), any()) } throws SdkGiftCardException.NotChecked()

            assertFailsWith<GiftCardException.NotChecked> { dataSource.redeem(handle, ADDRESS) }
        }

    @Test
    fun redeemOfACardInUseIsInUse() =
        runTest {
            val dataSource = dataSource()
            val handle = dataSource.parse(LINK).summary.handle
            coEvery { redeemer.redeem(recipient, any(), any()) } throws SdkGiftCardException.InUse()

            assertFailsWith<GiftCardException.InUse> { dataSource.redeem(handle, ADDRESS) }
        }

    @Test
    fun redeemOfADustCardIsNothingToRedeem() =
        runTest {
            val dataSource = dataSource()
            val handle = dataSource.parse(LINK).summary.handle
            coEvery { redeemer.redeem(recipient, any(), any()) } throws SdkGiftCardException.NothingToRedeem()

            assertFailsWith<GiftCardException.NothingToRedeem> { dataSource.redeem(handle, ADDRESS) }
        }

    @Test
    fun unknownHandleIsRejected() =
        runTest {
            val dataSource = dataSource()

            assertFailsWith<GiftCardException.UnknownHandle> { dataSource.check(GiftCardHandle("nope")) }
            assertFailsWith<GiftCardException.UnknownHandle> { dataSource.redeem(GiftCardHandle("nope"), ADDRESS) }
        }

    @Test
    fun redeemSendsToTheValidatedAddressAndReturnsTheTxId() =
        runTest {
            val dataSource = dataSource(this)
            val handle = dataSource.parse(LINK).summary.handle
            coEvery { redeemer.redeem(recipient, any(), any()) } returns
                GiftCardRedeemer.Redemption(fee = Zatoshi(FEE), results = listOf(success(TX_ID)))

            assertEquals(success(TX_ID).txIdString(), dataSource.redeem(handle, ADDRESS).txId)
            assertEquals(1, newRedeemerCalls)
        }

    @Test
    fun redeemRecordsTheClaimInTheMainWallet() =
        runTest {
            val dataSource = dataSource(this)
            val handle = dataSource.parse(LINK).summary.handle
            coEvery { redeemer.redeem(recipient, any(), mainSynchronizer) } returns
                GiftCardRedeemer.Redemption(
                    fee = Zatoshi(FEE),
                    results = listOf(success(TX_ID)),
                    recordedInDestination = true
                )

            assertEquals(success(TX_ID).txIdString(), dataSource.redeem(handle, ADDRESS).txId)
            coVerify(exactly = 1) { redeemer.redeem(recipient, any(), mainSynchronizer) }
        }

    @Test
    fun redeemSucceedsWhenTheMainWalletDidNotRecordTheClaim() =
        runTest {
            val dataSource = dataSource(this)
            val handle = dataSource.parse(LINK).summary.handle
            coEvery { redeemer.redeem(recipient, any(), mainSynchronizer) } returns
                GiftCardRedeemer.Redemption(
                    fee = Zatoshi(FEE),
                    results = listOf(success(TX_ID)),
                    recordedInDestination = false
                )

            assertEquals(success(TX_ID).txIdString(), dataSource.redeem(handle, ADDRESS).txId)
            advanceUntilIdle()
            coVerify(exactly = 0) { redeemer.close() }
            assertEquals(1, newRedeemerCalls)
        }

    @Test
    fun redeemIntoAWalletOnAnotherNetworkIsWrongNetwork() =
        runTest {
            val dataSource = dataSource()
            val handle = dataSource.parse(LINK).summary.handle
            coEvery { redeemer.redeem(recipient, any(), any()) } throws SdkGiftCardException.NetworkMismatch()

            assertFailsWith<GiftCardException.WrongNetwork> { dataSource.redeem(handle, ADDRESS) }
        }

    @Test
    fun redeemMemoNamesTheGiftCardAndItsMessage() =
        runTest {
            assertEquals(MemoContent.fromString("$MEMO_LABEL · $MESSAGE"), redeemMemo(message = MESSAGE))
        }

    @Test
    fun redeemMemoNamesTheGiftCardWhenItHasNoMessage() =
        runTest {
            assertEquals(MemoContent.fromString(MEMO_LABEL), redeemMemo(message = null))
        }

    /**
     * 300 two-byte characters: 600 bytes. After the 13-byte prefix, 499 bytes are left for the message, which is not
     * a multiple of two, so the cut must step back to a character boundary.
     */
    @Test
    fun redeemMemoCutsALongMessageOnACharacterBoundary() =
        runTest {
            val message = "é".repeat(300)
            assertEquals(600, MemoContent.length(message))

            val memo = redeemMemo(message)

            val text = memo.toStringOrNull()!!
            assertEquals("$MEMO_LABEL · " + "é".repeat(249), text)
            assertEquals(511, MemoContent.length(text))
        }

    /** Parses a card with [message] and redeems it, returning the memo given to the redeemer. */
    private suspend fun TestScope.redeemMemo(message: String?): MemoContent {
        coEvery { GiftCard.parse(LINK) } returns card(ZcashNetwork.Mainnet, message = message)
        val dataSource = dataSource(this)
        val handle = dataSource.parse(LINK).summary.handle
        val memo = slot<MemoContent?>()
        coEvery { redeemer.redeem(recipient, captureNullable(memo), any()) } returns
            GiftCardRedeemer.Redemption(fee = Zatoshi(FEE), results = listOf(success(TX_ID)))

        dataSource.redeem(handle, ADDRESS)

        return assertNotNull(memo.captured)
    }

    @Test
    fun unsubmittedRedeemFailsAndStartsOverWithAFreshRedeemer() =
        runTest {
            val dataSource = dataSource(this)
            val handle = dataSource.parse(LINK).summary.handle
            coEvery { redeemer.redeem(recipient, any(), any()) } returns
                GiftCardRedeemer.Redemption(
                    fee = Zatoshi(FEE),
                    results = listOf(TransactionSubmitResult.NotAttempted(TX_ID))
                )

            assertFailsWith<GiftCardException.SubmitFailed> { dataSource.redeem(handle, ADDRESS) }
            advanceUntilIdle()

            coVerify(exactly = 1) { redeemer.close() }
            assertEquals(2, newRedeemerCalls)
            coEvery { replacementRedeemer.check() } returns GiftCardRedeemer.Status.Empty
            assertEquals(GiftCardStatus.Empty, dataSource.check(handle))
        }

    @Test
    fun closeClosesTheRedeemerOnceWipesTheCardAndForgetsTheHandle() =
        runTest {
            val dataSource = dataSource(this)
            val handle = dataSource.parse(LINK).summary.handle

            dataSource.close(handle)
            dataSource.close(handle)
            dataSource.close(GiftCardHandle("nope"))
            advanceUntilIdle()

            coVerify(exactly = 1) { redeemer.close() }
            verify(exactly = 1) { parsedCard.wipe() }
            assertFailsWith<GiftCardException.UnknownHandle> { dataSource.check(handle) }
        }

    @Test
    fun theCardIsNotWipedWhenOnlyItsRedeemerIsReplaced() =
        runTest {
            val dataSource = dataSource(this)
            val handle = dataSource.parse(LINK).summary.handle
            coEvery { redeemer.redeem(recipient, any(), any()) } returns
                GiftCardRedeemer.Redemption(
                    fee = Zatoshi(FEE),
                    results = listOf(TransactionSubmitResult.NotAttempted(TX_ID))
                )

            assertFailsWith<GiftCardException.SubmitFailed> { dataSource.redeem(handle, ADDRESS) }
            advanceUntilIdle()

            verify(exactly = 0) { parsedCard.wipe() }
        }

    @Test
    fun aRedemptionWithoutResultsIsASubmitFailure() =
        runTest {
            val dataSource = dataSource(this)
            val handle = dataSource.parse(LINK).summary.handle
            coEvery { redeemer.redeem(recipient, any(), any()) } returns
                GiftCardRedeemer.Redemption(fee = Zatoshi(FEE), results = emptyList())

            assertFailsWith<GiftCardException.SubmitFailed> { dataSource.redeem(handle, ADDRESS) }
            advanceUntilIdle()

            assertEquals(2, newRedeemerCalls)
        }

    @Test
    fun redeemReportsTheAmountReceived() =
        runTest {
            val dataSource = dataSource(this)
            val handle = dataSource.parse(LINK).summary.handle
            coEvery { redeemer.redeem(recipient, any(), any()) } returns
                GiftCardRedeemer.Redemption(
                    fee = Zatoshi(FEE),
                    results = listOf(success(TX_ID)),
                    amount = Zatoshi(AMOUNT - FEE)
                )

            assertEquals(
                GiftCardRedemption(txId = success(TX_ID).txIdString(), received = Zatoshi(AMOUNT - FEE)),
                dataSource.redeem(handle, ADDRESS)
            )
        }

    @Test
    fun storedCardWalletsAreListedOnEveryNetworkAndErasedByAlias() =
        runTest {
            coEvery { GiftCardRedeemer.storedAliases(any(), ZcashNetwork.Mainnet) } returns setOf(ALIAS)
            coEvery { GiftCardRedeemer.storedAliases(any(), ZcashNetwork.Testnet) } returns setOf(OTHER_ALIAS)
            coEvery { Synchronizer.eraseAlias(any(), any(), any()) } returns true
            val dataSource = dataSource()

            val stored = dataSource.findStoredCardWallets()

            assertEquals(
                listOf(
                    StoredCardWallet(ZcashNetwork.Mainnet, ALIAS),
                    StoredCardWallet(ZcashNetwork.Testnet, OTHER_ALIAS)
                ),
                stored
            )
            dataSource.eraseCardWallet(stored.first())
            coVerify(exactly = 1) { Synchronizer.eraseAlias(any(), ZcashNetwork.Mainnet, ALIAS) }
        }

    @Test
    fun parsingTheSameCardAgainLeavesTheEarlierRedeemerAlone() =
        runTest {
            val dataSource = dataSource(this)
            val first = dataSource.parse(LINK).summary.handle
            dataSource.parse(LINK)
            advanceUntilIdle()

            coVerify(exactly = 0) { redeemer.close() }
            coEvery { redeemer.check() } returns GiftCardRedeemer.Status.Empty
            assertEquals(GiftCardStatus.Empty, dataSource.check(first))
        }

    @Test
    fun closedRedeemerIsReportedAsUnknownHandle() =
        runTest {
            val dataSource = dataSource(this)
            val handle = dataSource.parse(LINK).summary.handle
            coEvery { redeemer.check() } throws SdkGiftCardException.Closed()

            assertFailsWith<GiftCardException.UnknownHandle> { dataSource.check(handle) }
        }

    @Test
    fun aZodlCardIsReportedAsIssuedByZodl() =
        runTest {
            coEvery { GiftCard.parse(LINK) } returns card(ZcashNetwork.Mainnet, origin = SdkGiftCardOrigin.Zodl)

            assertEquals(GiftCardOrigin.ZODL, dataSource().parse(LINK).summary.origin)
        }

    @Test
    fun aCardTheRedeemerRefusesForItsNetworkIsWrongNetworkAndNotHeld() =
        runTest {
            val cause = SdkGiftCardException.NetworkMismatch()
            every { GiftCardRedeemer.new(any(), any(), any(), any(), any(), any()) } throws cause

            val e = assertFailsWith<GiftCardException.WrongNetwork> { dataSource().parse(LINK) }

            assertSame(cause, e.cause)
        }

    @Test
    fun redeemOfAClosedRedeemerIsUnknownHandle() =
        runTest {
            val dataSource = dataSource()
            val handle = dataSource.parse(LINK).summary.handle
            val cause = SdkGiftCardException.Closed()
            coEvery { redeemer.redeem(recipient, any(), any()) } throws cause

            val e = assertFailsWith<GiftCardException.UnknownHandle> { dataSource.redeem(handle, ADDRESS) }

            assertSame(cause, e.cause)
        }

    @Test
    fun anUnsubmittedRedeemOfACardClosedMeanwhileKeepsTheCardClosed() =
        runTest {
            val dataSource = dataSource(this)
            val handle = dataSource.parse(LINK).summary.handle
            coEvery { redeemer.redeem(recipient, any(), any()) } answers {
                dataSource.close(handle)
                GiftCardRedeemer.Redemption(
                    fee = Zatoshi(FEE),
                    results = listOf(TransactionSubmitResult.NotAttempted(TX_ID))
                )
            }

            assertFailsWith<GiftCardException.SubmitFailed> { dataSource.redeem(handle, ADDRESS) }
            advanceUntilIdle()

            coVerify(exactly = 1) { redeemer.close() }
            coVerify(exactly = 0) { replacementRedeemer.close() }
            verify(exactly = 1) { parsedCard.wipe() }
            assertFailsWith<GiftCardException.UnknownHandle> { dataSource.check(handle) }
        }

    @Test
    fun aRedeemWaitsForAnEarlierCloseOfTheSameCardWallet() =
        runTest {
            val events = mutableListOf<String>()
            coEvery { redeemer.close() } coAnswers {
                delay(CLOSE_DURATION_MS)
                events += "closed"
            }
            coEvery { replacementRedeemer.redeem(recipient, any(), any()) } coAnswers {
                events += "redeemed"
                GiftCardRedeemer.Redemption(fee = Zatoshi(FEE), results = listOf(success(TX_ID)))
            }
            val dataSource = dataSource(this)
            dataSource.close(dataSource.parse(LINK).summary.handle)
            val handle = dataSource.parse(LINK).summary.handle

            dataSource.redeem(handle, ADDRESS)

            assertEquals(listOf("closed", "redeemed"), events)
        }

    @Test
    fun closesOfTheSameCardWalletRunOneAfterTheOther() =
        runTest {
            val events = mutableListOf<String>()
            coEvery { redeemer.close() } coAnswers {
                delay(CLOSE_DURATION_MS)
                events += "first"
            }
            coEvery { replacementRedeemer.close() } coAnswers { events += "second" }
            val dataSource = dataSource(this)
            val first = dataSource.parse(LINK).summary.handle
            val second = dataSource.parse(LINK).summary.handle

            dataSource.close(first)
            dataSource.close(second)
            advanceUntilIdle()

            assertEquals(listOf("first", "second"), events)
            assertFailsWith<GiftCardException.UnknownHandle> { dataSource.check(first) }
        }

    @Test
    fun aFailingCloseStillWipesTheCardsKey() =
        runTest {
            coEvery { redeemer.close() } throws IllegalStateException("database is locked")
            val dataSource = dataSource(this)
            val handle = dataSource.parse(LINK).summary.handle

            dataSource.close(handle)
            advanceUntilIdle()

            coVerify(exactly = 1) { redeemer.close() }
            verify(exactly = 1) { parsedCard.wipe() }
        }

    private fun dataSource(scope: TestScope? = null) =
        GiftCardDataSourceImpl(
            application = application,
            persistableWalletProvider = persistableWalletProvider,
            synchronizerProvider = synchronizerProvider,
            isTorEnabledStorageProvider = isTorEnabledStorageProvider,
        ).also { dataSource -> scope?.let { dataSource.scope = it } }

    private fun wallet(network: ZcashNetwork) =
        mockk<PersistableWallet> {
            every { this@mockk.network } returns network
            every { endpoint } returns ENDPOINT
        }

    private fun card(
        network: ZcashNetwork,
        message: String? = MESSAGE,
        origin: SdkGiftCardOrigin = SdkGiftCardOrigin.LegacyV2
    ) = mockk<GiftCard>(relaxUnitFun = true) {
        every { this@mockk.network } returns network
        every { id } returns CARD_ID
        every { this@mockk.origin } returns origin
        every { birthdayHeight } returns BlockHeight.new(BIRTHDAY)
        every { statedAmount } returns Zatoshi(AMOUNT)
        every { description } returns message
    }

    private fun success(txId: FirstClassByteArray) = TransactionSubmitResult.Success(txId)

    private companion object {
        const val LINK = "https://gift.zodl.com/#v=1&key=zgift1test&height=3100000"
        const val OTHER_LINK = "https://gift.zodl.com/#v=1&key=zgift1other&height=3100000"
        const val CARD_ID = "0123456789abcdef0123456789abcdef"
        const val ALIAS = "giftcard_$CARD_ID"
        const val OTHER_ALIAS = "giftcard_fedcba9876543210fedcba9876543210"
        const val ADDRESS = "u1destination"
        const val BIRTHDAY = 3_100_000L
        const val AMOUNT = 10_000_000L
        const val FEE = 10_000L
        const val MESSAGE = "Welcome to Zcash Summit"
        const val MEMO_LABEL = "Gift card"
        const val CLOSE_DURATION_MS = 1_000L
        val ENDPOINT = LightWalletEndpoint(host = "zec.rocks", port = 443, isSecure = true)
        val TX_ID = FirstClassByteArray(ByteArray(32) { it.toByte() })
    }
}
