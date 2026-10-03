package co.electriccoin.zcash.ui.common.repository

import android.app.Application
import cash.z.ecc.android.sdk.GiftCardRedeemer
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import cash.z.ecc.android.sdk.model.GiftCard
import cash.z.ecc.android.sdk.model.GiftCardLinkError
import cash.z.ecc.android.sdk.model.PersistableWallet
import cash.z.ecc.android.sdk.model.RecipientAddress
import cash.z.ecc.android.sdk.model.TransactionSubmitResult
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.common.model.GiftCardException
import co.electriccoin.zcash.ui.common.model.GiftCardHandle
import co.electriccoin.zcash.ui.common.model.GiftCardOrigin
import co.electriccoin.zcash.ui.common.model.GiftCardStatus
import co.electriccoin.zcash.ui.common.provider.PersistableWalletProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import cash.z.ecc.android.sdk.exception.GiftCardException as SdkGiftCardException
import cash.z.ecc.android.sdk.model.GiftCardOrigin as SdkGiftCardOrigin

/**
 * [GiftCardRepositoryImpl] against a mocked SDK: link errors and statuses map to the app's types, the card wallet
 * uses the main wallet's network and endpoint, and redeemers are closed exactly once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GiftCardRepositoryImplTest {
    private val persistableWalletProvider = mockk<PersistableWalletProvider>()

    private val redeemer = mockk<GiftCardRedeemer>()

    private val replacementRedeemer = mockk<GiftCardRedeemer>()

    private val recipient = mockk<RecipientAddress>()

    private var newRedeemerCalls = 0

    @BeforeTest
    fun setUp() {
        mockkObject(GiftCard.Companion)
        mockkObject(GiftCardRedeemer.Companion)
        mockkObject(RecipientAddress.Companion)

        coEvery { persistableWalletProvider.getPersistableWallet() } returns wallet(ZcashNetwork.Mainnet)
        coEvery { GiftCard.parse(LINK) } returns card(ZcashNetwork.Mainnet)
        every { GiftCardRedeemer.new(any(), any(), any(), any(), any()) } answers {
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
            val summary = repository().parse(LINK)

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
                    alias = any()
                )
            }
        }

    @Test
    fun eachParseGetsItsOwnHandle() =
        runTest {
            coEvery { GiftCard.parse(OTHER_LINK) } returns card(ZcashNetwork.Mainnet)
            every { replacementRedeemer.alias } returns OTHER_ALIAS
            val repository = repository()

            assertNotEquals(repository.parse(LINK).handle, repository.parse(OTHER_LINK).handle)
        }

    @Test
    fun malformedLinkIsInvalid() =
        runTest {
            val cause = SdkGiftCardException.InvalidLink(GiftCardLinkError.MissingField)
            coEvery { GiftCard.parse(LINK) } throws cause

            val e = assertFailsWith<GiftCardException.InvalidLink> { repository().parse(LINK) }

            assertSame(cause, e.cause)
            assertEquals(0, newRedeemerCalls)
        }

    @Test
    fun linkForAnotherNetworkIsWrongNetwork() =
        runTest {
            coEvery { GiftCard.parse(LINK) } throws
                SdkGiftCardException.InvalidLink(GiftCardLinkError.NetworkMismatch)

            assertFailsWith<GiftCardException.WrongNetwork> { repository().parse(LINK) }
        }

    @Test
    fun cardForAnotherNetworkThanTheWalletIsWrongNetwork() =
        runTest {
            coEvery { GiftCard.parse(LINK) } returns card(ZcashNetwork.Testnet)

            assertFailsWith<GiftCardException.WrongNetwork> { repository().parse(LINK) }
            assertEquals(0, newRedeemerCalls)
        }

    @Test
    fun withoutAWalletRedemptionIsNotAvailable() =
        runTest {
            coEvery { persistableWalletProvider.getPersistableWallet() } returns null

            assertFailsWith<GiftCardException.NotAvailable> { repository().parse(LINK) }
        }

    @Test
    fun checkMapsTheCardsStatus() =
        runTest {
            val repository = repository()
            val handle = repository.parse(LINK).handle
            val balance =
                GiftCardRedeemer.Balance(total = Zatoshi(300), spendable = Zatoshi(200), pending = Zatoshi(100))

            coEvery { redeemer.check() } returns GiftCardRedeemer.Status.Ready(balance)
            assertEquals(GiftCardStatus.Ready(Zatoshi(200)), repository.check(handle))

            coEvery { redeemer.check() } returns GiftCardRedeemer.Status.Pending(balance)
            assertEquals(GiftCardStatus.Pending(Zatoshi(100), confirmationsRemaining = null), repository.check(handle))

            coEvery { redeemer.check() } returns GiftCardRedeemer.Status.Empty
            assertEquals(GiftCardStatus.Empty, repository.check(handle))
        }

    @Test
    fun unknownHandleIsRejected() =
        runTest {
            val repository = repository()

            assertFailsWith<GiftCardException.UnknownHandle> { repository.check(GiftCardHandle("nope")) }
            assertFailsWith<GiftCardException.UnknownHandle> { repository.redeem(GiftCardHandle("nope"), ADDRESS) }
        }

    @Test
    fun redeemSendsToTheValidatedAddressAndReturnsTheTxId() =
        runTest {
            val repository = repository(this)
            val handle = repository.parse(LINK).handle
            coEvery { redeemer.redeem(recipient, null) } returns
                GiftCardRedeemer.Redemption(fee = Zatoshi(FEE), results = listOf(success(TX_ID)))

            assertEquals(success(TX_ID).txIdString(), repository.redeem(handle, ADDRESS))
            assertEquals(1, newRedeemerCalls)
        }

    @Test
    fun unsubmittedRedeemFailsAndStartsOverWithAFreshRedeemer() =
        runTest {
            val repository = repository(this)
            val handle = repository.parse(LINK).handle
            coEvery { redeemer.redeem(recipient, null) } returns
                GiftCardRedeemer.Redemption(
                    fee = Zatoshi(FEE),
                    results = listOf(TransactionSubmitResult.NotAttempted(TX_ID))
                )

            assertFailsWith<GiftCardException.SubmitFailed> { repository.redeem(handle, ADDRESS) }
            advanceUntilIdle()

            coVerify(exactly = 1) { redeemer.close() }
            assertEquals(2, newRedeemerCalls)
            coEvery { replacementRedeemer.check() } returns GiftCardRedeemer.Status.Empty
            assertEquals(GiftCardStatus.Empty, repository.check(handle))
        }

    @Test
    fun cleanupClosesTheRedeemerOnceAndForgetsTheHandle() =
        runTest {
            val repository = repository(this)
            val handle = repository.parse(LINK).handle

            repository.cleanup(handle)
            repository.cleanup(handle)
            repository.cleanup(GiftCardHandle("nope"))
            advanceUntilIdle()

            coVerify(exactly = 1) { redeemer.close() }
            assertFailsWith<GiftCardException.UnknownHandle> { repository.check(handle) }
        }

    @Test
    fun parsingAHeldCardAgainClosesTheEarlierRedeemer() =
        runTest {
            val repository = repository(this)
            val first = repository.parse(LINK).handle
            val second = repository.parse(LINK).handle
            advanceUntilIdle()

            coVerify(exactly = 1) { redeemer.close() }
            assertFailsWith<GiftCardException.UnknownHandle> { repository.check(first) }
            coEvery { replacementRedeemer.check() } returns GiftCardRedeemer.Status.Empty
            assertEquals(GiftCardStatus.Empty, repository.check(second))
        }

    @Test
    fun closedRedeemerIsReportedAsUnknownHandle() =
        runTest {
            val repository = repository(this)
            val handle = repository.parse(LINK).handle
            coEvery { redeemer.check() } throws SdkGiftCardException.Closed()

            assertFailsWith<GiftCardException.UnknownHandle> { repository.check(handle) }
        }

    @Test
    fun noProgressIsReported() =
        runTest {
            val repository = repository()
            val handle = repository.parse(LINK).handle
            var emitted: Float? = null

            repository.observeCheckProgress(handle).collect { emitted = it }

            assertNull(emitted)
        }

    private fun repository(scope: TestScope? = null) =
        GiftCardRepositoryImpl(
            application = mockk<Application>(relaxed = true),
            persistableWalletProvider = persistableWalletProvider,
        ).also { repository -> scope?.let { repository.scope = it } }

    private fun wallet(network: ZcashNetwork) =
        mockk<PersistableWallet> {
            every { this@mockk.network } returns network
            every { endpoint } returns ENDPOINT
        }

    private fun card(network: ZcashNetwork) =
        mockk<GiftCard> {
            every { this@mockk.network } returns network
            every { id } returns CARD_ID
            every { origin } returns SdkGiftCardOrigin.VizorV2
            every { birthdayHeight } returns BlockHeight.new(BIRTHDAY)
            every { statedAmount } returns Zatoshi(AMOUNT)
            every { description } returns MESSAGE
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
        val ENDPOINT = LightWalletEndpoint(host = "zec.rocks", port = 443, isSecure = true)
        val TX_ID = FirstClassByteArray(ByteArray(32) { it.toByte() })
    }
}
