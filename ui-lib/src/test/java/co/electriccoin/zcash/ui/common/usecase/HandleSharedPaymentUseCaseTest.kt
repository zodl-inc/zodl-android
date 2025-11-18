package co.electriccoin.zcash.ui.common.usecase

import android.content.Context
import android.net.Uri
import androidx.navigation.NavBackStackEntry
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.type.AddressType
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.WalletRepository
import co.electriccoin.zcash.ui.common.usecase.Zip321ParseUriValidationUseCase.Zip321ParseUriValidation
import co.electriccoin.zcash.ui.common.viewmodel.SecretState
import co.electriccoin.zcash.ui.screen.scan.ImageToQrCodeResult
import co.electriccoin.zcash.ui.screen.scan.ImageUriToQrCodeConverter
import co.electriccoin.zcash.ui.screen.send.Send
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
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
 * Coverage of how [HandleSharedPaymentUseCase] turns arbitrary shared content into payment
 * candidates, and what it does with each outcome. The validators are mocked - what matters here is
 * which substrings reach them, in what order, and that nothing else does.
 *
 * Candidate order is asserted through [tried], recorded by the mocked
 * [Zip321ParseUriValidationUseCase], since that is the first thing every candidate is passed to.
 * A test declares the one candidate that should resolve by adding it to [resolves]; everything else
 * is rejected, so each test states only what it is actually about.
 */
class HandleSharedPaymentUseCaseTest {
    private val context = mockk<Context>(relaxed = true)
    private val image = mockk<Uri>(relaxed = true)
    private val imageUriToQrCodeConverter = mockk<ImageUriToQrCodeConverter>()
    private val synchronizer = mockk<Synchronizer>()
    private val synchronizerProvider = mockk<SynchronizerProvider>()
    private val walletRepository = mockk<WalletRepository>()
    private val zip321 = mockk<Zip321ParseUriValidationUseCase>()
    private val router = RecordingNavigationRouter()
    private val showError = mockk<ShowErrorUseCase>(relaxed = true)

    /** Every candidate the use case tried, in the order it tried them. */
    private val tried = mutableListOf<String>()

    /** Candidates that ZIP-321 should accept; anything absent is rejected. */
    private val resolves = mutableMapOf<String, Zip321ParseUriValidation>()

    private val useCase =
        HandleSharedPaymentUseCase(
            context = context,
            imageUriToQrCodeConverter = imageUriToQrCodeConverter,
            synchronizerProvider = synchronizerProvider,
            walletRepository = walletRepository,
            zip321ParseUriValidationUseCase = zip321,
            navigationRouter = router,
            showError = showError
        )

    @BeforeTest
    fun setUp() {
        coEvery { synchronizerProvider.getSynchronizerOrNull() } returns synchronizer
        every { walletRepository.secretState } returns MutableStateFlow(SecretState.READY)
        // A single stub, so that a per-candidate override can never bypass the recording.
        coEvery { zip321(any()) } answers {
            val candidate = firstArg<String>()
            tried += candidate
            resolves[candidate] ?: Zip321ParseUriValidation.Invalid
        }
        coEvery { synchronizer.validateAddress(any()) } returns AddressType.Invalid()
    }

    // region candidate extraction

    @Test
    fun bareAddressIsTheOnlyCandidate() =
        runBlocking {
            useCase(TRANSPARENT_ADDRESS)

            assertEquals(listOf(TRANSPARENT_ADDRESS), tried)
        }

    @Test
    fun addressEmbeddedInASentenceIsFound() =
        runBlocking {
            useCase("Please pay me at $TRANSPARENT_ADDRESS thanks")

            // "Please", "pay", "me", "at" and "thanks" are all too short to be an address.
            assertEquals(listOf(TRANSPARENT_ADDRESS), tried)
        }

    @Test
    fun quotedAndBracketedAddressesAreFound() =
        runBlocking {
            useCase("""Use "$TRANSPARENT_ADDRESS" or ($TRANSPARENT_ADDRESS)""")

            // Both occurrences normalise to the same candidate, so it is only validated once.
            assertEquals(listOf(TRANSPARENT_ADDRESS), tried)
        }

    @Test
    fun zip321UriKeepsItsQueryParameters() =
        runBlocking {
            useCase(ZIP321_URI_WITH_MEMO)

            assertEquals(listOf(ZIP321_URI_WITH_MEMO), tried)
        }

    @Test
    fun sentencePunctuationAfterAUriIsRetriedWithoutIt() =
        runBlocking {
            useCase("Send it to $ZIP321_URI.")

            // A trailing period cannot be told apart from a ZIP-321 query value, so the match is
            // tried as found and then again trimmed - the trimmed form is what actually parses.
            assertEquals(listOf("$ZIP321_URI.", ZIP321_URI), tried)
        }

    @Test
    fun zip321UriIsTriedBeforeABareAddressAppearingEarlier() =
        runBlocking {
            useCase("$TRANSPARENT_ADDRESS or better $ZIP321_URI")

            // The URI also carries the amount, so it must win even though it appears second.
            assertEquals(listOf(ZIP321_URI, TRANSPARENT_ADDRESS), tried)
        }

    @Test
    fun proseWithoutAPaymentIsNeverValidated() =
        runBlocking {
            useCase("Hello world, just checking in about that thing we discussed")

            // Nothing may reach the validators - every candidate costs an SDK address lookup.
            assertEquals(emptyList(), tried)
            verify { showError(any()) }
        }

    // endregion

    // region what is opened

    @Test
    fun zip321UriOpensSendWithItsAmountAndMemo() =
        runBlocking {
            resolves[ZIP321_URI_WITH_MEMO] = zip321Valid(amount = "0.0001", memo = "hello")
            coEvery { synchronizer.validateAddress(TRANSPARENT_ADDRESS) } returns AddressType.Transparent

            useCase(ZIP321_URI_WITH_MEMO)

            // 0.0001 ZEC is 10 000 zatoshi. The payment rides on the route rather than through
            // PrefillSendUseCase, whose rendezvous channel does not survive Send being remounted.
            assertEquals(
                listOf<BaseNavigationCommand>(
                    NavigationCommand.ReplaceAll(
                        listOf(
                            Send(
                                recipientAddress = TRANSPARENT_ADDRESS,
                                recipientAddressType = RouteAddressType.TRANSPARENT,
                                amount = 10_000L,
                                memo = "hello"
                            )
                        )
                    )
                ),
                router.commands
            )
            verify(exactly = 0) { showError(any()) }
        }

    @Test
    fun bareAddressOpensSendWithNoAmountOrMemo() =
        runBlocking {
            resolves[TRANSPARENT_ADDRESS] = Zip321ParseUriValidation.SingleAddress(TRANSPARENT_ADDRESS)
            coEvery { synchronizer.validateAddress(TRANSPARENT_ADDRESS) } returns AddressType.Transparent

            useCase(TRANSPARENT_ADDRESS)

            assertEquals(
                listOf<BaseNavigationCommand>(
                    NavigationCommand.ReplaceAll(
                        listOf(
                            Send(
                                recipientAddress = TRANSPARENT_ADDRESS,
                                recipientAddressType = RouteAddressType.TRANSPARENT
                            )
                        )
                    )
                ),
                router.commands
            )
            verify(exactly = 0) { showError(any()) }
        }

    @Test
    fun sendIsOpenedOnTopOfHomeRatherThanReplacingWhatIsCurrent() =
        runBlocking {
            // The share may have launched the app straight into this, so Home is what is current and
            // replacing it would leave Back exiting the app. It is also never a forward: a share
            // must not stack up behind whatever screen the user happened to be on.
            resolves[TRANSPARENT_ADDRESS] = Zip321ParseUriValidation.SingleAddress(TRANSPARENT_ADDRESS)
            coEvery { synchronizer.validateAddress(TRANSPARENT_ADDRESS) } returns AddressType.Transparent

            useCase(TRANSPARENT_ADDRESS)

            assertEquals(1, router.commands.size)
            assertEquals(NavigationCommand.ReplaceAll::class, router.commands.first()::class)
        }

    @Test
    fun aSharedPaymentNeverReachesAProposal() =
        runBlocking {
            // The intent filters are exported, so any app can deliver a payment without a share
            // sheet. Send is the furthest it may get - never a transaction awaiting confirmation.
            resolves[ZIP321_URI] = zip321Valid(amount = "0.0001", memo = null)
            coEvery { synchronizer.validateAddress(TRANSPARENT_ADDRESS) } returns AddressType.Transparent

            useCase(ZIP321_URI)

            val opened = (router.commands.single() as NavigationCommand.ReplaceAll).routes
            assertEquals(1, opened.size)
            assertEquals(Send::class, opened.single()::class)
        }

    @Test
    fun addressRejectedBySdkValidationFallsThroughToTheError() =
        runBlocking {
            // ZIP-321 reports a single recipient, but the SDK then rejects the address itself.
            resolves[TRANSPARENT_ADDRESS] = Zip321ParseUriValidation.SingleAddress(TRANSPARENT_ADDRESS)

            useCase(TRANSPARENT_ADDRESS)

            assertEquals(emptyList(), router.commands)
            verify { showError(any()) }
        }

    @Test
    fun stopsAtTheFirstCandidateThatResolves() =
        runBlocking {
            resolves[ZIP321_URI] = zip321Valid(amount = null, memo = null)
            coEvery { synchronizer.validateAddress(TRANSPARENT_ADDRESS) } returns AddressType.Transparent

            useCase("$ZIP321_URI and also $SHIELDED_ADDRESS")

            // The URI sorts first and resolves, so the trailing address is never validated.
            assertEquals(listOf(ZIP321_URI), tried)
        }

    @Test
    fun multiPaymentRequestIsRejectedRatherThanPartlyApplied() =
        runBlocking {
            // Send shows one recipient, so opening it with the first of several would drop the rest
            // of a request the sender considers whole.
            resolves[ZIP321_URI] = zip321Valid(amount = "0.0001", memo = null, payments = 2)
            coEvery { synchronizer.validateAddress(TRANSPARENT_ADDRESS) } returns AddressType.Transparent

            useCase(ZIP321_URI)

            assertEquals(emptyList(), router.commands)
            verify { showError(any()) }
        }

    @Test
    fun sharedPaymentIsDroppedWhenTheWalletGoesAwayWhileItIsInFlight() =
        runBlocking {
            // first { READY } alone would park here forever, and then resume onto whatever wallet
            // was created next.
            every { walletRepository.secretState } returns MutableStateFlow(SecretState.NONE)
            resolves[TRANSPARENT_ADDRESS] = Zip321ParseUriValidation.SingleAddress(TRANSPARENT_ADDRESS)
            coEvery { synchronizer.validateAddress(TRANSPARENT_ADDRESS) } returns AddressType.Transparent

            useCase(TRANSPARENT_ADDRESS)

            assertEquals(emptyList(), router.commands)
            assertEquals(emptyList(), tried)
            verify(exactly = 0) { showError(any()) }
        }

    @Test
    fun sharedContentIsIgnoredWhenThereIsNoWallet() =
        runBlocking {
            coEvery { synchronizerProvider.getSynchronizerOrNull() } returns null

            useCase(TRANSPARENT_ADDRESS)

            // Having no wallet is not a problem with what was shared, so it is not reported as one.
            verify(exactly = 0) { showError(any()) }
            assertEquals(emptyList(), tried)
        }

    // endregion

    // region shared images

    @Test
    fun qrCodeInASharedImageIsValidatedAsText() =
        runBlocking {
            coEvery { imageUriToQrCodeConverter(context, image) } returns
                ImageToQrCodeResult.SingleCode(TRANSPARENT_ADDRESS)

            useCase(image)

            assertEquals(listOf(TRANSPARENT_ADDRESS), tried)
        }

    @Test
    fun imageWithoutAQrCodeReportsAnInvalidImage() =
        runBlocking {
            coEvery { imageUriToQrCodeConverter(context, image) } returns ImageToQrCodeResult.NoCode

            useCase(image)

            verify { showError(any()) }
            assertEquals(emptyList(), tried)
        }

    @Test
    fun imageWithSeveralQrCodesIsReportedRatherThanGuessed() =
        runBlocking {
            coEvery { imageUriToQrCodeConverter(context, image) } returns ImageToQrCodeResult.MultipleCodes

            useCase(image)

            verify { showError(any()) }
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

        /** Only needs to be long enough and alphanumeric - the validators are mocked. */
        const val SHIELDED_ADDRESS = "zs1abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz01"

        const val ZIP321_URI = "zcash:$TRANSPARENT_ADDRESS?amount=1"
        const val ZIP321_URI_WITH_MEMO = "zcash:$TRANSPARENT_ADDRESS?amount=1&memo=aGVsbG8"
    }
}

private class RecordingNavigationRouter : NavigationRouter {
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
