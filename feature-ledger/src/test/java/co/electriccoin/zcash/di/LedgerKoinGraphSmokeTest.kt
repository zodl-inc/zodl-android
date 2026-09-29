package co.electriccoin.zcash.di

import android.app.Application
import android.content.Context
import co.electriccoin.zcash.ledger.di.featureLedgerModule
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.datasource.ProposalDataSource
import co.electriccoin.zcash.ui.common.ledger.LedgerAccountImporter
import co.electriccoin.zcash.ui.common.ledger.LedgerNavContributor
import co.electriccoin.zcash.ui.common.ledger.LedgerNavigator
import co.electriccoin.zcash.ui.common.ledger.LedgerProposalPipeline
import co.electriccoin.zcash.ui.common.provider.LedgerAccountBindingProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.LedgerProposalRepository
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.usecase.ObserveProposalUseCase
import co.electriccoin.zcash.ui.common.usecase.PrefillSendUseCase
import co.electriccoin.zcash.ui.common.usecase.ProcessSwapTransactionUseCase
import co.electriccoin.zcash.ui.common.usecase.SelectWalletAccountUseCase
import co.electriccoin.zcash.ui.screen.connectledger.connect.LedgerConnectVM
import co.electriccoin.zcash.ui.screen.connectledger.connected.LedgerConnectedVM
import co.electriccoin.zcash.ui.screen.connectledger.handshake.LedgerHandshakeVM
import co.electriccoin.zcash.ui.screen.connectledger.openapp.LedgerOpenAppArgs
import co.electriccoin.zcash.ui.screen.connectledger.openapp.LedgerOpenAppVM
import co.electriccoin.zcash.ui.screen.connectledger.scan.LedgerDeviceScanVM
import co.electriccoin.zcash.ui.screen.connectledger.turnon.LedgerTurnOnVM
import co.electriccoin.zcash.ui.screen.error.NavigateToErrorUseCase
import co.electriccoin.zcash.ui.screen.signledgertransaction.LedgerSignVM
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * Smoke-tests the Koin graph of [featureLedgerModule], like MigrationKoinGraphSmokeTest and
 * VotingKoinGraphSmokeTest do for their modules: every Ledger view model and every contract ui-lib
 * resolves must come out of the module plus stubs for the types ui-lib owns. A missing binding fails
 * here with a NoDefinitionFoundException instead of when a screen opens on a device. The main
 * dispatcher is a standard test dispatcher so work the view models launch at construction never runs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerKoinGraphSmokeTest {
    private lateinit var koin: KoinApplication

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        val stubsModule =
            module {
                single<Context> { mockk(relaxed = true) }
                single<Application> { mockk(relaxed = true) }
                single<NavigationRouter> { mockk(relaxed = true) }

                factory { mockk<SelectWalletAccountUseCase>(relaxed = true) }
                factory { mockk<NavigateToErrorUseCase>(relaxed = true) }
                factory { mockk<ObserveProposalUseCase>(relaxed = true) }
                factory { mockk<ProcessSwapTransactionUseCase>(relaxed = true) }
                factory { mockk<PrefillSendUseCase>(relaxed = true) }

                single<SwapRepository> { mockk(relaxed = true) }

                single<AccountDataSource> { mockk(relaxed = true) }
                single<ProposalDataSource> { mockk(relaxed = true) }

                single<SynchronizerProvider> { mockk(relaxed = true) }
                single<LedgerAccountBindingProvider> { mockk(relaxed = true) }

                factory { LedgerOpenAppArgs() }
            }

        koin =
            koinApplication {
                modules(featureLedgerModule, stubsModule)
            }
    }

    @AfterTest
    fun tearDown() {
        koin.close()
        Dispatchers.resetMain()
    }

    @Test
    fun ledgerConnectVM_resolvesFromKoin() {
        koin.koin.get<LedgerConnectVM>()
    }

    @Test
    fun ledgerTurnOnVM_resolvesFromKoin() {
        koin.koin.get<LedgerTurnOnVM>()
    }

    @Test
    fun ledgerDeviceScanVM_resolvesFromKoin() {
        koin.koin.get<LedgerDeviceScanVM>()
    }

    @Test
    fun ledgerOpenAppVM_resolvesFromKoin() {
        koin.koin.get<LedgerOpenAppVM>()
    }

    @Test
    fun ledgerHandshakeVM_resolvesFromKoin() {
        koin.koin.get<LedgerHandshakeVM>()
    }

    @Test
    fun ledgerConnectedVM_resolvesFromKoin() {
        koin.koin.get<LedgerConnectedVM>()
    }

    @Test
    fun ledgerSignVM_resolvesFromKoin() {
        koin.koin.get<LedgerSignVM>()
    }

    @Test
    fun contracts_resolveFromKoin() {
        koin.koin.get<LedgerNavContributor>()
        koin.koin.get<LedgerNavigator>()
        koin.koin.get<LedgerAccountImporter>()
    }

    @Test
    fun proposalPipeline_isTheSameInstanceAsTheProposalRepository() {
        assertSame<Any>(koin.koin.get<LedgerProposalRepository>(), koin.koin.get<LedgerProposalPipeline>())
    }
}
