package co.electriccoin.zcash.ui.screen.send

import co.electriccoin.zcash.ui.screen.send.model.RecipientAddressState
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import cash.z.ecc.android.sdk.type.AddressType as SdkAddressType
import cash.z.ecc.sdk.model.AddressType as RouteAddressType

@OptIn(ExperimentalCoroutinesApi::class)
class SendViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun recipientStartsOutAsTheRouteArguments() {
        assertEquals(
            RecipientAddressState.new("shared address", SdkAddressType.Transparent),
            vm(Send("shared address", RouteAddressType.TRANSPARENT)).recipientAddressState.value
        )
        assertEquals(RecipientAddressState.new("", null), vm(Send()).recipientAddressState.value)
    }

    private fun vm(args: Send) =
        SendViewModel(
            args = args,
            exchangeRateRepository = mockk(relaxed = true),
            observeContactByAddress = mockk(relaxed = true),
            observeContactPicked = mockk(relaxed = true),
            createProposal = mockk(relaxed = true),
            observeWalletAccounts = mockk(relaxed = true),
            navigateToSelectRecipient = mockk(relaxed = true),
            navigationRouter = mockk(relaxed = true),
        )
}
