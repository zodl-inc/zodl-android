package co.electriccoin.zcash.viewingkey.di

import co.electriccoin.zcash.ui.common.repository.ViewOnlyPendingImportRepository
import co.electriccoin.zcash.ui.common.repository.ViewOnlyPendingImportRepositoryImpl
import co.electriccoin.zcash.ui.common.usecase.GetViewOnlyAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.ImportViewOnlyAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.RemoveViewOnlyAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.SdkUfvkDescriber
import co.electriccoin.zcash.ui.common.usecase.SubmitViewOnlyKeyUseCase
import co.electriccoin.zcash.ui.common.usecase.UfvkDescriber
import co.electriccoin.zcash.ui.common.usecase.ValidateUfvkUseCase
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyAccountImporter
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyNavContributor
import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyNavigator
import co.electriccoin.zcash.ui.screen.viewingkey.addaccount.AddVKAccountVM
import co.electriccoin.zcash.ui.screen.viewingkey.alreadyimported.AlreadyImportedVKVM
import co.electriccoin.zcash.ui.screen.viewingkey.imported.ImportedVKVM
import co.electriccoin.zcash.ui.screen.viewingkey.importvk.ImportVKVM
import co.electriccoin.zcash.ui.screen.viewingkey.name.NameVKVM
import co.electriccoin.zcash.ui.screen.viewingkey.remove.RemoveVKAccountVM
import co.electriccoin.zcash.ui.screen.viewingkey.scan.ScanVKVM
import co.electriccoin.zcash.viewingkey.ViewOnlyAccountImporterImpl
import co.electriccoin.zcash.viewingkey.ViewOnlyContentImpl
import co.electriccoin.zcash.viewingkey.ViewOnlyNavContributorImpl
import co.electriccoin.zcash.viewingkey.ViewOnlyNavigatorImpl
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Everything the view-only feature contributes to the app's Koin graph: the implementations of
 * ui-lib's view-only contracts (see ViewOnlyContracts.kt), the module's copy and images behind
 * [ViewOnlyContent], the in-memory pending import, and the use cases and view models of the import
 * flow and of the remove flow.
 * Wired in ZcashApplication.startKoin next to the Ledger, migration and voting modules.
 */
val featureViewingKeyModule =
    module {
        singleOf(::ViewOnlyContentImpl) bind ViewOnlyContent::class
        singleOf(::ViewOnlyNavContributorImpl) bind ViewOnlyNavContributor::class
        singleOf(::ViewOnlyNavigatorImpl) bind ViewOnlyNavigator::class
        singleOf(::ViewOnlyAccountImporterImpl) bind ViewOnlyAccountImporter::class

        singleOf(::ViewOnlyPendingImportRepositoryImpl) bind ViewOnlyPendingImportRepository::class

        factoryOf(::SdkUfvkDescriber) bind UfvkDescriber::class
        factoryOf(::ValidateUfvkUseCase)
        factoryOf(::SubmitViewOnlyKeyUseCase)
        singleOf(::ImportViewOnlyAccountUseCase)
        factoryOf(::GetViewOnlyAccountUseCase)
        factoryOf(::RemoveViewOnlyAccountUseCase)

        viewModelOf(::AddVKAccountVM)
        viewModelOf(::ImportVKVM)
        viewModelOf(::ScanVKVM)
        viewModelOf(::NameVKVM)
        viewModelOf(::ImportedVKVM)
        viewModelOf(::AlreadyImportedVKVM)
        viewModelOf(::RemoveVKAccountVM)
    }
