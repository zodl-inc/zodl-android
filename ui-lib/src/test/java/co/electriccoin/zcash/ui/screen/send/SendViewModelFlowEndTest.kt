package co.electriccoin.zcash.ui.screen.send

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.provider.SignerCapabilitiesProvider
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Leaving the send form ends the signer-capabilities flow so the next send measures afresh. */
@OptIn(ExperimentalCoroutinesApi::class)
class SendViewModelFlowEndTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun clearingTheViewModelEndsTheFlowExactlyOnce() =
        runTest {
            val signerCapabilities = mockk<SignerCapabilitiesProvider>(relaxed = true)
            val store = ViewModelStore()
            val vm =
                SendViewModel(
                    exchangeRateRepository = mockk(relaxed = true),
                    observeContactByAddress = mockk(relaxed = true),
                    observeContactPicked = mockk(relaxed = true),
                    createProposal = mockk(relaxed = true),
                    observeWalletAccounts = mockk(relaxed = true),
                    getSelectedWalletAccount = mockk(relaxed = true),
                    getMaxSendAmount = mockk(relaxed = true),
                    navigateToSelectRecipient = mockk(relaxed = true),
                    navigationRouter = mockk<NavigationRouter>(relaxed = true),
                    signerCapabilitiesProvider = signerCapabilities,
                )
            ViewModelProvider.create(store, viewModelFactory { initializer { vm } })[SendViewModel::class]

            verify(exactly = 0) { signerCapabilities.endFlow() }
            store.clear()

            verify(exactly = 1) { signerCapabilities.endFlow() }
        }
}
