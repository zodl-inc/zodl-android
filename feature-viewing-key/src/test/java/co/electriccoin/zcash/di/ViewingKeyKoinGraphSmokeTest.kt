package co.electriccoin.zcash.di

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.migration.MigrationAppHooks
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.BiometricRepository
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import co.electriccoin.zcash.ui.common.repository.ViewOnlyPendingImportRepository
import co.electriccoin.zcash.ui.common.usecase.ClearClipboardUseCase
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetViewOnlyAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.GetWalletAccountsUseCase
import co.electriccoin.zcash.ui.common.usecase.ImportViewOnlyAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.IsAccountNameTakenUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToColorPickerUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveAccountIconUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.RemoveViewOnlyAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.RenameViewOnlyAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitViewOnlyKeyUseCase
import co.electriccoin.zcash.ui.common.usecase.ValidateUfvkUseCase
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyAccountImporter
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyNavContributor
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyNavigator
import co.electriccoin.zcash.ui.screen.editaccount.EditAccountArgs
import co.electriccoin.zcash.ui.screen.editaccount.EditAccountVM
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import co.electriccoin.zcash.ui.screen.viewingkey.addaccount.AddVKAccountVM
import co.electriccoin.zcash.ui.screen.viewingkey.alreadyimported.AlreadyImportedVKVM
import co.electriccoin.zcash.ui.screen.viewingkey.imported.ImportedVKVM
import co.electriccoin.zcash.ui.screen.viewingkey.importvk.ImportVKVM
import co.electriccoin.zcash.ui.screen.viewingkey.name.NameVKVM
import co.electriccoin.zcash.ui.screen.viewingkey.remove.RemoveVKAccountArgs
import co.electriccoin.zcash.ui.screen.viewingkey.remove.RemoveVKAccountVM
import co.electriccoin.zcash.ui.screen.viewingkey.scan.ScanVKVM
import co.electriccoin.zcash.viewingkey.di.featureViewingKeyModule
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.KoinApplication
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * Smoke-tests the Koin graph of [featureViewingKeyModule], like LedgerKoinGraphSmokeTest does for its
 * module: every contract ui-lib resolves, [ViewOnlyContent] included, and every use case and view
 * model of the import flow and of the remove flow must come out of the module plus stubs
 * for the types ui-lib owns. ui-lib's [EditAccountVM] is declared here as ui-lib declares it, so the
 * contracts it takes from this module are checked too. A missing binding fails here with a
 * NoDefinitionFoundException instead of when a screen opens on a device. The main dispatcher is a
 * standard test dispatcher so work the view models launch at construction never runs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewingKeyKoinGraphSmokeTest {
    private lateinit var koin: KoinApplication

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        val stubsModule =
            module {
                single<NavigationRouter> { mockk(relaxed = true) }
                single<AccountDataSource> { mockk(relaxed = true) }
                single<SynchronizerProvider> { mockk(relaxed = true) }
                factory { mockk<GetWalletAccountsUseCase>(relaxed = true) }
                factory { mockk<ClearClipboardUseCase>(relaxed = true) }
                single<BiometricRepository> { mockk(relaxed = true) }
                single<MigrationAppHooks> { mockk(relaxed = true) }
                factory { mockk<NavigateToErrorUseCase>(relaxed = true) }
                single { mockk<NavigateToColorPickerUseCase>(relaxed = true) }
                single<MetadataRepository> { mockk(relaxed = true) }
                factory { mockk<ObserveSelectedWalletAccountUseCase>(relaxed = true) }
                factory { mockk<RenameViewOnlyAccountUseCase>(relaxed = true) }
                factory { mockk<ObserveAccountIconUseCase>(relaxed = true) }
                factory { mockk<IsAccountNameTakenUseCase>(relaxed = true) }
                factory { mockk<ErrorMapperUseCase>(relaxed = true) }
                viewModelOf(::EditAccountVM)
            }

        koin =
            koinApplication {
                modules(featureViewingKeyModule, stubsModule)
            }
    }

    @AfterTest
    fun tearDown() {
        koin.close()
        Dispatchers.resetMain()
    }

    @Test
    fun contracts_resolveFromKoin() {
        koin.koin.get<ViewOnlyNavContributor>()
        koin.koin.get<ViewOnlyNavigator>()
        koin.koin.get<ViewOnlyAccountImporter>()
    }

    @Test
    fun useCases_resolveFromKoin() {
        koin.koin.get<ValidateUfvkUseCase>()
        koin.koin.get<SubmitViewOnlyKeyUseCase>()
        koin.koin.get<ImportViewOnlyAccountUseCase>()
        koin.koin.get<GetViewOnlyAccountUseCase>()
        koin.koin.get<RemoveViewOnlyAccountUseCase>()
    }

    @Test
    fun viewModels_resolveFromKoin() {
        koin.koin.get<AddVKAccountVM>()
        koin.koin.get<ImportVKVM>()
        koin.koin.get<ScanVKVM>()
        koin.koin.get<NameVKVM>()
        koin.koin.get<ImportedVKVM>()
        koin.koin.get<AlreadyImportedVKVM>()
        koin.koin.get<RemoveVKAccountVM> { parametersOf(RemoveVKAccountArgs(ACCOUNT_KEY_ID)) }
        koin.koin.get<EditAccountVM> { parametersOf(EditAccountArgs(ACCOUNT_KEY_ID)) }
    }

    @Test
    fun content_resolvesFromKoin() {
        koin.koin.get<ViewOnlyContent>()
    }

    @Test
    fun pendingImport_isOneSharedInstance() {
        assertSame(koin.koin.get<ViewOnlyPendingImportRepository>(), koin.koin.get<ViewOnlyPendingImportRepository>())
    }

    private companion object {
        const val ACCOUNT_KEY_ID = "00"
    }
}
