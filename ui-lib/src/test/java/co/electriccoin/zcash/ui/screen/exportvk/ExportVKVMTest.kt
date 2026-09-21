package co.electriccoin.zcash.ui.screen.exportvk

import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavBackStackEntry
import cash.z.ecc.android.sdk.model.Account
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.VKType
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.usecase.GetVKUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.design.util.StyledStringStyle
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.styledStringResource
import co.electriccoin.zcash.ui.design.util.withStyle
import co.electriccoin.zcash.ui.screen.exportvk.confirm.ExportVKConfirmArgs
import co.electriccoin.zcash.ui.screen.exportvk.detail.VKDetailArgs
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Export Viewing Key chooser (MOB-1883): exactly one of Incoming/Full can be selected, Continue stays
 * disabled until a selection whose key the account actually has, Incoming goes straight to the export screen
 * and Full goes through the consent sheet first. The header logo and the bold wallet name in the description
 * follow the selected account, so a Keystone wallet gets Keystone branding (MOB-1892).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExportVKVMTest {
    private lateinit var dispatcher: TestDispatcher

    @BeforeTest
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun continueIsDisabledWithoutSelection() =
        runTest(dispatcher) {
            val vm = startedVm()

            val state = requireNotNull(vm.state.value)
            assertFalse(state.continueButton.isEnabled)
            assertTrue(state.options.none { it.isChecked })
        }

    @Test
    fun optionsAreOrderedIncomingThenFull() =
        runTest(dispatcher) {
            val vm = startedVm()

            assertEquals(
                listOf(VKType.INCOMING, VKType.FULL),
                requireNotNull(vm.state.value).options.map { it.type }
            )
        }

    @Test
    fun tappingAnOptionSelectsOnlyItAndEnablesContinue() =
        runTest(dispatcher) {
            val vm = startedVm()

            optionFor(vm, VKType.FULL).onClick()
            advanceUntilIdle()
            optionFor(vm, VKType.INCOMING).onClick()
            advanceUntilIdle()

            val state = requireNotNull(vm.state.value)
            assertEquals(
                listOf(true, false),
                state.options.map { it.isChecked },
            )
            assertTrue(state.continueButton.isEnabled)
        }

    @Test
    fun continueWithIncomingReplacesTheChooserWithTheExportScreen() =
        runTest(dispatcher) {
            val router = FakeNavigationRouter()
            val vm = startedVm(router = router)

            optionFor(vm, VKType.INCOMING).onClick()
            advanceUntilIdle()
            requireNotNull(vm.state.value).continueButton.onClick()

            assertEquals(VKDetailArgs(VKType.INCOMING), router.replacedRoutes.single())
            assertTrue(router.forwardedRoutes.isEmpty())
        }

    @Test
    fun continueWithFullForwardsToTheConsentSheet() =
        runTest(dispatcher) {
            val router = FakeNavigationRouter()
            val vm = startedVm(router = router)

            optionFor(vm, VKType.FULL).onClick()
            advanceUntilIdle()
            requireNotNull(vm.state.value).continueButton.onClick()

            assertEquals(ExportVKConfirmArgs, router.forwardedRoutes.single())
        }

    @Test
    fun continueStaysDisabledWhenTheSelectedKeyIsMissing() =
        runTest(dispatcher) {
            val vm =
                startedVm(
                    availability = mapOf(VKType.INCOMING to true, VKType.FULL to false)
                )

            optionFor(vm, VKType.FULL).onClick()
            advanceUntilIdle()

            val state = requireNotNull(vm.state.value)
            assertTrue(optionFor(vm, VKType.FULL).isChecked)
            assertFalse(state.continueButton.isEnabled)
        }

    @Test
    fun zodlWalletBrandsTheHeaderAndDescriptionAsZodl() =
        runTest(dispatcher) {
            val state = requireNotNull(startedVm(account = zashiAccount()).state.value)

            assertEquals(imageRes(R.drawable.ic_item_zashi), state.logo)
            assertEquals(descriptionFor(R.string.accounts_zashi), state.description)
        }

    @Test
    fun keystoneWalletBrandsTheHeaderAndDescriptionAsKeystone() =
        runTest(dispatcher) {
            val state = requireNotNull(startedVm(account = keystoneAccount()).state.value)

            assertEquals(imageRes(R.drawable.ic_item_keystone), state.logo)
            assertEquals(descriptionFor(R.string.accounts_keystone), state.description)
        }

    @Test
    fun backNavigatesBack() =
        runTest(dispatcher) {
            val router = FakeNavigationRouter()
            val vm = startedVm(router = router)

            requireNotNull(vm.state.value).onBack()

            assertEquals(1, router.backCount)
        }

    private fun optionFor(
        vm: ExportVKVM,
        type: VKType
    ) = requireNotNull(vm.state.value).options.first { it.type == type }

    private fun descriptionFor(walletName: Int) =
        styledStringResource(
            R.string.exportViewingKey_description,
            stringRes(walletName) withStyle StyledStringStyle(fontWeight = FontWeight.Bold)
        )

    private fun zashiAccount() =
        ZashiAccount(
            sdkAccount = mockk<Account>(relaxed = true),
            unifiedAddress = "unified",
            transparentAddress = "transparent",
            saplingAddress = "sapling",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = true,
        )

    private fun keystoneAccount() =
        KeystoneAccount(
            sdkAccount = mockk<Account>(relaxed = true),
            unifiedAddress = "unified",
            transparentAddress = "transparent",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = true,
        )

    private fun TestScope.startedVm(
        availability: Map<VKType, Boolean> =
            mapOf(VKType.INCOMING to true, VKType.FULL to true),
        account: WalletAccount = zashiAccount(),
        router: FakeNavigationRouter = FakeNavigationRouter(),
    ): ExportVKVM {
        val getVK =
            mockk<GetVKUseCase> {
                every { observeAvailability() } returns flowOf(availability)
            }
        val observeSelectedWalletAccount =
            mockk<ObserveSelectedWalletAccountUseCase> {
                every { require() } returns flowOf(account)
            }
        val vm =
            ExportVKVM(
                getVK = getVK,
                observeSelectedWalletAccount = observeSelectedWalletAccount,
                navigationRouter = router
            )
        backgroundScope.launch { vm.state.collect { } }
        advanceUntilIdle()
        return vm
    }
}

private class FakeNavigationRouter : NavigationRouter {
    var backCount = 0
        private set
    val forwardedRoutes = mutableListOf<Any>()
    val replacedRoutes = mutableListOf<Any>()

    override fun forward(vararg routes: Any) {
        forwardedRoutes.addAll(routes)
    }

    override fun replace(vararg routes: Any) {
        replacedRoutes.addAll(routes)
    }

    override fun replaceAll(vararg routes: Any) = Unit

    override fun replaceFrom(route: KClass<*>, vararg routes: Any) = Unit

    override fun back() {
        backCount++
    }

    override fun backTo(route: KClass<*>) = Unit

    override fun custom(block: (NavBackStackEntry?) -> NavigationCommand?) = Unit

    override fun backToRoot() = Unit

    override fun observePipeline(): Flow<BaseNavigationCommand> = emptyFlow()
}
