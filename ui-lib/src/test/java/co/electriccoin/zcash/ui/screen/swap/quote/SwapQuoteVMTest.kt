package co.electriccoin.zcash.ui.screen.swap.quote

import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.model.SwapMode
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
import co.electriccoin.zcash.ui.common.repository.SwapQuoteData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.usecase.CancelSwapQuoteUseCase
import co.electriccoin.zcash.ui.common.usecase.CancelSwapUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitProposalUseCase
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.StyledStringResource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The ViewModel-level counterpart of [SwapQuoteVMMapperTest]: the "Swap from" / "Pay from" row of a
 * live quote must name whichever account is actually selected, driven through the real
 * [SwapQuoteVMMapper] rather than a mock of it.
 *
 * Runs under Robolectric for the same reason as [SwapQuoteVMMapperTest]: the mapper resolves
 * `FiatCurrency.USD.symbol`, which needs real ICU currency data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SwapQuoteVMTest {
    private val defaultUuid = AccountUuid.new(ByteArray(16) { it.toByte() })
    private val sdkAccount = Account.new(defaultUuid)
    private val destinationAsset = SwapAssetTestFixture.asset(tokenTicker = "usdc", chainTicker = "eth")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theFromRowNamesTheSelectedLedgerAccount() =
        runTest {
            val vm = vm(account = ledger())
            collect(vm)

            assertEquals(R.string.accounts_ledger, fromRowTitle(vm))
        }

    @Test
    fun theFromRowNamesTheSelectedKeystoneAccount() =
        runTest {
            val vm = vm(account = keystone())
            collect(vm)

            assertEquals(R.string.accounts_keystone, fromRowTitle(vm))
        }

    @Test
    fun theFromRowNamesTheSelectedZashiAccount() =
        runTest {
            val vm = vm(account = zashi())
            collect(vm)

            assertEquals(R.string.accounts_zashi, fromRowTitle(vm))
        }

    private fun fromRowTitle(vm: SwapQuoteVM): Int {
        val state = assertNotNull(vm.state.value) as SwapQuoteState.Success
        return state.items
            .first()
            .title
            .resourceId()
    }

    private fun vm(account: WalletAccount) =
        SwapQuoteVM(
            observeProposal =
                mockk {
                    every { observeNullable() } returns flowOf(null)
                },
            observeSelectedWalletAccount =
                mockk {
                    every { require() } returns flowOf(account)
                },
            applicationStateProvider =
                mockk<ApplicationStateProvider> {
                    every { observeOnForeground() } returns emptyFlow()
                },
            swapRepository =
                mockk<SwapRepository>(relaxed = true) {
                    every { quote } returns
                        MutableStateFlow(
                            SwapQuoteData.Success(
                                quote = FakeSwapQuote(destinationAsset = destinationAsset, mode = SwapMode.EXACT_INPUT)
                            )
                        )
                },
            cancelSwapQuote = mockk<CancelSwapQuoteUseCase>(relaxed = true),
            cancelSwap = mockk<CancelSwapUseCase>(relaxed = true),
            swapQuoteSuccessMapper = SwapQuoteVMMapper(),
            submitProposal = mockk<SubmitProposalUseCase>(relaxed = true),
            navigationRouter = mockk<NavigationRouter>(relaxed = true),
        )

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

    private fun TestScope.collect(vm: SwapQuoteVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
    }
}
