package co.electriccoin.zcash.ui.screen.pay

import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.AddressBookContact
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.DEFAULT_SLIPPAGE
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * A gift card link typed or pasted into the pay address field shows the gift card error and keeps the review button
 * disabled. So does a saved contact whose address is a gift card link.
 */
class ExactOutputVMMapperGiftCardLinkTest {
    private val mapper = ExactOutputVMMapper()

    /** An account that can spend any amount, so only the address decides whether the button is enabled. */
    private val account =
        mockk<WalletAccount> {
            every { canSpend(any()) } returns true
            every { loadedBalances } returns null
        }

    @BeforeTest
    fun setUp() {
        mockkObject(FiatCurrency.USD)
        every { FiatCurrency.USD.symbol } returns "$"
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(FiatCurrency.USD)
    }

    @Test
    fun giftCardLinkShowsErrorAndDisablesReview() {
        GIFT_LINKS.forEach { link ->
            val state = mapper.createState(internalState(address = link), callbacks)

            assertEquals(stringRes(R.string.swap_error_giftCardLink), state.address.error)
            assertFalse(state.primaryButton?.isEnabled ?: true)
        }
    }

    @Test
    fun ordinaryAddressKeepsReviewEnabled() {
        val state = mapper.createState(internalState(address = "0xordinaryaddress"), callbacks)

        assertNull(state.address.error)
        assertTrue(state.primaryButton?.isEnabled == true)
    }

    @Test
    fun giftCardLinkContactDisablesReview() {
        val contact =
            EnhancedABContact(
                contact =
                    AddressBookContact(
                        name = "Card",
                        address = GIFT_LINKS.first(),
                        lastUpdated = Instant.fromEpochMilliseconds(0),
                        chain = "btc"
                    ),
                blockchain = SwapAssetTestFixture.blockchain("btc")
            )

        val state = mapper.createState(internalState(address = "", selectedABContact = contact), callbacks)

        assertFalse(state.primaryButton?.isEnabled ?: true)
    }

    private fun internalState(
        address: String,
        selectedABContact: EnhancedABContact? = null,
    ) = InternalStateImpl(
        address = address,
        isABHintVisible = false,
        selectedABContact = selectedABContact,
        asset = SwapAssetTestFixture.asset(),
        amount = NumberTextFieldInnerState.fromAmount(BigDecimal("1")),
        fiatAmount = NumberTextFieldInnerState(),
        slippage = DEFAULT_SLIPPAGE,
        isRequestingQuote = false,
        account = account,
        swapAssets = SwapAssetTestFixture.assetsData(),
        isEphemeralAddressLocked = false
    )

    private val callbacks =
        ExactOutputStateCallbacks(
            onBack = {},
            onSwapInfoClick = {},
            onSwapAssetPickerClick = {},
            onSlippageClick = {},
            onRequestSwapQuoteClick = { _, _, _ -> },
            onTryAgainClick = {},
            onAddressChange = {},
            onTextFieldChange = { _, _ -> },
            onQrCodeScannerClick = {},
            onAddressBookClick = {},
            onDeleteSelectedContactClick = {},
        )

    private companion object {
        val GIFT_LINKS =
            listOf(
                "https://gift.zodl.com/#v=1&key=zgift1testsecret&height=1",
                "  HTTPS://GIFT.ZODL.COM#v=1&key=zgift1testsecret&height=1 ",
                "https://link.vizor.cash/payment-links/open#v1=testsecret",
            )
    }
}
