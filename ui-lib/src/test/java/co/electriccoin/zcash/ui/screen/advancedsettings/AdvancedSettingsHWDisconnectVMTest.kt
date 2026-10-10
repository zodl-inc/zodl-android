package co.electriccoin.zcash.ui.screen.advancedsettings

import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.Zip32AccountIndex
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.migration.MigrationGate
import co.electriccoin.zcash.ui.common.migration.MigrationNavigator
import co.electriccoin.zcash.ui.common.model.KeystoneAccount
import co.electriccoin.zcash.ui.common.model.LedgerAccount
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.model.WalletRestoringState
import co.electriccoin.zcash.ui.common.model.ZashiAccount
import co.electriccoin.zcash.ui.common.provider.GetVersionInfoProvider
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.common.usecase.GetWalletRestoringStateUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToExportPrivateDataUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToResetWalletUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToTaxExportUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToWalletBackupUseCase
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.fixture.VersionInfoFixture
import co.electriccoin.zcash.ui.screen.disconnect.DisconnectArgs
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The Advanced settings screen's hardware-wallet disconnect row must name whichever vendor is
 * currently selected and disappear entirely once a Zodl (software) account is selected.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdvancedSettingsHWDisconnectVMTest {
    private val defaultUuid = AccountUuid.new(ByteArray(16) { it.toByte() })
    private val sdkAccount = Account.new(defaultUuid)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theLedgerAccountGetsExactlyOneDisconnectRowTitledForLedger() =
        runTest {
            val vm = vm(MutableStateFlow(listOf(ledger(isSelected = true))))
            collect(vm)

            assertEquals(R.string.ledger_disconnect_title, assertNotNull(disconnectRow(vm)).title.resourceIdOrNull())
        }

    @Test
    fun theKeystoneAccountGetsExactlyOneDisconnectRowTitledForKeystone() =
        runTest {
            val vm = vm(MutableStateFlow(listOf(keystone(isSelected = true))))
            collect(vm)

            assertEquals(R.string.disconnectHWWallet_cta, assertNotNull(disconnectRow(vm)).title.resourceIdOrNull())
        }

    @Test
    fun theZashiAccountGetsNoDisconnectRowEvenWithAKeystoneAccountPresent() =
        runTest {
            val vm =
                vm(
                    MutableStateFlow(
                        listOf(zashi(isSelected = true), keystone(isSelected = false))
                    )
                )
            collect(vm)

            assertNull(disconnectRow(vm))
        }

    @Test
    fun tappingTheDisconnectRowForwardsToDisconnect() =
        runTest {
            val navigationRouter = mockk<NavigationRouter>(relaxed = true)
            val vm = vm(MutableStateFlow(listOf(ledger(isSelected = true))), navigationRouter = navigationRouter)
            collect(vm)

            assertNotNull(disconnectRow(vm)).onClick?.invoke()
            runCurrent()

            verify(exactly = 1) { navigationRouter.forward(DisconnectArgs) }
        }

    private fun disconnectRow(vm: AdvancedSettingsVM) =
        vm.state.value.items.singleOrNull {
            it.title.resourceIdOrNull() == R.string.ledger_disconnect_title ||
                it.title.resourceIdOrNull() == R.string.disconnectHWWallet_cta
        }

    private fun vm(
        accounts: MutableStateFlow<List<WalletAccount>?>,
        navigationRouter: NavigationRouter = mockk(relaxed = true),
    ) = AdvancedSettingsVM(
        getWalletRestoringState =
            mockk {
                every { observe() } returns MutableStateFlow(WalletRestoringState.NONE)
            },
        getWalletAccounts =
            mockk {
                every { observe() } returns accounts
            },
        navigationRouter = navigationRouter,
        navigateToTaxExport = mockk<NavigateToTaxExportUseCase>(relaxed = true),
        navigateToWalletBackup = mockk<NavigateToWalletBackupUseCase>(relaxed = true),
        getVersionInfo =
            mockk<GetVersionInfoProvider> {
                every { this@mockk() } returns VersionInfoFixture.new()
            },
        navigateToResetWallet = mockk<NavigateToResetWalletUseCase>(relaxed = true),
        navigateToExportPrivateData = mockk<NavigateToExportPrivateDataUseCase>(relaxed = true),
        migrationGate =
            mockk<MigrationGate> {
                coEvery { isRestartAvailable() } returns false
            },
        migrationNavigator = mockk<MigrationNavigator>(relaxed = true),
    )

    private fun zashi(isSelected: Boolean = false) =
        ZashiAccount(
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

    private fun StringResource.resourceIdOrNull(): Int? = (this as? StringResource.ByResource)?.resource

    private fun TestScope.collect(vm: AdvancedSettingsVM) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
    }
}
