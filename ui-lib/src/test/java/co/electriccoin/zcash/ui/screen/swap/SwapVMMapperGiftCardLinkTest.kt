package co.electriccoin.zcash.ui.screen.swap

import cash.z.ecc.android.sdk.model.FiatCurrency
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.AddressBookContact
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.SwapDirection
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.DEFAULT_SLIPPAGE
import co.electriccoin.zcash.ui.common.repository.EnhancedABContact
import co.electriccoin.zcash.ui.common.repository.GiftCardSecretFixture
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
 * A gift card link typed or pasted into the swap address field, in either direction, shows the gift card error and
 * keeps the quote button disabled. So does a saved contact whose address is a gift card link.
 */
class SwapVMMapperGiftCardLinkTest {
    private val mapper = SwapVMMapper()

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
    fun giftCardLinkShowsErrorAndDisablesQuote() {
        SwapDirection.entries.forEach { direction ->
            GiftCardSecretFixture.all.forEach { (name, link) ->
                val state = mapper.createState(internalState(addressText = link, direction = direction), callbacks)

                assertEquals(stringRes(R.string.swap_error_giftCardLink), state.address.error, name)
                assertFalse(state.primaryButton?.isEnabled ?: true, name)
            }
        }
    }

    @Test
    fun ordinaryAddressKeepsQuoteEnabled() {
        SwapDirection.entries.forEach { direction ->
            GiftCardSecretFixture.ordinaryAddresses.forEach { address ->
                val state =
                    mapper.createState(internalState(addressText = address, direction = direction), callbacks)

                assertNull(state.address.error, address)
                assertTrue(state.primaryButton?.isEnabled == true, address)
            }
        }
    }

    @Test
    fun giftCardLinkContactDisablesQuote() {
        GiftCardSecretFixture.all.forEach { (name, link) ->
            val contact =
                EnhancedABContact(
                    contact =
                        AddressBookContact(
                            name = "Card",
                            address = link,
                            lastUpdated = Instant.fromEpochMilliseconds(0),
                            chain = "btc"
                        ),
                    blockchain = SwapAssetTestFixture.blockchain("btc")
                )

            val state = mapper.createState(internalState(addressText = "", selectedContact = contact), callbacks)

            assertFalse(state.primaryButton?.isEnabled ?: true, name)
        }
    }

    private fun internalState(
        addressText: String,
        direction: SwapDirection = SwapDirection.SWAP_INTO_ZEC,
        selectedContact: EnhancedABContact? = null,
    ) = InternalStateImpl(
        account = account,
        swapAsset = SwapAssetTestFixture.asset(),
        currencyType = CurrencyType.TOKEN,
        amountTextState = NumberTextFieldInnerState.fromAmount(BigDecimal("1")),
        addressText = addressText,
        slippage = DEFAULT_SLIPPAGE,
        swapAssets = SwapAssetTestFixture.assetsData(),
        isRequestingQuote = false,
        selectedContact = selectedContact,
        swapDirection = direction,
        isEphemeralAddressLocked = false
    )

    private val callbacks =
        SwapStateCallbacks(
            onBack = {},
            onSwapInfoClick = {},
            onSwapAssetPickerClick = {},
            onSwapCurrencyTypeClick = {},
            onSlippageClick = {},
            onRequestSwapQuoteClick = { _, _, _ -> },
            onTryAgainClick = {},
            onAddressChange = {},
            onTextFieldChange = {},
            onQrCodeScannerClick = {},
            onAddressBookClick = {},
            onDeleteSelectedContactClick = {},
            onBalanceButtonClick = {},
            onChangeButtonClick = {},
            onAddressClick = {},
        )
}
