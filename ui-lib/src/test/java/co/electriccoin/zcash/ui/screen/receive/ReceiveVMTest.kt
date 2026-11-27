package co.electriccoin.zcash.ui.screen.receive

import androidx.compose.ui.graphics.Color
import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.metadata.MetadataV3
import co.electriccoin.zcash.ui.common.usecase.AccountIconFixtures
import co.electriccoin.zcash.ui.common.usecase.AccountIconFixtures.PICKED
import co.electriccoin.zcash.ui.common.usecase.EditAccountFixtures
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.screen.receive.ReceiveAddressState.ColorMode
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
import kotlin.test.assertNotNull

/**
 * The receive screen's address rows: a view-only account shows its coloured icon, the view-only
 * titles and the hardware-wallet style on its shielded address, while the Zodl account keeps the
 * ZEC icon even once it has a colour.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveVMTest {
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
    fun aViewOnlyAccountReceivesWithItsColoredIconAndViewOnlyTitles() =
        runTest {
            allMetadata.value = mapOf(EditAccountFixtures.viewOnlyUuid to AccountIconFixtures.metadata(Color(PICKED)))

            val items = items(EditAccountFixtures.viewOnly())

            assertEquals(List(2) { AccountIconFixtures.viewOnlyIcon(Color(PICKED)) }, items.map { it.icon })
            assertEquals(listOf(VIEW_ONLY_SHIELDED, VIEW_ONLY_TRANSPARENT), items.map { it.title })
        }

    @Test
    fun theViewOnlyShieldedAddressUsesTheHardwareWalletStyleAndInfoIcon() =
        runTest {
            val (shielded, transparent) = items(EditAccountFixtures.viewOnly())

            assertEquals(ColorMode.HW_WALLET, shielded.colorMode)
            assertEquals(R.drawable.ic_receive_ks_shielded_info, shielded.infoIconButton.icon)
            assertEquals(ColorMode.DEFAULT, transparent.colorMode)
            assertEquals(R.drawable.ic_receive_zcash_info, transparent.infoIconButton.icon)
        }

    @Test
    fun theZodlAccountKeepsTheZecIconEvenWithAColor() =
        runTest {
            allMetadata.value = mapOf(EditAccountFixtures.zodlUuid to AccountIconFixtures.metadata(Color(PICKED)))

            val items = items(EditAccountFixtures.zodl())

            assertEquals(List(2) { imageRes(R.drawable.ic_zec_round_full) }, items.map { it.icon })
            assertEquals(ColorMode.ZASHI, items.first().colorMode)
        }

    private fun TestScope.items(account: WalletAccount): List<ReceiveAddressState> {
        val vm =
            ReceiveVM(
                observeSelectedWalletAccount =
                    mockk<ObserveSelectedWalletAccountUseCase> {
                        every { require() } returns flowOf(account)
                    },
                copyToClipboard = mockk(relaxed = true),
                navigationRouter = mockk(relaxed = true),
                navigateToRequestZec = mockk(relaxed = true),
                viewOnlyContent =
                    mockk<ViewOnlyContent> {
                        every { receiveShieldedTitle } returns VIEW_ONLY_SHIELDED
                        every { receiveTransparentTitle } returns VIEW_ONLY_TRANSPARENT
                    },
                observeAccountIcon = AccountIconFixtures.useCase(allMetadata),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        return assertNotNull(vm.state.value.items)
    }
}

/**
 * Resource-backed texts: mockk cannot return the inline `StringResource.ByString` from a mocked
 * interface property.
 */
private val VIEW_ONLY_SHIELDED = StringResource.ByResource(resource = 1, args = emptyList())

private val VIEW_ONLY_TRANSPARENT = StringResource.ByResource(resource = 2, args = emptyList())
