package co.electriccoin.zcash.ui.screen.swap.info

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SwapAssetTestFixture
import co.electriccoin.zcash.ui.common.provider.BlockchainProvider
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [SwapRefundAddressInfoVM] builds its message from the ticker args (a snapshot taken at navigation
 * time), rebuilding the chain name from the [BlockchainProvider], and shows the empty message when
 * no asset is selected.
 */
class SwapRefundAddressInfoVMTest {
    private val blockchainProvider =
        mockk<BlockchainProvider> {
            every { getBlockchain(any()) } answers { SwapAssetTestFixture.blockchain(firstArg()) }
        }

    @Test
    fun buildsMessageFromTickerArgs() {
        val vm =
            SwapRefundAddressInfoVM(
                args = SwapRefundAddressInfoArgs(tokenTicker = "btc", chainTicker = "btc"),
                blockchainProvider = blockchainProvider,
                navigationRouter = mockk<NavigationRouter>(relaxed = true)
            )

        assertEquals(
            stringRes(
                R.string.swap_refund_address_info_message,
                "btc",
                SwapAssetTestFixture.blockchain("btc").chainName
            ),
            vm.state.value?.message
        )
    }

    @Test
    fun showsEmptyMessageWhenNoAssetSelected() {
        val vm =
            SwapRefundAddressInfoVM(
                args = SwapRefundAddressInfoArgs(tokenTicker = null, chainTicker = null),
                blockchainProvider = blockchainProvider,
                navigationRouter = mockk<NavigationRouter>(relaxed = true)
            )

        assertEquals(stringRes(R.string.swap_refund_address_info_message_empty), vm.state.value?.message)
    }
}
