package co.electriccoin.zcash.ui.screen.accountlist

import androidx.compose.ui.graphics.Color
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.WalletBalance
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.ViewOnlyAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.model.metadata.MetadataV3
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.usecase.AccountIconFixtures
import co.electriccoin.zcash.ui.common.usecase.AccountIconFixtures.PICKED
import co.electriccoin.zcash.ui.common.usecase.EditAccountFixtures
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.common.usecase.SelectWalletAccountUseCase
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyNavigator
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.asPrivacySensitive
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.ExternalUrl
import co.electriccoin.zcash.ui.screen.accountlist.options.WalletOptionsArgs
import co.electriccoin.zcash.ui.screen.editaccount.EditAccountArgs
import io.mockk.coVerify
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import co.electriccoin.zcash.ui.R as UiR

/**
 * The full-screen "Wallets & Hardware" selector: the wallets grouped into Zodl, Keystone, Ledger and
 * view-only sections in that order whatever order they arrive in, each row with its avatar, its
 * balance (privacy-sensitive, a placeholder while unknown) and its trailing action; the Keystone
 * promo while no Keystone is connected; and the "Add Account" call to action once the wallets
 * are loaded. Every row carries its own account's coloured icon, and a colour change redraws only
 * that row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountListVMTest {
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
    fun sectionsFollowTheFixedOrderWhateverOrderTheAccountsArriveIn() =
        runTest {
            val vm =
                vm(
                    listOf(
                        viewOnly(id = 4, isSelected = false),
                        ledger(id = 3, isSelected = false),
                        zodl(id = 1, isSelected = true),
                        keystone(id = 2, isSelected = false),
                    )
                )
            collect(vm)

            val sections = assertNotNull(vm.state.value.sections)
            assertEquals(
                listOf(
                    stringRes(UiR.string.hwWallet_section_zodl),
                    stringRes(UiR.string.hwWallet_section_keystone),
                    stringRes(UiR.string.hwWallet_section_ledger),
                    VIEW_ONLY_SECTION,
                ),
                sections.map { it.title }
            )
            assertEquals(
                listOf(R.drawable.ic_item_zashi, R.drawable.ic_item_keystone, R.drawable.ic_item_ledger)
                    .map { imageRes(it) } + AccountIconFixtures.viewOnlyIcon(),
                sections.flatMap { section -> section.items.map { it.icon } }
            )
            assertTrue(sections.all { it.count == null })
        }

    @Test
    fun emptySectionsAreLeftOut() =
        runTest {
            val vm = vm(listOf(zodl(id = 1, isSelected = true), viewOnly(id = 2, isSelected = false)))
            collect(vm)

            assertEquals(
                listOf(stringRes(UiR.string.hwWallet_section_zodl), VIEW_ONLY_SECTION),
                assertNotNull(vm.state.value.sections).map { it.title }
            )
        }

    @Test
    fun severalViewOnlyAccountsTurnTheHeaderPluralAndShowTheirCount() =
        runTest {
            val vm =
                vm(
                    listOf(
                        zodl(id = 1, isSelected = true),
                        viewOnly(id = 2, isSelected = false),
                        viewOnly(id = 3, isSelected = false),
                        viewOnly(id = 4, isSelected = false),
                    )
                )
            collect(vm)

            val section = assertNotNull(vm.state.value.sections).last()
            assertEquals(VIEW_ONLY_SECTION_PLURAL, section.title)
            assertEquals(3, section.items.size)
            assertNotNull(section.count)
        }

    @Test
    fun onlyTheSelectedRowIsDrawnSelected() =
        runTest {
            val vm =
                vm(
                    listOf(
                        zodl(id = 1, isSelected = false),
                        keystone(id = 2, isSelected = false),
                        ledger(id = 3, isSelected = true),
                    )
                )
            collect(vm)

            val items = assertNotNull(vm.state.value.sections).flatMap { it.items }
            assertEquals(listOf(false, false, true), items.map { it.isSelected })
        }

    @Test
    fun everyRowIsTitledWithItsAccountName() =
        runTest {
            val accounts =
                listOf(
                    zodl(id = 1, isSelected = true),
                    keystone(id = 2, isSelected = false),
                    ledger(id = 3, isSelected = false),
                    viewOnly(id = 4, isSelected = false),
                )
            val vm = vm(accounts)
            collect(vm)

            assertEquals(
                accounts.map { it.name },
                assertNotNull(vm.state.value.sections).flatMap { section -> section.items.map { it.title } }
            )
        }

    @Test
    fun theZodlRowEndsInAChevronAndEveryOtherRowInTheOptionsAction() =
        runTest {
            val vm =
                vm(
                    listOf(
                        zodl(id = 1, isSelected = false),
                        keystone(id = 2, isSelected = true),
                        ledger(id = 3, isSelected = false),
                        viewOnly(id = 4, isSelected = false),
                    )
                )
            collect(vm)

            val items = assertNotNull(vm.state.value.sections).flatMap { it.items }
            assertEquals(
                listOf(
                    R.drawable.ic_wallet_list_chevron,
                    R.drawable.ic_wallet_list_more,
                    R.drawable.ic_wallet_list_more,
                    R.drawable.ic_wallet_list_more,
                ),
                items.map { assertNotNull(it.trailingAction).icon }
            )
        }

    @Test
    fun aRowShowsTheAccountsBalanceAsPrivacySensitiveText() =
        runTest {
            val vm =
                vm(
                    listOf(
                        zodl(id = 1, isSelected = true, balance = Zatoshi(100)),
                        keystone(id = 2, isSelected = false, balance = Zatoshi(10)),
                    )
                )
            collect(vm)

            val items = assertNotNull(vm.state.value.sections).flatMap { it.items }
            assertEquals(stringRes(Zatoshi(300)).asPrivacySensitive(), items[0].subtitle)
            assertEquals(stringRes(Zatoshi(30)).asPrivacySensitive(), items[1].subtitle)
        }

    @Test
    fun aViewOnlyRowAppendsTheViewOnlyLabelToItsBalance() =
        runTest {
            val vm = vm(listOf(zodl(id = 1, isSelected = true), viewOnly(id = 2, isSelected = false, Zatoshi(5))))
            collect(vm)

            val row = assertNotNull(vm.state.value.sections).last().items.single()
            assertEquals(stringRes(Zatoshi(15)).asPrivacySensitive() + " · " + VIEW_ONLY_LABEL, row.subtitle)
        }

    @Test
    fun aBalanceThatHasNotLoadedIsAPlaceholderNeverZero() =
        runTest {
            val vm =
                vm(
                    listOf(
                        zodl(id = 1, isSelected = true, balance = null),
                        viewOnly(id = 2, isSelected = false, balance = null),
                    )
                )
            collect(vm)

            val items = assertNotNull(vm.state.value.sections).flatMap { it.items }
            assertTrue(items.all { it.subtitle == null })
        }

    @Test
    fun tappingARowSelectsThatAccountAndCloses() =
        runTest {
            val selectWalletAccount = mockk<SelectWalletAccountUseCase>(relaxed = true)
            val ledger = ledger(id = 2, isSelected = false)
            val vm = vm(listOf(zodl(id = 1, isSelected = true), ledger), selectWalletAccount = selectWalletAccount)
            collect(vm)

            assertNotNull(vm.state.value.sections)[1].items.single().onClick()
            runCurrent()

            coVerify(exactly = 1) { selectWalletAccount.invoke(ledger, true) }
        }

    @Test
    fun anUnchangedAccountKeepsAnEqualRowAcrossEmissions() =
        runTest {
            val zodl = zodl(id = 1, isSelected = true)
            val keystone = keystone(id = 2, isSelected = false)
            val viewOnly = viewOnly(id = 3, isSelected = false)
            val accounts = MutableStateFlow<List<WalletAccount>?>(listOf(zodl, keystone, viewOnly))
            val vm = vm(accounts)
            collect(vm)
            val before = assertNotNull(vm.state.value.sections).flatMap { it.items }

            accounts.value = listOf(zodl.copy(), keystone.copy(transparentBalance = Zatoshi(2)), viewOnly.copy())
            runCurrent()

            val after = assertNotNull(vm.state.value.sections).flatMap { it.items }
            assertEquals(before[0], after[0])
            assertNotEquals(before[1], after[1])
            assertEquals(before[1].onClick, after[1].onClick)
            assertEquals(before[1].trailingAction?.onClick, after[1].trailingAction?.onClick)
            assertEquals(before[2], after[2])
        }

    @Test
    fun theCallbacksOfARemovedAccountAreDropped() =
        runTest {
            val zodl = zodl(id = 1, isSelected = true)
            val keystone = keystone(id = 2, isSelected = false)
            val accounts = MutableStateFlow<List<WalletAccount>?>(listOf(zodl, keystone))
            val vm = vm(accounts)
            collect(vm)
            val before = assertNotNull(vm.state.value.sections).flatMap { it.items }[1]

            accounts.value = listOf(zodl)
            runCurrent()
            accounts.value = listOf(zodl, keystone)
            runCurrent()

            val after = assertNotNull(vm.state.value.sections).flatMap { it.items }[1]
            assertNotSame(before.onClick, after.onClick)
            assertNotSame(before.trailingAction?.onClick, after.trailingAction?.onClick)
        }

    @Test
    fun tappingARowSelectsTheAccountAsItIsWhenTapped() =
        runTest {
            val selectWalletAccount = mockk<SelectWalletAccountUseCase>(relaxed = true)
            val ledger = ledger(id = 2, isSelected = false)
            val accounts = MutableStateFlow<List<WalletAccount>?>(listOf(zodl(id = 1, isSelected = true), ledger))
            val vm = vm(accounts, selectWalletAccount = selectWalletAccount)
            collect(vm)
            val onClick = assertNotNull(vm.state.value.sections)[1].items.single().onClick
            val updated = ledger.copy(transparentBalance = Zatoshi(7))

            accounts.value = listOf(zodl(id = 1, isSelected = true), updated)
            runCurrent()
            onClick()
            runCurrent()

            coVerify(exactly = 1) { selectWalletAccount.invoke(updated, true) }
        }

    @Test
    fun theChevronIsDecorativeAndTheOptionsButtonNamesItsWallet() =
        runTest {
            val vm = vm(listOf(zodl(id = 1, isSelected = true), viewOnly(id = 2, isSelected = false)))
            collect(vm)

            val sections = assertNotNull(vm.state.value.sections)
            assertNull(assertNotNull(sections[0].items.single().trailingAction).contentDescription)
            assertEquals(
                stringRes(UiR.string.hwWallet_walletOptions_contentDescription, stringRes("Savings")),
                assertNotNull(sections[1].items.single().trailingAction).contentDescription
            )
        }

    @Test
    fun theZodlChevronSelectsLikeTheRowAndOpensNothing() =
        runTest {
            val selectWalletAccount = mockk<SelectWalletAccountUseCase>(relaxed = true)
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val zodl = zodl(id = 1, isSelected = false)
            val vm =
                vm(
                    listOf(zodl, keystone(id = 2, isSelected = true)),
                    selectWalletAccount = selectWalletAccount,
                    navigationRouter = navigationRouter,
                )
            collect(vm)

            val row = assertNotNull(vm.state.value.sections)[0].items.single()
            val chevron = assertNotNull(row.trailingAction)
            assertEquals(row.onClick, chevron.onClick)
            chevron.onClick()
            runCurrent()

            coVerify(exactly = 1) { selectWalletAccount.invoke(zodl, true) }
            verify(exactly = 0) { navigationRouter.forward(*anyVararg()) }
        }

    @Test
    fun tappingTheZodlRowStillSelectsItAndCloses() =
        runTest {
            val selectWalletAccount = mockk<SelectWalletAccountUseCase>(relaxed = true)
            val zodl = zodl(id = 1, isSelected = false)
            val vm = vm(listOf(zodl, keystone(id = 2, isSelected = true)), selectWalletAccount = selectWalletAccount)
            collect(vm)

            assertNotNull(vm.state.value.sections)[0].items.single().onClick()
            runCurrent()

            coVerify(exactly = 1) { selectWalletAccount.invoke(zodl, true) }
        }

    @Test
    fun theOptionsOfAHardwareWalletOpenItsWalletOptionsWithoutSelectingIt() =
        runTest {
            val selectWalletAccount = mockk<SelectWalletAccountUseCase>(relaxed = true)
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val keystone = keystone(id = 2, isSelected = false)
            val ledger = ledger(id = 3, isSelected = false)
            val vm =
                vm(
                    listOf(zodl(id = 1, isSelected = true), keystone, ledger),
                    selectWalletAccount = selectWalletAccount,
                    navigationRouter = navigationRouter,
                )
            collect(vm)

            val sections = assertNotNull(vm.state.value.sections)
            assertNotNull(sections[1].items.single().trailingAction).onClick()
            assertNotNull(sections[2].items.single().trailingAction).onClick()
            runCurrent()

            verify(exactly = 1) {
                navigationRouter.forward(WalletOptionsArgs(keystone.sdkAccount.accountUuid.toStorageKeyId()))
            }
            verify(exactly = 1) {
                navigationRouter.forward(WalletOptionsArgs(ledger.sdkAccount.accountUuid.toStorageKeyId()))
            }
            coVerify(exactly = 0) { selectWalletAccount.invoke(any(), any()) }
        }

    @Test
    fun theOptionsOfAViewOnlyAccountOpenEditAccountWithoutSelectingIt() =
        runTest {
            val selectWalletAccount = mockk<SelectWalletAccountUseCase>(relaxed = true)
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val viewOnly = viewOnly(id = 2, isSelected = false)
            val vm =
                vm(
                    listOf(zodl(id = 1, isSelected = true), viewOnly),
                    selectWalletAccount = selectWalletAccount,
                    navigationRouter = navigationRouter,
                )
            collect(vm)

            assertNotNull(assertNotNull(vm.state.value.sections)[1].items.single().trailingAction).onClick()
            runCurrent()

            verify(exactly = 1) {
                navigationRouter.forward(EditAccountArgs(viewOnly.sdkAccount.accountUuid.toStorageKeyId()))
            }
            coVerify(exactly = 0) { selectWalletAccount.invoke(any(), any()) }
        }

    @Test
    fun thePromoShowsUntilAKeystoneIsConnected() =
        runTest {
            listOf(
                listOf(zodl(id = 1, isSelected = true)),
                listOf(zodl(id = 1, isSelected = true), ledger(id = 2, isSelected = false)),
                listOf(zodl(id = 1, isSelected = true), viewOnly(id = 2, isSelected = false)),
            ).forEach { accounts ->
                val vm = vm(accounts)
                collect(vm)
                assertNotNull(vm.state.value.keystonePromo)
            }

            listOf(
                listOf(zodl(id = 1, isSelected = true), keystone(id = 2, isSelected = false)),
                listOf(
                    zodl(id = 1, isSelected = true),
                    keystone(id = 2, isSelected = false),
                    ledger(id = 3, isSelected = false)
                ),
            ).forEach { accounts ->
                val vm = vm(accounts)
                collect(vm)
                assertNull(vm.state.value.keystonePromo)
            }
        }

    @Test
    fun thePromoOpensTheKeystoneShopInTheBrowserOnTopOfThisScreen() =
        runTest {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = vm(listOf(zodl(id = 1, isSelected = true)), navigationRouter = navigationRouter)
            collect(vm)

            val promo = assertNotNull(vm.state.value.keystonePromo)
            assertEquals(stringRes(UiR.string.hwWallet_promo_title), promo.title)
            promo.onClick()

            verify(exactly = 1) {
                navigationRouter.forward(ExternalUrl("https://keyst.one/shop/products/keystone-3-pro?discount=Zodl"))
            }
            verify(exactly = 0) { navigationRouter.replace(*anyVararg()) }
        }

    @Test
    fun theCallToActionShowsOnceTheAccountsAreLoadedWhateverIsConnected() =
        runTest {
            listOf(
                listOf(zodl(id = 1, isSelected = true)),
                listOf(zodl(id = 1, isSelected = true), keystone(id = 2, isSelected = false)),
                listOf(
                    zodl(id = 1, isSelected = true),
                    keystone(id = 2, isSelected = false),
                    ledger(id = 3, isSelected = false)
                ),
                listOf(zodl(id = 1, isSelected = false), viewOnly(id = 2, isSelected = true)),
            ).forEach { accounts ->
                val vm = vm(accounts)
                collect(vm)

                assertNotNull(vm.state.value.addAccountButton)
            }
        }

    @Test
    fun whileLoadingTheScreenShowsPlaceholdersOnly() =
        runTest {
            val vm = vm(accounts = null)
            collect(vm)

            val state = vm.state.value
            assertNull(state.sections)
            assertNull(state.keystonePromo)
            assertNull(state.addAccountButton)
            assertEquals(stringRes(UiR.string.hwWallet_drawer_title), state.title)
        }

    @Test
    fun theCallToActionCarriesTheViewOnlyCopyAndOpensTheAddAccountOptions() =
        runTest {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val viewOnlyNavigator = mockk<ViewOnlyNavigator>(relaxed = true)
            val vm =
                vm(
                    listOf(zodl(id = 1, isSelected = true)),
                    navigationRouter = navigationRouter,
                    viewOnlyNavigator = viewOnlyNavigator,
                )
            collect(vm)

            val button = assertNotNull(vm.state.value.addAccountButton)
            assertEquals(ButtonStyle.PRIMARY, button.style)
            assertEquals(ADD_ACCOUNT, button.text)
            assertEquals(ADD_ACCOUNT_ICON, button.icon)
            button.onClick()

            verify(exactly = 1) { viewOnlyNavigator.forwardToAddAccount() }
            verify(exactly = 0) { navigationRouter.forward(any()) }
        }

    @Test
    fun backClosesTheScreen() =
        runTest {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = vm(listOf(zodl(id = 1, isSelected = true)), navigationRouter = navigationRouter)
            collect(vm)

            vm.state.value.onBack()

            verify(exactly = 1) { navigationRouter.back() }
        }

    @Test
    fun everyRowCarriesItsAccountsColoredIcon() =
        runTest {
            val zodl = zodl(id = 1, isSelected = true)
            val keystone = keystone(id = 2, isSelected = false)
            val colored = viewOnly(id = 3, isSelected = false)
            val plain = viewOnly(id = 4, isSelected = false)
            allMetadata.value =
                mapOf(
                    zodl.sdkAccount.accountUuid to AccountIconFixtures.metadata(Color(ZODL_COLOR)),
                    keystone.sdkAccount.accountUuid to AccountIconFixtures.metadata(Color(PICKED)),
                    colored.sdkAccount.accountUuid to AccountIconFixtures.metadata(Color(PICKED)),
                )
            val vm = vm(listOf(zodl, keystone, colored, plain))
            collect(vm)

            assertEquals(
                listOf(
                    ImageResource.Layered(
                        background = Color(ZODL_COLOR),
                        foreground = ImageResource.ByDrawable(R.drawable.ic_item_zodl_glyph)
                    ),
                    imageRes(R.drawable.ic_item_keystone),
                    AccountIconFixtures.viewOnlyIcon(Color(PICKED)),
                    AccountIconFixtures.viewOnlyIcon(),
                ),
                assertNotNull(vm.state.value.sections).flatMap { section -> section.items.map { it.icon } }
            )
        }

    @Test
    fun aColorChangeRedrawsOnlyThatRow() =
        runTest {
            val zodl = zodl(id = 1, isSelected = true)
            val first = viewOnly(id = 2, isSelected = false)
            val second = viewOnly(id = 3, isSelected = false)
            allMetadata.value = mapOf(first.sdkAccount.accountUuid to AccountIconFixtures.metadata(Color(PICKED)))
            val vm = vm(listOf(zodl, first, second))
            collect(vm)
            val before = assertNotNull(vm.state.value.sections).flatMap { it.items }

            allMetadata.value =
                mapOf(
                    first.sdkAccount.accountUuid to AccountIconFixtures.metadata(Color(PICKED)),
                    second.sdkAccount.accountUuid to AccountIconFixtures.metadata(Color(ZODL_COLOR)),
                )
            runCurrent()

            val after = assertNotNull(vm.state.value.sections).flatMap { it.items }
            assertEquals(before[0], after[0])
            assertEquals(before[1], after[1])
            assertNotEquals(before[2], after[2])
            assertEquals(AccountIconFixtures.viewOnlyIcon(Color(ZODL_COLOR)), after[2].icon)
        }

    private fun vm(
        accounts: List<WalletAccount>?,
        selectWalletAccount: SelectWalletAccountUseCase = mockk(relaxed = true),
        navigationRouter: NavigationRouter = mockk(relaxed = true),
        viewOnlyNavigator: ViewOnlyNavigator = mockk(relaxed = true),
    ) = vm(MutableStateFlow(accounts), selectWalletAccount, navigationRouter, viewOnlyNavigator)

    private fun vm(
        accounts: MutableStateFlow<List<WalletAccount>?>,
        selectWalletAccount: SelectWalletAccountUseCase = mockk(relaxed = true),
        navigationRouter: NavigationRouter = mockk(relaxed = true),
        viewOnlyNavigator: ViewOnlyNavigator = mockk(relaxed = true),
    ) = AccountListVM(
        getWalletAccounts =
            mockk<GetWalletAccountsUseCase> {
                every { observe() } returns accounts
            },
        selectWalletAccount = selectWalletAccount,
        navigationRouter = navigationRouter,
        viewOnlyNavigator = viewOnlyNavigator,
        viewOnlyContent =
            mockk<ViewOnlyContent> {
                every { addAccountButton } returns ADD_ACCOUNT
                every { addAccountButtonIcon } returns ADD_ACCOUNT_ICON
                every { accountListSection } returns VIEW_ONLY_SECTION
                every { accountListSectionPlural } returns VIEW_ONLY_SECTION_PLURAL
                every { topBarSubtitle } returns VIEW_ONLY_LABEL
            },
        observeAccountIcon = AccountIconFixtures.useCase(allMetadata),
    )

    /**
     * A pool balance of [amount], all of it available. The accounts below put it in every shielded
     * pool, so a Zodl account (no transparent funds) totals 3x and a Keystone or view-only account
     * (as much again in the transparent pool) 3x [amount] as well.
     */
    private fun balance(amount: Zatoshi?) = amount?.let { WalletBalance(it, Zatoshi(0), Zatoshi(0)) }

    private fun zodl(
        id: Int,
        isSelected: Boolean,
        balance: Zatoshi? = Zatoshi(1),
    ) = ZodlAccount(
        sdkAccount = EditAccountFixtures.sdkAccount(id),
        unifiedAddress = "u-zodl",
        transparentAddress = "t-zodl",
        saplingAddress = "s-zodl",
        orchardBalance = balance(balance),
        saplingBalance = balance(balance),
        ironwoodBalance = balance(balance),
        transparentBalance = balance?.let { Zatoshi(0) },
        isSelected = isSelected,
    )

    private fun keystone(
        id: Int,
        isSelected: Boolean,
        balance: Zatoshi? = Zatoshi(1),
    ) = KeystoneAccount(
        sdkAccount = EditAccountFixtures.sdkAccount(id),
        unifiedAddress = "u-keystone",
        transparentAddress = "t-keystone",
        orchardBalance = balance(balance),
        ironwoodBalance = balance(balance),
        transparentBalance = balance,
        isSelected = isSelected,
    )

    private fun ledger(
        id: Int,
        isSelected: Boolean,
    ) = LedgerAccount(
        sdkAccount = EditAccountFixtures.sdkAccount(id),
        unifiedAddress = "u-ledger",
        transparentAddress = "t-ledger",
        orchardBalance = balance(Zatoshi(1)),
        ironwoodBalance = balance(Zatoshi(1)),
        transparentBalance = Zatoshi(1),
        isSelected = isSelected,
        deviceIdentity = null,
        zip32AccountIndex = Zip32AccountIndex.new(0L),
    )

    private fun viewOnly(
        id: Int,
        isSelected: Boolean,
        balance: Zatoshi? = Zatoshi(1),
    ) = ViewOnlyAccount(
        sdkAccount = EditAccountFixtures.sdkAccount(id),
        unifiedAddress = "u-view-only",
        transparentAddress = "t-view-only",
        orchardBalance = balance(balance),
        saplingBalance = balance(balance?.let { Zatoshi(0) }),
        ironwoodBalance = balance(balance),
        transparentBalance = balance,
        isSelected = isSelected,
        name = stringRes("Savings"),
        customName = "Savings",
        icon = AccountIconFixtures.VIEW_ONLY_ICON,
    )

    private fun TestScope.collect(vm: AccountListVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
    }
}

/**
 * Resource-backed texts: mockk cannot return the inline `StringResource.ByString` from a mocked
 * interface property.
 */
private val ADD_ACCOUNT = StringResource.ByResource(resource = 1, args = emptyList())

private val VIEW_ONLY_SECTION = StringResource.ByResource(resource = 2, args = emptyList())

private val VIEW_ONLY_SECTION_PLURAL = StringResource.ByResource(resource = 3, args = emptyList())

private val VIEW_ONLY_LABEL = StringResource.ByResource(resource = 4, args = emptyList())

private const val ADD_ACCOUNT_ICON = 42

private const val ZODL_COLOR = 0xFF0088FF
