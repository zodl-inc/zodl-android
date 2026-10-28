package co.electriccoin.zcash.ui.screen.signkeystonetransaction

import co.electriccoin.zcash.ui.common.datasource.ShieldTransactionProposal
import co.electriccoin.zcash.ui.common.usecase.EditAccountFixtures
import co.electriccoin.zcash.ui.common.usecase.ObserveProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.design.R
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The signing account's row shows the Keystone account's own icon, not the generic info icon. */
@OptIn(ExperimentalCoroutinesApi::class)
class SignKeystoneTransactionVMTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aKeystoneAccountSignsWithItsOwnIcon() =
        runTest {
            val vm =
                SignKeystoneTransactionVM(
                    observeSelectedWalletAccount =
                        mockk<ObserveSelectedWalletAccountUseCase> {
                            every { require() } returns flowOf(EditAccountFixtures.keystone(isSelected = true))
                        },
                    observeProposalUseCase =
                        mockk<ObserveProposalUseCase> {
                            every { this@mockk() } returns flowOf(ShieldTransactionProposal(proposal = mockk()))
                        },
                    navigationRouter = mockk(),
                    createKeystoneProposalPCZTEncoder = mockk(relaxed = true),
                    cancelKeystoneProposalFlow = mockk(),
                    sharePCZT = mockk(),
                )

            assertEquals(
                R.drawable.ic_item_keystone,
                vm.state
                    .filterNotNull()
                    .first()
                    .accountInfo.icon
            )
        }
}
