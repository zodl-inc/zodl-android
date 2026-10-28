package co.electriccoin.zcash.ui.screen.request.viewmodel

import android.app.Application
import androidx.compose.ui.graphics.Color
import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.metadata.MetadataV3
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.usecase.AccountIconFixtures
import co.electriccoin.zcash.ui.common.usecase.AccountIconFixtures.PICKED
import co.electriccoin.zcash.ui.common.usecase.EditAccountFixtures
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.screen.receive.ReceiveAddressType
import co.electriccoin.zcash.ui.screen.request.model.RequestState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The memo stage of a ZEC request shows the account's icon: a view-only account's in its colour, a
 * hardware wallet's own drawable, and the ZEC icon for the Zodl account even once it has a colour.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestVMTest {
    private val allMetadata = MutableStateFlow<Map<AccountUuid, MetadataV3>>(emptyMap())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aViewOnlyAccountsMemoStageShowsItsColoredIcon() =
        runTest {
            allMetadata.value = mapOf(EditAccountFixtures.viewOnlyUuid to AccountIconFixtures.metadata(Color(PICKED)))

            assertEquals(AccountIconFixtures.viewOnlyIcon(Color(PICKED)), memoIcon(EditAccountFixtures.viewOnly()))
        }

    @Test
    fun aViewOnlyAccountWithoutAColorShowsTheDefaultCircle() =
        runTest {
            assertEquals(AccountIconFixtures.viewOnlyIcon(), memoIcon(EditAccountFixtures.viewOnly()))
        }

    @Test
    fun hardwareWalletsShowTheirOwnDrawable() =
        runTest {
            allMetadata.value = mapOf(EditAccountFixtures.hardwareUuid to AccountIconFixtures.metadata(Color(PICKED)))

            val hardwareWallets =
                listOf(EditAccountFixtures.keystone(isSelected = true), EditAccountFixtures.ledger(isSelected = true))

            hardwareWallets.forEach { account ->
                assertEquals(imageRes(account.icon), memoIcon(account))
            }
        }

    @Test
    fun theZodlAccountKeepsTheZecIconEvenWithAColor() =
        runTest {
            allMetadata.value = mapOf(EditAccountFixtures.zodlUuid to AccountIconFixtures.metadata(Color(PICKED)))

            assertEquals(imageRes(R.drawable.ic_zec_round_full), memoIcon(EditAccountFixtures.zodl()))
        }

    /** The icon of [account]'s memo stage, reached from the amount stage of a shielded request. */
    private fun TestScope.memoIcon(account: WalletAccount): ImageResource {
        val vm =
            RequestVM(
                addressTypeOrdinal = ReceiveAddressType.Unified.ordinal,
                application = mockk<Application> { every { getString(any()) } returns "0" },
                exchangeRateRepository =
                    mockk<ExchangeRateRepository> {
                        every { state } returns MutableStateFlow(ExchangeRateState.OptedOut)
                    },
                getZcashCurrency = mockk(relaxed = true),
                zip321BuildUriUseCase = mockk(relaxed = true),
                observeSelectedWalletAccount =
                    mockk<ObserveSelectedWalletAccountUseCase> {
                        every { require() } returns flowOf(account)
                    },
                navigationRouter = mockk(relaxed = true),
                shareQR = mockk(relaxed = true),
                observeAccountIcon = AccountIconFixtures.useCase(allMetadata),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }

        assertIs<RequestState.Amount>(vm.state.value).onDone()

        return assertIs<RequestState.Memo>(vm.state.value).icon
    }
}
