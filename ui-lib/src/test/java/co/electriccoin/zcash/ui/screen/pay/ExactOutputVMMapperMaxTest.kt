package co.electriccoin.zcash.ui.screen.pay

import cash.z.ecc.android.sdk.model.FiatCurrency
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.DEFAULT_SLIPPAGE
import co.electriccoin.zcash.ui.design.component.InnerTextFieldState
import co.electriccoin.zcash.ui.design.component.MaxFeedback
import co.electriccoin.zcash.ui.design.component.MaxVariant
import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import co.electriccoin.zcash.ui.design.component.TextSelection
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.stringResByDynamicNumber
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Max chip on the Pay amount row (enablement, no spinner, click), Review gating and the feedback line. */
class ExactOutputVMMapperMaxTest {
    private val mapper = ExactOutputVMMapper()
    private var maxClicks = 0
    private var lastAmountChange: Pair<NumberTextFieldInnerState, NumberTextFieldInnerState>? = null

    @BeforeTest
    fun setUp() {
        mockkObject(FiatCurrency.USD)
        every { FiatCurrency.USD.symbol } returns "$"
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(FiatCurrency.USD)
    }

    private fun account(spendable: Long?, pending: Long? = 0) =
        mockk<WalletAccount> {
            every { canSpend(any()) } returns true
            every { loadedBalances } returns null
            every { spendableShieldedBalance } returns spendable?.let { Zatoshi(it) }
            every { pendingShieldedBalance } returns pending?.let { Zatoshi(it) }
            every { isShieldingAvailable } returns false
            every { isShieldedPending } returns ((pending ?: 0) > 0)
        }

    private fun nothingAccount(isShieldingAvailable: Boolean?, isShieldedPending: Boolean) =
        mockk<WalletAccount> {
            every { canSpend(any()) } returns true
            every { loadedBalances } returns null
            every { spendableShieldedBalance } returns Zatoshi(0)
            every { pendingShieldedBalance } returns Zatoshi(if (isShieldedPending) 5 else 0)
            every { this@mockk.isShieldingAvailable } returns isShieldingAvailable
            every { this@mockk.isShieldedPending } returns isShieldedPending
        }

    private fun state(
        account: WalletAccount? = account(spendable = 100),
        tokenPrice: BigDecimal? = BigDecimal("2"),
        zecPrice: BigDecimal? = BigDecimal("30"),
        isRequestingQuote: Boolean = false,
    ) = payState(account, tokenPrice, zecPrice) { copy(isRequestingQuote = isRequestingQuote) }.max

    /** The mapped state for an account and prices; [edit] adjusts the rest of the internal state. */
    private fun payState(
        account: WalletAccount? = account(spendable = 100),
        tokenPrice: BigDecimal? = BigDecimal("2"),
        zecPrice: BigDecimal? = BigDecimal("30"),
        address: String = "",
        edit: InternalStateImpl.() -> InternalStateImpl = { this },
    ) = mapper
        .createState(
            InternalStateImpl(
                address = address,
                isABHintVisible = false,
                selectedABContact = null,
                asset = SwapAssetTestFixture.asset(usdPrice = tokenPrice),
                amount = NumberTextFieldInnerState(),
                fiatAmount = NumberTextFieldInnerState(),
                slippage = DEFAULT_SLIPPAGE,
                isRequestingQuote = false,
                account = account,
                swapAssets =
                    SwapAssetTestFixture.assetsData(zecAsset = zecPrice?.let { zecAssetWith(it) }),
                isEphemeralAddressLocked = false,
            ).edit(),
            callbacks
        )

    private fun zecAssetWith(price: BigDecimal) =
        SwapAssetTestFixture.asset(tokenTicker = "zec", chainTicker = "zec", usdPrice = price)

    @Test
    fun enabledWithSpendableFundsAndBothPrices() {
        val max = assertNotNull(state())
        assertTrue(max.isEnabled)
        max.onClick()
        assertEquals(1, maxClicks)
    }

    @Test
    fun disabledWithoutTokenPrice() {
        assertFalse(assertNotNull(state(tokenPrice = null)).isEnabled)
    }

    @Test
    fun disabledWithoutZecAsset() {
        assertFalse(assertNotNull(state(zecPrice = null)).isEnabled)
    }

    @Test
    fun disabledWithNonPositivePrice() {
        assertFalse(assertNotNull(state(tokenPrice = BigDecimal.ZERO)).isEnabled)
        assertFalse(assertNotNull(state(zecPrice = BigDecimal.ZERO)).isEnabled)
    }

    @Test
    fun disabledWithoutSpendableFunds() {
        val max = assertNotNull(state(account = account(spendable = 0, pending = 5)))
        assertFalse(max.isEnabled)
    }

    @Test
    fun disabledWithoutASpinnerWhileBalancesAreUndetermined() {
        listOf(null, account(spendable = null)).forEach {
            assertFalse(assertNotNull(state(account = it)).isEnabled)
        }
    }

    @Test
    fun disabledWhileRequestingQuote() {
        assertFalse(assertNotNull(state(isRequestingQuote = true)).isEnabled)
    }

    @Test
    fun feedbackIsPassedThroughToTheSlot() {
        assertNull(payState().maxFeedback)
        assertEquals(MaxFeedback.Hint, payState { copy(maxFeedback = MaxFeedback.Hint) }.maxFeedback)
        val error = MaxFeedback.Error(MaxVariant.SEND)
        assertEquals(error, payState { copy(maxFeedback = error) }.maxFeedback)
    }

    @Test
    fun reviewNeedsShieldedSpendableFunds() {
        assertTrue(reviewEnabled(account(spendable = 100)))
        // syncing / undetermined, confirming, transparent-only and empty wallets
        assertFalse(reviewEnabled(null))
        assertFalse(reviewEnabled(account(spendable = null)))
        assertFalse(reviewEnabled(account(spendable = 0, pending = 5)))
        assertFalse(reviewEnabled(nothingAccount(isShieldingAvailable = true, isShieldedPending = false)))
        assertFalse(reviewEnabled(nothingAccount(isShieldingAvailable = false, isShieldedPending = false)))
    }

    @Test
    fun reviewStillNeedsTheExistingRules() {
        assertFalse(reviewEnabled(account(spendable = 100), address = ""))
        assertFalse(reviewEnabled(account(spendable = 100), amount = BigDecimal.ZERO))
        assertFalse(reviewEnabled(account(spendable = 100), amount = null))
    }

    @Test
    fun typedAmountNeverGainsAGroupingSeparator() {
        val state = payState()
        val typed =
            NumberTextFieldInnerState(
                innerTextFieldState = InnerTextFieldState(value = stringRes("1234.56"), selection = TextSelection.End),
                amount = BigDecimal("1234.56"),
                lastValidAmount = BigDecimal("1234.56")
            )
        state.amount.onValueChange(typed)
        val (amount, fiat) = assertNotNull(lastAmountChange)
        assertEquals(typed, amount)
        // 1234.56 of a $2 token is $2469.12, written without the "2,469.12" separator
        assertEquals(
            stringResByDynamicNumber(BigDecimal("2469.12"), includeGroupingSeparator = false),
            fiat.innerTextFieldState.value
        )
        assertEquals(BigDecimal("2469.12"), fiat.amount)
    }

    @Test
    fun typedFiatNeverGainsAGroupingSeparatorInTheTokenField() {
        val state = payState()
        val typed =
            NumberTextFieldInnerState(
                innerTextFieldState = InnerTextFieldState(value = stringRes("2469.12"), selection = TextSelection.End),
                amount = BigDecimal("2469.12"),
                lastValidAmount = BigDecimal("2469.12")
            )
        state.amountFiat.onValueChange(typed)
        val (amount, _) = assertNotNull(lastAmountChange)
        assertEquals(
            stringResByDynamicNumber(BigDecimal("1234.56"), includeGroupingSeparator = false),
            amount.innerTextFieldState.value
        )
    }

    private fun reviewEnabled(
        account: WalletAccount?,
        address: String = "bc1qaddress",
        amount: BigDecimal? = BigDecimal("1"),
    ) = payState(account = account, address = address) {
        copy(amount = NumberTextFieldInnerState(amount = amount))
    }.primaryButton!!.isEnabled

    @Test
    fun fiatShowsADashOnlyOncePricesLoadedWithoutAUsablePrice() {
        assertTrue(payState(tokenPrice = null).isFiatPriceUnavailable)
        assertTrue(payState(zecPrice = null).isFiatPriceUnavailable)
        assertFalse(payState().isFiatPriceUnavailable)
        assertFalse(
            payState(tokenPrice = null) { copy(swapAssets = swapAssets.copy(isLoading = true)) }.isFiatPriceUnavailable
        )
    }

    private val callbacks
        get() =
            ExactOutputStateCallbacks(
                onBack = {},
                onSwapInfoClick = {},
                onSwapAssetPickerClick = {},
                onSlippageClick = {},
                onRequestSwapQuoteClick = { _, _, _ -> },
                onTryAgainClick = {},
                onAddressChange = {},
                onTextFieldChange = { amount, fiat -> lastAmountChange = amount to fiat },
                onQrCodeScannerClick = {},
                onAddressBookClick = {},
                onDeleteSelectedContactClick = {},
                onMaxClick = { maxClicks++ },
            )
}
