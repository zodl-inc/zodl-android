package co.electriccoin.zcash.ui.screen.migration.setup

import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.ui.common.model.LceError
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.repository.ExchangeRateRepository
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetOrchardBalanceUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.common.wallet.ExchangeRateState
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
 * A view-only account cannot migrate funds it cannot spend: the setup screen shows the view-only
 * unsupported-operation sheet instead of loading the Orchard balance it would offer to move.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MigrationSetupVMViewOnlyTest {
    private val viewOnly =
        ViewOnlyAccount(
            sdkAccount = Account.new(AccountUuid.new(ByteArray(16) { it.toByte() })),
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = true,
            name = stringRes("Savings"),
            customName = "Savings",
            icon = 0,
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aViewOnlyAccountGetsTheUnsupportedSheetInsteadOfItsBalance() =
        runTest {
            val title = stringRes(co.electriccoin.zcash.ui.R.string.error_general_title)
            val getOrchardBalance = mockk<GetOrchardBalanceUseCase>(relaxed = true)
            val vm =
                MigrationSetupVM(
                    getSelectedWalletAccount =
                        mockk<GetSelectedWalletAccountUseCase> {
                            coEvery { this@mockk() } returns viewOnly
                            every { observe() } returns MutableStateFlow(viewOnly)
                        },
                    getOrchardBalance = getOrchardBalance,
                    exchangeRateRepository =
                        mockk<ExchangeRateRepository>(relaxed = true) {
                            every { state } returns MutableStateFlow(ExchangeRateState.OptIn)
                        },
                    navigationRouter = mockk(relaxed = true),
                    errorStateMapper =
                        ErrorMapperUseCase(
                            sendEmail = mockk(relaxed = true),
                            viewOnlyContent =
                                mockk<ViewOnlyContent>(relaxed = true) {
                                    every { unsupportedOperationTitle } returns title
                                },
                        ),
                    getMigrationPrivacyOrReviewDestination = mockk(relaxed = true),
                    getMigrationGateInfo = mockk(relaxed = true),
                    migrationUpgradeNotice = mockk(relaxed = true),
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }

            val error = assertIs<LceError.BottomSheet>(vm.state.value.error)
            assertEquals(title, error.state.title)
            coVerify(exactly = 0) { getOrchardBalance() }
        }
}
