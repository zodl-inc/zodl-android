package co.electriccoin.zcash.ui.screen.swap.quote

import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.DynamicSwapAddress
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.SwapAddress
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.SwapQuote
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.StyledStringResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The quote sheet's "Swap from" / "Pay from" row must name whichever account is selected, not a
 * hardcoded "Zodl" — a Ledger or Keystone account has to be named correctly. The row itself is
 * only shown for a cross-chain quote; a ZEC-to-ZEC on-ramp quote drops it entirely.
 *
 * Runs under Robolectric because [SwapQuoteVMMapper] resolves `FiatCurrency.USD.symbol`, which
 * needs the real ICU currency data that a plain JVM unit test does not have.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SwapQuoteVMMapperTest {
    private val defaultUuid = AccountUuid.new(ByteArray(16) { it.toByte() })
    private val sdkAccount = Account.new(defaultUuid)
    private val mapper = SwapQuoteVMMapper()
    private val destinationAsset = SwapAssetTestFixture.asset(tokenTicker = "usdc", chainTicker = "eth")

    @Test
    fun theFromRowNamesTheSelectedZashiAccount() {
        val fromRow = createItems(account = zashi(), destinationAsset = destinationAsset).first()

        assertEquals(R.string.accounts_zashi, fromRow.title.resourceId())
    }

    @Test
    fun theFromRowNamesTheSelectedKeystoneAccount() {
        val fromRow = createItems(account = keystone(), destinationAsset = destinationAsset).first()

        assertEquals(R.string.accounts_keystone, fromRow.title.resourceId())
    }

    @Test
    fun theFromRowNamesTheSelectedLedgerAccount() {
        val fromRow = createItems(account = ledger(), destinationAsset = destinationAsset).first()

        assertEquals(R.string.accounts_ledger, fromRow.title.resourceId())
    }

    @Test
    fun theFromRowIsAbsentWhenTheDestinationAssetIsZec() {
        val items = createItems(account = zashi(), destinationAsset = SwapAssetTestFixture.zecAsset())

        assertEquals(
            emptyList(),
            items.filter { it.description.resourceId() == R.string.swapAndPay_swapFrom }
        )
    }

    private fun createItems(
        account: WalletAccount,
        destinationAsset: SwapAsset,
        mode: SwapMode = SwapMode.EXACT_INPUT
    ): List<SwapQuoteInfoItem> {
        val state =
            mapper.createState(
                state =
                    SwapQuoteInternalState(
                        proposal = null,
                        quote = FakeSwapQuote(destinationAsset = destinationAsset, mode = mode),
                        account = account
                    ),
                onBack = {},
                onSubmitQuoteClick = {},
                onNavigateToOnRampSwap = {}
            )
        return state.items
    }

    private fun zashi(isSelected: Boolean = true) =
        ZashiAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u",
            transparentAddress = "t",
            saplingAddress = "s",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = isSelected,
        )

    private fun keystone(isSelected: Boolean = true) =
        KeystoneAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = isSelected,
        )

    private fun ledger(isSelected: Boolean = true) =
        LedgerAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = isSelected,
            deviceIdentity = "tpk0-deadbeef",
            zip32AccountIndex = Zip32AccountIndex.new(0L),
        )

    private fun StringResource.resourceId(): Int = (this as StringResource.ByResource).resource

    private fun StyledStringResource.resourceId(): Int =
        (this as StyledStringResource.ByStringResource).resource.resourceId()

    /**
     * A plain [SwapQuote] test double rather than a mock: MockK's automatic answer generation for
     * an unstubbed or freshly-recorded property backed by a `@JvmInline value class` (here
     * [SwapAddress]'s implementations) never terminates, so every quote field is a real value.
     */
    private data class FakeSwapQuote(
        override val destinationAsset: SwapAsset,
        override val mode: SwapMode,
        override val originAsset: SwapAsset = SwapAssetTestFixture.asset(tokenTicker = "btc", chainTicker = "btc"),
        override val depositAddress: SwapAddress = DynamicSwapAddress("deposit-address"),
        override val destinationAddress: SwapAddress = DynamicSwapAddress("dest-address"),
        override val refundAddress: SwapAddress = DynamicSwapAddress("refund-address"),
        override val provider: String = "near",
        override val zecExchangeRate: BigDecimal = BigDecimal("30"),
        override val amountIn: BigDecimal = BigDecimal("1.5"),
        override val amountInFormatted: BigDecimal = BigDecimal("1.5"),
        override val amountInUsd: BigDecimal = BigDecimal("100"),
        override val amountOut: BigDecimal = BigDecimal("99"),
        override val amountOutUsd: BigDecimal = BigDecimal("99"),
        override val amountOutFormatted: BigDecimal = BigDecimal("99"),
        override val affiliateFee: BigDecimal = BigDecimal("0.01"),
        override val affiliateFeeZatoshi: Zatoshi = Zatoshi(1000),
        override val affiliateFeeUsd: BigDecimal = BigDecimal("1"),
        override val timestamp: Instant = Clock.System.now(),
        override val deadline: Instant = Clock.System.now(),
        override val slippage: BigDecimal = BigDecimal("1"),
    ) : SwapQuote {
        override fun getTotal(proposal: Proposal?): BigDecimal = amountIn

        override fun getTotalUsd(proposal: Proposal?): BigDecimal = amountInUsd

        override fun getTotalFeesUsd(proposal: Proposal?): BigDecimal = affiliateFeeUsd

        override fun getTotalFeesZatoshi(proposal: Proposal?): Zatoshi = affiliateFeeZatoshi
    }
}
