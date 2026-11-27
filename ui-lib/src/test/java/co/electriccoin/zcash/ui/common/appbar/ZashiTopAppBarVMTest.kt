package co.electriccoin.zcash.ui.common.appbar

import androidx.compose.ui.graphics.Color
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.DistributionDimension
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.model.metadata.MetadataV3
import co.electriccoin.zcash.ui.common.provider.GetVersionInfoProvider
import co.electriccoin.zcash.ui.common.repository.ConfigurationRepository
import co.electriccoin.zcash.ui.common.usecase.AccountIconFixtures
import co.electriccoin.zcash.ui.common.usecase.AccountIconFixtures.PICKED
import co.electriccoin.zcash.ui.common.usecase.EditAccountFixtures
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.fixture.VersionInfoFixture
import co.electriccoin.zcash.ui.screen.integrations.IntegrationsArgs
import co.electriccoin.zcash.ui.screen.more.MoreArgs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertNull

/**
 * The top app bar shows the selected account as the account itself describes it - its avatar and
 * name - and only a view-only account adds the small label from the view-only feature above the
 * name. A view-only account is not a hardware wallet, so it never changes where the more button
 * leads. An account's stored colour reaches the bar's avatar, live.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZashiTopAppBarVMTest {
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
    fun spendableAccountsShowTheirOwnAvatarAndNameWithoutALabel() {
        listOf(zodl(isSelected = true), keystone(isSelected = true), ledger(isSelected = true)).forEach { account ->
            val switch = assertNotNull(vm(listOf(account)).state.value.accountSwitchState)

            assertEquals(imageRes(account.icon), switch.icon)
            assertEquals(account.name, switch.name)
            assertNull(switch.subtitle)
        }
    }

    @Test
    fun theVendorAvatarsAreTheOnesTheBarAlwaysShowed() {
        assertEquals(
            listOf(R.drawable.ic_item_zashi, R.drawable.ic_item_keystone, R.drawable.ic_item_ledger).map(::imageRes),
            listOf(zodl(isSelected = true), keystone(isSelected = true), ledger(isSelected = true)).map {
                assertNotNull(vm(listOf(it)).state.value.accountSwitchState).icon
            }
        )
    }

    @Test
    fun aViewOnlyAccountShowsItsAvatarAndNameUnderTheViewOnlyLabel() {
        val viewOnly = viewOnly(isSelected = true)
        val switch = assertNotNull(vm(listOf(zodl(isSelected = false), viewOnly)).state.value.accountSwitchState)

        assertEquals(AccountIconFixtures.viewOnlyIcon(), switch.icon)
        assertEquals(stringRes("Savings"), switch.name)
        assertEquals(VIEW_ONLY_LABEL, switch.subtitle)
    }

    @Test
    fun theSwitchShimmersUntilAnAccountIsSelected() {
        assertNull(vm(accounts = null).state.value.accountSwitchState)
        assertNull(vm(listOf(zodl(isSelected = false))).state.value.accountSwitchState)
    }

    @Test
    fun aViewOnlyAccountDoesNotCountAsAConnectedHardwareWallet() =
        runTest {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm =
                vm(
                    listOf(zodl(isSelected = false), viewOnly(isSelected = true)),
                    navigationRouter = navigationRouter,
                )

            val moreButton = vm.state.value.moreButton
            moreButton.onClick()

            verify(exactly = 1) { navigationRouter.forward(IntegrationsArgs) }
            verify(exactly = 0) { navigationRouter.forward(MoreArgs) }
        }

    @Test
    fun aConnectedHardwareWalletStillSkipsTheEmptyIntegrations() =
        runTest {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm =
                vm(
                    listOf(zodl(isSelected = false), keystone(isSelected = true)),
                    navigationRouter = navigationRouter,
                )

            val moreButton = vm.state.value.moreButton
            moreButton.onClick()

            verify(exactly = 1) { navigationRouter.forward(MoreArgs) }
        }

    @Test
    fun aColoredViewOnlyAccountShowsItsColorOnTheBar() =
        runTest {
            val viewOnly = viewOnly(isSelected = true)
            allMetadata.value = mapOf(viewOnly.sdkAccount.accountUuid to AccountIconFixtures.metadata(Color(PICKED)))
            val vm = vm(listOf(zodl(isSelected = false), viewOnly))
            collect(vm)

            assertEquals(
                AccountIconFixtures.viewOnlyIcon(Color(PICKED)),
                assertNotNull(vm.state.value.accountSwitchState).icon
            )
        }

    @Test
    fun aColoredZodlAccountShowsItsMarkOnTheBar() =
        runTest {
            val zodl = zodl(isSelected = true)
            allMetadata.value = mapOf(zodl.sdkAccount.accountUuid to AccountIconFixtures.metadata(Color(PICKED)))
            val vm = vm(listOf(zodl))
            collect(vm)

            assertEquals(
                ImageResource.Layered(
                    background = Color(PICKED),
                    foreground = ImageResource.ByDrawable(R.drawable.ic_item_zodl_glyph)
                ),
                assertNotNull(vm.state.value.accountSwitchState).icon
            )
        }

    @Test
    fun aColorChangeReachesTheBarLive() =
        runTest {
            val viewOnly = viewOnly(isSelected = true)
            val vm = vm(listOf(zodl(isSelected = false), viewOnly))
            collect(vm)
            assertEquals(AccountIconFixtures.viewOnlyIcon(), assertNotNull(vm.state.value.accountSwitchState).icon)

            allMetadata.value = mapOf(viewOnly.sdkAccount.accountUuid to AccountIconFixtures.metadata(Color(PICKED)))
            assertEquals(
                AccountIconFixtures.viewOnlyIcon(Color(PICKED)),
                assertNotNull(vm.state.value.accountSwitchState).icon
            )

            allMetadata.value = mapOf(viewOnly.sdkAccount.accountUuid to AccountIconFixtures.metadata(Color(OTHER)))
            assertEquals(
                AccountIconFixtures.viewOnlyIcon(Color(OTHER)),
                assertNotNull(vm.state.value.accountSwitchState).icon
            )
        }

    private fun TestScope.collect(vm: ZashiTopAppBarVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
    }

    private fun vm(
        accounts: List<WalletAccount>?,
        navigationRouter: NavigationRouter = mockk(relaxed = true),
    ) = ZashiTopAppBarVM(
        getWalletAccountsUseCase =
            mockk<GetWalletAccountsUseCase> {
                every { observe() } returns MutableStateFlow(accounts)
            },
        standardPreferenceProvider = mockk(relaxed = true),
        navigationRouter = navigationRouter,
        getVersionInfo =
            mockk<GetVersionInfoProvider> {
                every { this@mockk() } returns
                    VersionInfoFixture.new(distributionDimension = DistributionDimension.FOSS)
            },
        configurationRepository =
            mockk<ConfigurationRepository> {
                coEvery { isFlexaAvailable() } returns false
            },
        viewOnlyContent =
            mockk<ViewOnlyContent> {
                every { topBarSubtitle } returns VIEW_ONLY_LABEL
            },
        observeAccountIcon = AccountIconFixtures.useCase(allMetadata),
    )

    private fun zodl(isSelected: Boolean) =
        ZodlAccount(
            sdkAccount = EditAccountFixtures.sdkAccount(0),
            unifiedAddress = "u-zodl",
            transparentAddress = "t-zodl",
            saplingAddress = "s-zodl",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = isSelected,
        )

    private fun keystone(isSelected: Boolean) =
        KeystoneAccount(
            sdkAccount = EditAccountFixtures.sdkAccount(1),
            unifiedAddress = "u-keystone",
            transparentAddress = "t-keystone",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = isSelected,
        )

    private fun ledger(isSelected: Boolean) =
        LedgerAccount(
            sdkAccount = EditAccountFixtures.sdkAccount(2),
            unifiedAddress = "u-ledger",
            transparentAddress = "t-ledger",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = isSelected,
            deviceIdentity = null,
            zip32AccountIndex = Zip32AccountIndex.new(0L),
        )

    private fun viewOnly(isSelected: Boolean) =
        ViewOnlyAccount(
            sdkAccount = EditAccountFixtures.sdkAccount(3),
            unifiedAddress = "u-view-only",
            transparentAddress = "t-view-only",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = isSelected,
            name = stringRes("Savings"),
            customName = "Savings",
            icon = AccountIconFixtures.VIEW_ONLY_ICON,
        )
}

/**
 * A resource-backed text: mockk cannot return the inline `StringResource.ByString` from a mocked
 * interface property.
 */
private val VIEW_ONLY_LABEL = StringResource.ByResource(resource = 7, args = emptyList())

private const val OTHER = 0xFF0088FF
