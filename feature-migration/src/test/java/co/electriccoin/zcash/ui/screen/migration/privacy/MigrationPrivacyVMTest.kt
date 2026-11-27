package co.electriccoin.zcash.ui.screen.migration.privacy

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.migration.MigrationMode
import co.electriccoin.zcash.ui.common.provider.IsMigrationTorEnabledStorageProvider
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MigrationPrivacyVMTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Toggling the checkbox must only update local VM state — this is a migration-scoped choice,
    // not the app's global Tor setting, and must not be persisted until the user actually commits
    // via Confirm.
    @Test
    fun togglingCheckboxDoesNotPersistAnything() =
        runTest {
            val torProvider = mockk<IsMigrationTorEnabledStorageProvider>(relaxed = true)
            val vm = vm(torProvider = torProvider)
            val collectJob = launch { vm.state.collect {} }
            advanceUntilIdle()

            vm.state.value
                ?.checkbox
                ?.onClick
                ?.invoke()
            advanceUntilIdle()

            coVerify(exactly = 0) { torProvider.store(any()) }
            collectJob.cancel()
        }

    @Test
    fun confirmPersistsTheCurrentToggleValueIntoTheMigrationScopedProvider() =
        runTest {
            val torProvider = mockk<IsMigrationTorEnabledStorageProvider>(relaxed = true)
            val vm = vm(torProvider = torProvider)
            val collectJob = launch { vm.state.collect {} }
            advanceUntilIdle()

            // Default is true; toggle off, then confirm.
            vm.state.value
                ?.checkbox
                ?.onClick
                ?.invoke()
            advanceUntilIdle()
            vm.state.value
                ?.onConfirm
                ?.invoke()
            advanceUntilIdle()

            coVerify(exactly = 1) { torProvider.store(false) }
            collectJob.cancel()
        }

    @Test
    fun confirmWithoutTogglingPersistsDefaultTrue() =
        runTest {
            val torProvider = mockk<IsMigrationTorEnabledStorageProvider>(relaxed = true)
            val vm = vm(torProvider = torProvider)
            val collectJob = launch { vm.state.collect {} }
            advanceUntilIdle()

            vm.state.value
                ?.onConfirm
                ?.invoke()
            advanceUntilIdle()

            coVerify(exactly = 1) { torProvider.store(true) }
            collectJob.cancel()
        }

    @Test
    fun checkboxDefaultsToCheckedRegardlessOfProviderState() =
        runTest {
            val vm = vm()
            val collectJob = launch { vm.state.collect {} }
            advanceUntilIdle()

            assertEquals(
                true,
                vm.state.value
                    ?.checkbox
                    ?.isChecked
            )
            collectJob.cancel()
        }

    private fun vm(
        mode: MigrationMode = MigrationMode.AUTOMATIC,
        router: NavigationRouter = mockk(relaxed = true),
        torProvider: IsMigrationTorEnabledStorageProvider = mockk(relaxed = true),
    ) = MigrationPrivacyVM(
        args = MigrationPrivacyArgs(mode = mode),
        navigationRouter = router,
        isMigrationTorEnabledStorageProvider = torProvider,
    )
}
