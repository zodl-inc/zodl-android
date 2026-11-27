package co.electriccoin.zcash.ui.screen.viewingkey

import androidx.compose.ui.graphics.Color
import cash.z.ecc.android.sdk.model.AccountUuid
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.WalletAccount
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.ViewOnlyAccountFixtures
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.screen.viewingkey.alreadyimported.AlreadyImportedVKVM
import co.electriccoin.zcash.ui.screen.viewingkey.imported.ImportedVKVM
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Both ends of the flow close it: the account is imported and selected, or it was in the wallet
 * already. The success screen shows the new account in its stored colour.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VKFlowEndVMTest {
    private val navigationRouter = mockk<NavigationRouter>(relaxed = true)

    private val selected = MutableStateFlow<WalletAccount?>(ViewOnlyAccountFixtures.viewOnly(isSelected = true))

    private val colors = MutableStateFlow<Map<AccountUuid, Color>>(emptyMap())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun doneAndBackOnTheSuccessScreenReturnToTheRoot() {
        val state = importedVM().state.value

        state.doneButton.onClick()
        state.onBack()

        verify(exactly = 2) { navigationRouter.backToRoot() }
    }

    @Test
    fun theSuccessScreenShowsTheNewAccountInItsColor() =
        runTest {
            val vm = importedVM()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
            assertEquals(Color.Unspecified, vm.avatarBackground())

            colors.value = mapOf(ViewOnlyAccountFixtures.viewOnlyUuid to Color(PICKED))

            assertEquals(Color(PICKED), vm.avatarBackground())
        }

    @Test
    fun closingTheAlreadyImportedSheetReturnsToTheRoot() {
        val state = AlreadyImportedVKVM(navigationRouter).state.value

        state.closeButton.onClick()

        verify(exactly = 1) { navigationRouter.backToRoot() }
    }

    private fun importedVM() =
        ImportedVKVM(
            observeSelectedWalletAccount =
                mockk<ObserveSelectedWalletAccountUseCase> { every { require() } returns selected.filterNotNull() },
            metadataRepository =
                mockk<MetadataRepository> {
                    every { observeColor(any()) } answers { colors.map { it[firstArg()] } }
                },
            navigationRouter = navigationRouter,
        )

    private fun ImportedVKVM.avatarBackground() = (state.value.header.avatar.image as ImageResource.Layered).background

    private companion object {
        const val PICKED = 0xFF0088FF
    }
}
