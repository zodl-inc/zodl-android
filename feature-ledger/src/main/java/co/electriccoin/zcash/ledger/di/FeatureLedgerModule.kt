package co.electriccoin.zcash.ledger.di

import co.electriccoin.zcash.ledger.LedgerAccountImporterImpl
import co.electriccoin.zcash.ledger.LedgerNavContributorImpl
import co.electriccoin.zcash.ledger.LedgerNavigatorImpl
import co.electriccoin.zcash.ui.common.datasource.LedgerDeviceDataSource
import co.electriccoin.zcash.ui.common.datasource.LedgerDeviceDataSourceImpl
import co.electriccoin.zcash.ui.common.datasource.LedgerSigningDataSource
import co.electriccoin.zcash.ui.common.datasource.LedgerSigningDataSourceImpl
import co.electriccoin.zcash.ui.common.ledger.LedgerAccountImporter
import co.electriccoin.zcash.ui.common.ledger.LedgerNavContributor
import co.electriccoin.zcash.ui.common.ledger.LedgerNavigator
import co.electriccoin.zcash.ui.common.ledger.LedgerProposalPipeline
import co.electriccoin.zcash.ui.common.provider.LedgerScannerProvider
import co.electriccoin.zcash.ui.common.provider.LedgerScannerProviderImpl
import co.electriccoin.zcash.ui.common.repository.LedgerPairingRepository
import co.electriccoin.zcash.ui.common.repository.LedgerPairingRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.LedgerProposalRepository
import co.electriccoin.zcash.ui.common.repository.LedgerProposalRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.LedgerRepairTargetRepository
import co.electriccoin.zcash.ui.common.repository.LedgerRepairTargetRepositoryImpl
import co.electriccoin.zcash.ui.common.repository.LedgerSelectedDeviceRepository
import co.electriccoin.zcash.ui.common.repository.LedgerSelectedDeviceRepositoryImpl
import co.electriccoin.zcash.ui.common.usecase.CancelLedgerSigningUseCase
import co.electriccoin.zcash.ui.common.usecase.ConnectLedgerDeviceUseCase
import co.electriccoin.zcash.ui.common.usecase.CreateLedgerAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToLedgerRepairUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveLedgerDevicesUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveLedgerSigningStateUseCase
import co.electriccoin.zcash.ui.common.usecase.PairLedgerDeviceUseCase
import co.electriccoin.zcash.ui.common.usecase.RetryLedgerSigningUseCase
import co.electriccoin.zcash.ui.common.usecase.SelectLedgerSigningDeviceUseCase
import co.electriccoin.zcash.ui.common.usecase.StartLedgerSigningUseCase
import co.electriccoin.zcash.ui.common.usecase.SubmitLedgerProposalUseCase
import co.electriccoin.zcash.ui.screen.connectledger.connect.LedgerConnectVM
import co.electriccoin.zcash.ui.screen.connectledger.connected.LedgerConnectedVM
import co.electriccoin.zcash.ui.screen.connectledger.handshake.LedgerHandshakeVM
import co.electriccoin.zcash.ui.screen.connectledger.openapp.LedgerOpenAppVM
import co.electriccoin.zcash.ui.screen.connectledger.scan.LedgerDeviceScanVM
import co.electriccoin.zcash.ui.screen.connectledger.turnon.LedgerTurnOnVM
import co.electriccoin.zcash.ui.screen.signledgertransaction.LedgerSignVM
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * Everything the Ledger feature contributes to the app's Koin graph — its providers, data sources,
 * repositories, use cases, view models, and the implementations of ui-lib's Ledger contracts (see
 * LedgerContracts.kt). Wired in ZcashApplication.startKoin next to the migration and voting modules.
 * The proposal repository is one instance under both of its types, so the send flows ui-lib drives
 * through [LedgerProposalPipeline] and the sign sheet share the same proposal and session.
 */
val featureLedgerModule =
    module {
        singleOf(::LedgerNavContributorImpl) bind LedgerNavContributor::class
        singleOf(::LedgerNavigatorImpl) bind LedgerNavigator::class
        singleOf(::LedgerAccountImporterImpl) bind LedgerAccountImporter::class

        singleOf(::LedgerScannerProviderImpl) bind LedgerScannerProvider::class

        singleOf(::LedgerDeviceDataSourceImpl) bind LedgerDeviceDataSource::class
        singleOf(::LedgerSigningDataSourceImpl) bind LedgerSigningDataSource::class

        singleOf(::LedgerPairingRepositoryImpl) bind LedgerPairingRepository::class
        singleOf(::LedgerProposalRepositoryImpl) binds
            arrayOf(LedgerProposalRepository::class, LedgerProposalPipeline::class)
        singleOf(::LedgerSelectedDeviceRepositoryImpl) bind LedgerSelectedDeviceRepository::class
        singleOf(::LedgerRepairTargetRepositoryImpl) bind LedgerRepairTargetRepository::class

        factoryOf(::CreateLedgerAccountUseCase)
        factoryOf(::ObserveLedgerDevicesUseCase)
        factoryOf(::PairLedgerDeviceUseCase)
        factoryOf(::ConnectLedgerDeviceUseCase)
        singleOf(::SubmitLedgerProposalUseCase)
        factoryOf(::ObserveLedgerSigningStateUseCase)
        factoryOf(::StartLedgerSigningUseCase)
        factoryOf(::SelectLedgerSigningDeviceUseCase)
        factoryOf(::RetryLedgerSigningUseCase)
        factoryOf(::CancelLedgerSigningUseCase)
        factoryOf(::NavigateToLedgerRepairUseCase)

        viewModelOf(::LedgerConnectVM)
        viewModelOf(::LedgerTurnOnVM)
        viewModelOf(::LedgerConnectedVM)
        viewModelOf(::LedgerDeviceScanVM)
        viewModelOf(::LedgerOpenAppVM)
        viewModelOf(::LedgerHandshakeVM)
        viewModelOf(::LedgerSignVM)
    }
