package co.electriccoin.zcash.ui.screen.disconnect

import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LceError
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.ZodlAccount
import co.electriccoin.zcash.ui.common.model.toStorageKeyId
import co.electriccoin.zcash.ui.common.usecase.DisconnectUseCase
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.screen.accountlist.AccountListArgs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import co.electriccoin.zcash.ui.design.R as UIR

/**
 * The disconnect screen serves whichever hardware vendor is selected, or the hardware wallet the
 * Wallets & Hardware screen names: it names and draws the one being removed, and the confirmed
 * disconnect deletes that account — which is also what drops a Ledger account's stored binding —
 * before selecting the software wallet again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DisconnectVMTest {
    private val defaultUuid = AccountUuid.new(ByteArray(16) { it.toByte() })
    private val sdkAccount = Account.new(defaultUuid)
    private val otherUuid = AccountUuid.new(ByteArray(16) { (it + 16).toByte() })

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theScreenNamesTheVendorItIsAboutToDisconnect() =
        runTest {
            val keystone = vm(MutableStateFlow(keystone(isSelected = true)))
            collect(keystone)
            assertShowsKeystone(assertNotNull(keystone.state.value.content))

            val ledger = vm(MutableStateFlow(ledger(isSelected = true)))
            collect(ledger)
            assertShowsLedger(assertNotNull(ledger.state.value.content))
        }

    @Test
    fun theConnectedCardShowsTheAccountsOwnNameAndTheScreenTitleTheVendor() =
        runTest {
            listOf(keystone(isSelected = true), ledger(isSelected = true)).forEach { account ->
                val vm = vm(MutableStateFlow(account))
                collect(vm)
                val state = assertNotNull(vm.state.value.content)

                assertEquals(account.name, state.connectedTitle)
                assertEquals(R.string.currentlyConnected, state.connectedStatus.resourceId())
            }
        }

    @Test
    fun theButtonsReadDisconnectInTitleCaseWhileTheAppBarKeepsItsOwnTitle() =
        runTest {
            val vm = vm(MutableStateFlow(keystone(isSelected = true)))
            collect(vm)
            val state = assertNotNull(vm.state.value.content)

            assertEquals(R.string.disconnectHWWallet_title, state.header.resourceId())
            assertEquals(R.string.disconnectHWWallet_button, state.disconnectButton.text.resourceId())
            assertEquals(ButtonStyle.DESTRUCTIVE1, state.disconnectButton.style)
        }

    @Test
    fun theConfirmationSheetOffersTheOutlinedDisconnectAboveCancel() =
        runTest {
            val vm = vm(MutableStateFlow(keystone(isSelected = true)))
            collect(vm)

            assertNotNull(vm.state.value.content).disconnectButton.onClick()
            runCurrent()
            val sheet = assertNotNull(assertNotNull(vm.state.value.content).confirmationDialog)

            assertEquals(R.drawable.ic_disconnect_sheet_info, sheet.icon)
            assertEquals(R.string.deleteWallet_sheet_title, sheet.title.resourceId())
            assertEquals(R.string.disconnectHWWallet_sheetDesc, sheet.message.resourceId())
            assertEquals(R.string.disconnectHWWallet_button, sheet.primaryAction.text.resourceId())
            assertEquals(ButtonStyle.DESTRUCTIVE1, sheet.primaryAction.style)
            val cancel = assertNotNull(sheet.secondaryAction)
            assertEquals(UIR.string.general_cancel, cancel.text.resourceId())
            assertEquals(ButtonStyle.PRIMARY, cancel.style)

            cancel.onClick()
            runCurrent()
            assertNull(assertNotNull(vm.state.value.content).confirmationDialog)
        }

    @Test
    fun aFailedDisconnectOffersContactSupportAboveTheTryAgainButtonThatRetries() =
        runTest {
            val account = keystone(isSelected = true)
            val disconnect =
                mockk<DisconnectUseCase> {
                    coEvery { this@mockk(account) } throws IllegalStateException("boom")
                }
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = vm(MutableStateFlow(account), disconnect, navigationRouter)
            collect(vm)

            assertNotNull(vm.state.value.content).disconnectButton.onClick()
            runCurrent()
            assertNotNull(assertNotNull(vm.state.value.content).confirmationDialog).primaryAction.onClick()
            runCurrent()

            val sheet = assertIs<LceError.BottomSheet>(vm.state.value.error).state
            assertEquals(R.drawable.ic_disconnect_sheet_info, sheet.icon)
            assertEquals(R.string.disconnectHWWallet_failureTitle, sheet.title.resourceId())
            assertEquals(R.string.disconnect_hardware_wallet_error_message, sheet.message.resourceId())
            assertEquals(UIR.string.disconnectHWWallet_contactSupport, sheet.primaryAction.text.resourceId())
            assertEquals(ButtonStyle.SECONDARY, sheet.primaryAction.style)
            val tryAgain = assertNotNull(sheet.secondaryAction)
            assertEquals(UIR.string.disconnectHWWallet_tryAgain, tryAgain.text.resourceId())
            assertEquals(ButtonStyle.PRIMARY, tryAgain.style)

            tryAgain.onClick()
            runCurrent()

            coVerify(exactly = 2) { disconnect.invoke(account) }
            verify(exactly = 0) { navigationRouter.backToRoot() }
        }

    @Test
    fun openingOnTheSoftwareWalletGoesBack() =
        runTest {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            vm(MutableStateFlow(zodl(isSelected = true)), navigationRouter = navigationRouter)
            runCurrent()

            verify(exactly = 1) { navigationRouter.back() }
        }

    @Test
    fun switchingTheSelectedHardwareWalletUpdatesTheScreen() =
        runTest {
            val selected = MutableStateFlow<WalletAccount?>(keystone(isSelected = true))
            val vm = vm(selected)
            collect(vm)
            assertShowsKeystone(assertNotNull(vm.state.value.content))

            selected.value = ledger(isSelected = true)
            runCurrent()

            assertShowsLedger(assertNotNull(vm.state.value.content))
        }

    @Test
    fun confirmingDisconnectDeletesTheLedgerAccountAndUnwinds() =
        runTest {
            val account = ledger(isSelected = true)
            val disconnect = mockk<DisconnectUseCase>(relaxed = true)
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = vm(MutableStateFlow(account), disconnect, navigationRouter)
            collect(vm)

            assertNotNull(vm.state.value.content).disconnectButton.onClick()
            runCurrent()
            assertNotNull(
                assertNotNull(vm.state.value.content).confirmationDialog
            ).primaryAction.onClick()
            runCurrent()

            coVerify(exactly = 1) { disconnect.invoke(account) }
            verify(exactly = 1) { navigationRouter.backToRoot() }
        }

    @Test
    fun theSelectionMovingToZashiAfterDisconnectDoesNotBlankTheScreen() =
        runTest {
            val account = ledger(isSelected = true)
            val selected = MutableStateFlow<WalletAccount?>(account)
            val disconnect =
                mockk<DisconnectUseCase> {
                    coEvery { this@mockk(account) } coAnswers { selected.value = zodl(isSelected = true) }
                }
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = vm(selected, disconnect, navigationRouter)
            collect(vm)

            assertNotNull(vm.state.value.content).disconnectButton.onClick()
            runCurrent()
            assertNotNull(
                assertNotNull(vm.state.value.content).confirmationDialog
            ).primaryAction.onClick()
            runCurrent()

            assertShowsLedger(assertNotNull(vm.state.value.content))
            verify(exactly = 1) { navigationRouter.backToRoot() }
            verify(exactly = 0) { navigationRouter.back() }
        }

    @Test
    fun aNamedHardwareWalletIsDisconnectedEvenWhileAnotherAccountIsSelected() =
        runTest {
            val zodl = zodl(isSelected = true)
            val ledger = ledger(isSelected = false).copy(sdkAccount = Account.new(otherUuid))
            val disconnect = mockk<DisconnectUseCase>(relaxed = true)
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm =
                vm(
                    selected = MutableStateFlow(zodl),
                    disconnect = disconnect,
                    navigationRouter = navigationRouter,
                    accounts = MutableStateFlow(listOf(zodl, ledger)),
                    args = DisconnectArgs(accountKeyId = otherUuid.toStorageKeyId()),
                )
            collect(vm)
            assertShowsLedger(assertNotNull(vm.state.value.content))

            assertNotNull(vm.state.value.content).disconnectButton.onClick()
            runCurrent()
            assertNotNull(
                assertNotNull(vm.state.value.content).confirmationDialog
            ).primaryAction.onClick()
            runCurrent()

            coVerify(exactly = 1) { disconnect.invoke(ledger) }
            verify(exactly = 1) { navigationRouter.backTo(AccountListArgs::class) }
            verify(exactly = 0) { navigationRouter.backToRoot() }
            verify(exactly = 0) { navigationRouter.back() }
        }

    @Test
    fun aNamedAccountThatIsMissingOrNotAHardwareWalletGoesBack() =
        runTest {
            val zodl = zodl(isSelected = true)
            listOf(DisconnectArgs(accountKeyId = defaultUuid.toStorageKeyId()), DisconnectArgs("missing"))
                .forEach { args ->
                    val navigationRouter = mockk<NavigationRouter>(relaxed = true)
                    vm(
                        selected = MutableStateFlow(keystone(isSelected = true)),
                        navigationRouter = navigationRouter,
                        accounts = MutableStateFlow(listOf(zodl)),
                        args = args,
                    )
                    runCurrent()

                    verify(exactly = 1) { navigationRouter.back() }
                }
        }

    @Test
    fun theDisconnectUseCaseDeletesTheSelectedAccountAndSelectsTheSoftwareWallet() =
        runTest {
            val account = ledger(isSelected = true).copy(sdkAccount = Account.new(otherUuid))
            val zodl = zodl()
            val accountDataSource =
                mockk<AccountDataSource>(relaxed = true) {
                    coEvery { getAllAccounts() } returns listOf(zodl, account)
                    coEvery { getZashiAccount() } returns zodl
                }

            disconnectUseCase(accountDataSource)(account)

            coVerify(exactly = 1) { accountDataSource.deleteAccount(account) }
            coVerify(exactly = 1) { accountDataSource.selectAccount(zodl) }
        }

    @Test
    fun disconnectingAnUnselectedAccountLeavesTheSelectionAlone() =
        runTest {
            val account = ledger(isSelected = false).copy(sdkAccount = Account.new(otherUuid))
            val zodl = zodl(isSelected = true)
            val accountDataSource =
                mockk<AccountDataSource>(relaxed = true) {
                    coEvery { getAllAccounts() } returns listOf(zodl, account)
                    coEvery { getZashiAccount() } returns zodl
                }

            disconnectUseCase(accountDataSource)(account)

            coVerify(exactly = 1) { accountDataSource.deleteAccount(account) }
            coVerify(exactly = 0) { accountDataSource.selectAccount(any<WalletAccount>()) }
            coVerify(exactly = 0) { accountDataSource.selectAccount(any<Account>()) }
        }

    @Test
    fun theSelectionIsReadAgainRatherThanTakenFromTheStaleAccount() =
        runTest {
            val stale = ledger(isSelected = false).copy(sdkAccount = Account.new(otherUuid))
            val zodl = zodl()
            val accountDataSource =
                mockk<AccountDataSource>(relaxed = true) {
                    coEvery { getAllAccounts() } returns listOf(zodl, stale.copy(isSelected = true))
                    coEvery { getZashiAccount() } returns zodl
                }

            disconnectUseCase(accountDataSource)(stale)

            coVerify(exactly = 1) { accountDataSource.selectAccount(zodl) }
        }

    private fun disconnectUseCase(accountDataSource: AccountDataSource) =
        DisconnectUseCase(
            accountDataSource = accountDataSource,
            biometricRepository = mockk(relaxed = true),
            migrationAppHooks = mockk(relaxed = true),
        )

    private fun vm(
        selected: MutableStateFlow<WalletAccount?>,
        disconnect: DisconnectUseCase = mockk(relaxed = true),
        navigationRouter: NavigationRouter = mockk(relaxed = true),
        accounts: MutableStateFlow<List<WalletAccount>?> = MutableStateFlow(null),
        args: DisconnectArgs = DisconnectArgs(),
    ) = DisconnectVM(
        args = args,
        observeSelectedWalletAccount =
            mockk {
                every { this@mockk() } returns selected
                every { require() } returns selected.filterNotNull()
            },
        getWalletAccounts =
            mockk<GetWalletAccountsUseCase> {
                every { observe() } returns accounts
                every { require() } returns accounts.filterNotNull()
            },
        disconnect = disconnect,
        navigationRouter = navigationRouter,
        errorStateMapper =
            ErrorMapperUseCase(
                sendEmail = mockk(relaxed = true),
                viewOnlyContent = mockk(relaxed = true),
            ),
    )

    private fun zodl(isSelected: Boolean = false) =
        ZodlAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u",
            transparentAddress = "t",
            saplingAddress = "s",
            orchardBalance = null,
            saplingBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = isSelected,
        )

    private fun keystone(isSelected: Boolean = false) =
        KeystoneAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = isSelected,
        )

    private fun ledger(isSelected: Boolean = false) =
        LedgerAccount(
            sdkAccount = sdkAccount,
            unifiedAddress = "u",
            transparentAddress = "t",
            orchardBalance = null,
            ironwoodBalance = null,
            transparentBalance = null,
            isSelected = isSelected,
            deviceIdentity = "tpk0-deadbeef",
            zip32AccountIndex = Zip32AccountIndex.new(0L),
        )

    private fun assertShowsKeystone(state: DisconnectState) {
        assertEquals(R.string.deleteKeystoneTitle, state.title.resourceId())
        assertEquals(R.string.accounts_keystone, state.connectedTitle.resourceId())
        assertEquals(co.electriccoin.zcash.ui.design.R.drawable.ic_item_keystone, state.icon)
        assertEquals(R.string.connectedHWInfo, state.infoText.resourceId())
    }

    private fun assertShowsLedger(state: DisconnectState) {
        assertEquals(R.string.ledger_disconnect_title, state.title.resourceId())
        assertEquals(R.string.accounts_ledger, state.connectedTitle.resourceId())
        assertEquals(co.electriccoin.zcash.ui.design.R.drawable.ic_item_ledger, state.icon)
        assertEquals(R.string.connectedHWInfo_ledger, state.infoText.resourceId())
    }

    private fun TestScope.collect(vm: DisconnectVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
    }

    private fun StringResource.resourceId(): Int = (this as StringResource.ByResource).resource
}
