package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import android.net.Uri
import androidx.navigation.NavBackStackEntry
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.GiftCardLinkStore
import co.electriccoin.zcash.ui.common.usecase.Zip321ParseUriValidationUseCase.Zip321ParseUriValidation
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.scan.ImageToQrCodeResult
import co.electriccoin.zcash.ui.screen.scan.ImageUriToQrCodeConverter
import co.electriccoin.zcash.ui.screen.send.Send
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.zecdev.zip321.model.MemoBytes
import org.zecdev.zip321.model.NonNegativeAmount
import org.zecdev.zip321.model.Payment
import org.zecdev.zip321.model.PaymentRequest
import org.zecdev.zip321.model.RecipientAddress
import org.zecdev.zip321.parser.ParserContext
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import cash.z.ecc.sdk.model.AddressType as RouteAddressType

/**
 * The validators are mocked: what matters here is which substrings of the shared content reach them, in what order,
 * and what each outcome opens. [tried] records every candidate handed to the ZIP 321 parser, and a test lists the
 * candidates it should accept in [accepted]; everything else is rejected.
 */
class HandleSharedPaymentUseCaseTest {
    private val context = mockk<Context>()
    private val image = mockk<Uri>()
    private val imageUriToQrCodeConverter = mockk<ImageUriToQrCodeConverter>()
    private val synchronizerProvider = mockk<SynchronizerProvider>()
    private val zip321 = mockk<Zip321ParseUriValidationUseCase>()
    private val validateAddress = mockk<ValidateAddressUseCase>()
    private val rejectIfSelectedAccountCannotSpend = mockk<RejectIfSelectedAccountCannotSpendUseCase>()
    private val giftCardLinkStore = mockk<GiftCardLinkStore>(relaxed = true)
    private val router = FakeNavigationRouter()
    private val showError = mockk<ShowErrorUseCase>(relaxed = true)

    private val tried = mutableListOf<String>()
    private val accepted = mutableMapOf<String, Zip321ParseUriValidation>()

    private val useCase =
        HandleSharedPaymentUseCase(
            context = context,
            imageUriToQrCodeConverter = imageUriToQrCodeConverter,
            synchronizerProvider = synchronizerProvider,
            zip321ParseUriValidationUseCase = zip321,
            validateAddress = validateAddress,
            rejectIfSelectedAccountCannotSpend = rejectIfSelectedAccountCannotSpend,
            giftCardLinkStore = giftCardLinkStore,
            navigationRouter = router,
            showError = showError
        )

    @BeforeTest
    fun setUp() {
        coEvery { synchronizerProvider.getSynchronizerOrNull() } returns mockk<Synchronizer>()
        coEvery { zip321(any()) } answers {
            val candidate = firstArg<String>()
            tried += candidate
            accepted[candidate] ?: Zip321ParseUriValidation.Invalid
        }
        coEvery { validateAddress(any()) } returns AddressType.Invalid()
        coEvery { rejectIfSelectedAccountCannotSpend(any()) } returns false
    }

    // region candidates

    @Test
    fun bareAddressIsTheOnlyCandidate() =
        runTest {
            useCase(TRANSPARENT_ADDRESS)

            assertEquals(listOf(TRANSPARENT_ADDRESS), tried)
        }

    @Test
    fun addressInsideASentenceIsFound() =
        runTest {
            useCase("Please pay me at $TRANSPARENT_ADDRESS thanks")

            assertEquals(listOf(TRANSPARENT_ADDRESS), tried)
        }

    @Test
    fun quotedAndBracketedAddressIsFoundOnce() =
        runTest {
            useCase("""Use "$TRANSPARENT_ADDRESS" or ($TRANSPARENT_ADDRESS)""")

            assertEquals(listOf(TRANSPARENT_ADDRESS), tried)
        }

    @Test
    fun addressInMarkdownEmphasisIsFound() =
        runTest {
            useCase("Pay _${TRANSPARENT_ADDRESS}_ or *$TRANSPARENT_ADDRESS*")

            assertEquals(listOf(TRANSPARENT_ADDRESS), tried)
        }

    @Test
    fun zip321UriKeepsItsQuery() =
        runTest {
            useCase(ZIP321_URI_WITH_MEMO)

            assertEquals(listOf(ZIP321_URI_WITH_MEMO), tried)
        }

    @Test
    fun punctuationAfterAUriIsRetriedWithoutIt() =
        runTest {
            useCase("Send it to $ZIP321_URI.")

            assertEquals(listOf("$ZIP321_URI.", ZIP321_URI), tried)
        }

    @Test
    fun zip321UriIsTriedBeforeABareAddressThatComesFirst() =
        runTest {
            useCase("$TRANSPARENT_ADDRESS or better $ZIP321_URI")

            assertEquals(listOf(ZIP321_URI, TRANSPARENT_ADDRESS), tried)
        }

    @Test
    fun proseWithoutAPaymentIsNotValidated() =
        runTest {
            useCase("Hello world, just checking in about that thing we discussed")

            assertEquals(emptyList(), tried)
            verify { showError(stringRes(R.string.scan_sharedContentInvalid)) }
        }

    @Test
    fun hashesAndIdsAreNotCandidates() =
        runTest {
            val hashes = (0 until 200).joinToString(" ") { "8f3c2a9b7d1e4f6a0b5c8d2e7f1a3b9c$it" }

            useCase("$hashes $TRANSPARENT_ADDRESS")

            assertEquals(listOf(TRANSPARENT_ADDRESS), tried)
        }

    @Test
    fun candidatesAreCapped() =
        runTest {
            useCase((0 until 200).joinToString(" ") { "utest1candidate0000000000000000$it" })

            assertEquals(64, tried.size)
            assertEquals(emptyList(), router.commands)
        }

    // endregion

    // region what is opened

    @Test
    fun zip321UriOpensSendWithItsAmountAndMemo() =
        runTest {
            accepted[ZIP321_URI_WITH_MEMO] = zip321Valid(amount = "0.0001", memo = "hello")
            coEvery { validateAddress(TRANSPARENT_ADDRESS) } returns AddressType.Transparent

            useCase(ZIP321_URI_WITH_MEMO)

            val send =
                Send(
                    recipientAddress = TRANSPARENT_ADDRESS,
                    recipientAddressType = RouteAddressType.TRANSPARENT,
                    amount = 10_000L,
                    memo = "hello"
                )
            assertEquals(listOf<BaseNavigationCommand>(NavigationCommand.ReplaceAll(listOf(send))), router.commands)
            verify(exactly = 0) { showError(any()) }
        }

    @Test
    fun bareAddressOpensSendOverHomeWithoutAmountOrMemo() =
        runTest {
            accepted[TRANSPARENT_ADDRESS] = Zip321ParseUriValidation.SingleAddress(TRANSPARENT_ADDRESS)
            coEvery { validateAddress(TRANSPARENT_ADDRESS) } returns AddressType.Transparent

            useCase(TRANSPARENT_ADDRESS)

            val send = Send(recipientAddress = TRANSPARENT_ADDRESS, recipientAddressType = RouteAddressType.TRANSPARENT)
            assertEquals(listOf<BaseNavigationCommand>(NavigationCommand.ReplaceAll(listOf(send))), router.commands)
            verify(exactly = 0) { showError(any()) }
        }

    @Test
    fun addressTheParserRejectsIsStillValidatedByTheSdk() =
        runTest {
            coEvery { validateAddress(TRANSPARENT_ADDRESS) } returns AddressType.Tex

            useCase(TRANSPARENT_ADDRESS)

            val send = Send(recipientAddress = TRANSPARENT_ADDRESS, recipientAddressType = RouteAddressType.TEX)
            assertEquals(listOf<BaseNavigationCommand>(NavigationCommand.ReplaceAll(listOf(send))), router.commands)
        }

    @Test
    fun addressTheSdkRejectsIsReportedAsInvalidContent() =
        runTest {
            accepted[TRANSPARENT_ADDRESS] = Zip321ParseUriValidation.SingleAddress(TRANSPARENT_ADDRESS)

            useCase(TRANSPARENT_ADDRESS)

            assertEquals(emptyList(), router.commands)
            verify { showError(stringRes(R.string.scan_sharedContentInvalid)) }
        }

    @Test
    fun stopsAtTheFirstCandidateThatResolves() =
        runTest {
            accepted[ZIP321_URI] = zip321Valid(amount = null, memo = null)
            coEvery { validateAddress(TRANSPARENT_ADDRESS) } returns AddressType.Transparent

            useCase("$ZIP321_URI and also $SHIELDED_ADDRESS")

            assertEquals(listOf(ZIP321_URI), tried)
        }

    @Test
    fun requestForSeveralPaymentsIsRejected() =
        runTest {
            accepted[ZIP321_URI] = zip321Valid(amount = "0.0001", memo = null, payments = 2)
            coEvery { validateAddress(TRANSPARENT_ADDRESS) } returns AddressType.Transparent

            useCase(ZIP321_URI)

            assertEquals(emptyList(), router.commands)
            verify { showError(stringRes(R.string.scan_sharedContentInvalid)) }
        }

    @Test
    fun withoutAWalletTheShareIsNotReportedAsInvalid() =
        runTest {
            coEvery { synchronizerProvider.getSynchronizerOrNull() } returns null

            useCase(TRANSPARENT_ADDRESS)

            verify(exactly = 1) { showError(stringRes(R.string.scan_sharedNoWallet)) }
            verify(exactly = 0) { showError(stringRes(R.string.scan_sharedContentInvalid)) }
            assertEquals(emptyList(), tried)
        }

    // endregion

    // region gift cards and view-only accounts

    @Test
    fun giftCardLinkOnlyOpensTheGiftCardScanner() =
        runTest {
            useCase("Here is your gift: $GIFT_CARD_LINK")

            verify(exactly = 1) { giftCardLinkStore.requestInAppScan() }
            assertEquals(emptyList(), tried)
            assertEquals(emptyList(), router.commands)
            verify(exactly = 0) { showError(any()) }
        }

    @Test
    fun giftCardQrCodeInAnImageOnlyOpensTheGiftCardScanner() =
        runTest {
            coEvery { imageUriToQrCodeConverter(context, image) } returns ImageToQrCodeResult.SingleCode(GIFT_CARD_LINK)

            useCase(image)

            verify(exactly = 1) { giftCardLinkStore.requestInAppScan() }
            assertEquals(emptyList(), tried)
            assertEquals(emptyList(), router.commands)
        }

    @Test
    fun accountThatCannotSpendDoesNotGetSend() =
        runTest {
            coEvery { rejectIfSelectedAccountCannotSpend(any()) } returns true
            accepted[TRANSPARENT_ADDRESS] = Zip321ParseUriValidation.SingleAddress(TRANSPARENT_ADDRESS)
            coEvery { validateAddress(TRANSPARENT_ADDRESS) } returns AddressType.Transparent

            useCase(TRANSPARENT_ADDRESS)

            assertEquals(emptyList(), router.commands)
            verify(exactly = 0) { showError(any()) }
        }

    @Test
    fun accountThatCannotSpendStillHearsThatContentHoldsNoPayment() =
        runTest {
            coEvery { rejectIfSelectedAccountCannotSpend(any()) } returns true

            useCase("Hello world")

            verify(exactly = 1) { showError(stringRes(R.string.scan_sharedContentInvalid)) }
            coVerify(exactly = 0) { rejectIfSelectedAccountCannotSpend(any()) }
        }

    // endregion

    // region images

    @Test
    fun qrCodeInAnImageIsHandledAsText() =
        runTest {
            coEvery { imageUriToQrCodeConverter(context, image) } returns
                ImageToQrCodeResult.SingleCode(TRANSPARENT_ADDRESS)

            useCase(image)

            assertEquals(listOf(TRANSPARENT_ADDRESS), tried)
        }

    @Test
    fun imageWithoutAQrCodeIsReportedAsInvalidImage() =
        runTest {
            coEvery { imageUriToQrCodeConverter(context, image) } returns ImageToQrCodeResult.NoCode

            useCase(image)

            verify { showError(stringRes(R.string.scan_invalidImage)) }
            assertEquals(emptyList(), tried)
        }

    @Test
    fun imageWithSeveralQrCodesIsReported() =
        runTest {
            coEvery { imageUriToQrCodeConverter(context, image) } returns ImageToQrCodeResult.MultipleCodes

            useCase(image)

            verify { showError(stringRes(R.string.scan_severalCodesFound)) }
            assertEquals(emptyList(), tried)
        }

    // endregion

    /** A real [PaymentRequest], so that the amount conversion under test is the real one. */
    private fun zip321Valid(
        amount: String?,
        memo: String?,
        payments: Int = 1
    ): Zip321ParseUriValidation.Valid =
        Zip321ParseUriValidation.Valid(
            zip321Uri = ZIP321_URI,
            payment =
                PaymentRequest(
                    List(payments) {
                        Payment(
                            recipientAddress =
                                RecipientAddress(
                                    value = TRANSPARENT_ADDRESS,
                                    network = ParserContext.TESTNET,
                                    validating = { true }
                                ),
                            nonNegativeAmount = amount?.let { NonNegativeAmount(it) },
                            memo = memo?.let { MemoBytes(it) },
                            label = null,
                            message = null,
                            otherParams = null
                        )
                    }
                )
        )

    private companion object {
        /** A real testnet transparent address, as used by the QR Scan manual test. */
        const val TRANSPARENT_ADDRESS = "tmEjY6KfCryQhJ1hKSGiA7p8EeVggpvN78r"

        /** Only needs to be long enough and alphanumeric: the validators are mocked. */
        const val SHIELDED_ADDRESS = "zs1abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz01"

        const val ZIP321_URI = "zcash:$TRANSPARENT_ADDRESS?amount=1"
        const val ZIP321_URI_WITH_MEMO = "zcash:$TRANSPARENT_ADDRESS?amount=1&memo=aGVsbG8"

        const val GIFT_CARD_LINK = "https://gift.zodl.com/#v=1&key=zgift1qqqqqqqqqqqqqqqqqqqqqqqqqqqq&height=3100000"
    }

    private class FakeNavigationRouter : NavigationRouter {
        val commands = mutableListOf<BaseNavigationCommand>()

        override fun forward(vararg routes: Any) {
            commands += NavigationCommand.Forward(routes.toList())
        }

        override fun replace(vararg routes: Any) {
            commands += NavigationCommand.Replace(routes.toList())
        }

        override fun replaceAll(vararg routes: Any) {
            commands += NavigationCommand.ReplaceAll(routes.toList())
        }

        override fun replaceFrom(route: KClass<*>, vararg routes: Any) {
            commands += NavigationCommand.ReplaceFrom(route, routes.toList())
        }

        override fun back() {
            commands += NavigationCommand.Back
        }

        override fun backTo(route: KClass<*>) {
            commands += NavigationCommand.BackTo(route)
        }

        override fun custom(block: (NavBackStackEntry?) -> NavigationCommand?) = Unit

        override fun backToRoot() {
            commands += NavigationCommand.BackToRoot
        }

        override fun observePipeline(): Flow<BaseNavigationCommand> = emptyFlow()
    }
}
