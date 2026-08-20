package co.electriccoin.zcash.di

/*
 * Koin graph smoke-test for all voting ViewModels.
 *
 * WHY THIS TEST EXISTS
 * --------------------
 * Koin's [viewModelOf] resolves every constructor parameter by type at VM-factory-call time
 * (inside the Android ViewModel infrastructure, not at app startup). A constructor param whose
 * type is not registered anywhere in the Koin graph causes a
 * [org.koin.core.error.NoDefinitionFoundException] when the screen opens — NOT when the app
 * starts or compiles. VM tests that construct the VM directly with mock arguments bypass Koin
 * entirely and therefore cannot catch this class of bug. See
 * MigrationKoinGraphSmokeTest (feature-migration) for the sibling test and the bug that
 * originally motivated this pattern.
 *
 * HOW IT WORKS
 * ------------
 * Each test builds a [koinApplication] containing ONLY the [featureVotingModule] (the module
 * under test) plus a [stubsModule] that provides mockk stubs for every type the voting VMs (and
 * the real repositories/use cases/providers featureVotingModule wires them through) need but
 * does not itself provide — i.e. types legitimately owned by other modules (ui-lib's
 * ProviderModule/RepositoryModule, the app's Context, etc.). It then resolves each voting VM via
 * [org.koin.core.Koin.get], which triggers the same reflective constructor-argument resolution
 * that [viewModelOf] uses at runtime. A missing type fails here instead of on-device.
 *
 * A type that featureVotingModule is itself supposed to provide (i.e. its interface AND
 * implementation both live in feature-voting) is deliberately NOT stubbed here — stubbing it
 * would hide a real missing-registration bug in featureVotingModule instead of catching it.
 */

import android.content.Context
import co.electriccoin.zcash.preference.EncryptedPreferenceProvider
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.datasource.AccountDataSource
import co.electriccoin.zcash.ui.common.provider.HasSeenHowToVoteKeystoneStorageProvider
import co.electriccoin.zcash.ui.common.provider.HasSeenHowToVoteStorageProvider
import co.electriccoin.zcash.ui.common.provider.HttpClientProvider
import co.electriccoin.zcash.ui.common.provider.KeystoneSDKProvider
import co.electriccoin.zcash.ui.common.provider.SynchronizerProvider
import co.electriccoin.zcash.ui.common.repository.BiometricRepository
import co.electriccoin.zcash.ui.common.repository.ConfigurationRepository
import co.electriccoin.zcash.ui.common.usecase.CopyToClipboardUseCase
import co.electriccoin.zcash.ui.common.usecase.ErrorMapperUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.common.usecase.GetWalletSeedBytesUseCase
import co.electriccoin.zcash.ui.common.usecase.ObserveSelectedWalletAccountUseCase
import co.electriccoin.zcash.ui.screen.voting.chainconfig.VoteChainConfigVM
import co.electriccoin.zcash.ui.screen.voting.coinholderpolling.VoteCoinholderPollingVM
import co.electriccoin.zcash.ui.screen.voting.confirmsubmission.VoteConfirmSubmissionArgs
import co.electriccoin.zcash.ui.screen.voting.confirmsubmission.VoteConfirmSubmissionVM
import co.electriccoin.zcash.ui.screen.voting.howtovote.VoteHowToVoteVM
import co.electriccoin.zcash.ui.screen.voting.proposaldetail.VoteProposalDetailArgs
import co.electriccoin.zcash.ui.screen.voting.proposaldetail.VoteProposalDetailVM
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListArgs
import co.electriccoin.zcash.ui.screen.voting.proposallist.VoteProposalListVM
import co.electriccoin.zcash.ui.screen.voting.results.VoteResultsArgs
import co.electriccoin.zcash.ui.screen.voting.results.VoteResultsVM
import co.electriccoin.zcash.ui.screen.voting.scankeystone.ScanKeystoneVotingPCZTRequest
import co.electriccoin.zcash.ui.screen.voting.scankeystone.viewmodel.ScanKeystoneVotingPCZTViewModel
import co.electriccoin.zcash.ui.screen.voting.signkeystone.SignKeystoneVotingArgs
import co.electriccoin.zcash.ui.screen.voting.signkeystone.SignKeystoneVotingVM
import co.electriccoin.zcash.ui.screen.voting.tallying.VoteTallyingArgs
import co.electriccoin.zcash.ui.screen.voting.tallying.VoteTallyingVM
import co.electriccoin.zcash.voting.di.featureVotingModule
import io.mockk.mockk
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Smoke-tests that every voting ViewModel can be resolved through the Koin [featureVotingModule]
 * without a [org.koin.core.error.NoDefinitionFoundException]. Covers (in [featureVotingModule]
 * registration order):
 *
 *   VoteCoinholderPollingVM, VoteChainConfigVM, VoteHowToVoteVM, VoteProposalListVM,
 *   VoteProposalDetailVM, VoteConfirmSubmissionVM, VoteTallyingVM, VoteResultsVM,
 *   SignKeystoneVotingVM, ScanKeystoneVotingPCZTViewModel
 */
class VotingKoinGraphSmokeTest {
    private lateinit var koin: KoinApplication

    @BeforeTest
    fun setUp() {
        // The stubs module provides mockk instances for every type the voting VMs (and the real
        // featureVotingModule repositories/use cases/providers they resolve through) inject but
        // that is not itself registered by featureVotingModule. Concrete arg data-classes
        // (SignKeystoneVotingArgs, etc.) are provided as real instances since data classes
        // cannot be created lazily by Koin without additional factory setup.
        val stubsModule =
            module {
                // Framework
                single<Context> { mockk(relaxed = true) }

                // NavigationRouter
                single<NavigationRouter> { mockk(relaxed = true) }

                // UseCases (owned by ui-lib, not feature-voting)
                factory { mockk<ErrorMapperUseCase>(relaxed = true) }
                factory { mockk<GetSelectedWalletAccountUseCase>(relaxed = true) }
                factory { mockk<ObserveSelectedWalletAccountUseCase>(relaxed = true) }
                factory { mockk<CopyToClipboardUseCase>(relaxed = true) }
                factory { mockk<GetWalletSeedBytesUseCase>(relaxed = true) }

                // Repositories (owned by ui-lib, not feature-voting)
                single<ConfigurationRepository> { mockk(relaxed = true) }
                single<BiometricRepository> { mockk(relaxed = true) }

                // Providers (owned by ui-lib, not feature-voting)
                single<HttpClientProvider> { mockk(relaxed = true) }
                single<SynchronizerProvider> { mockk(relaxed = true) }
                single<KeystoneSDKProvider> { mockk(relaxed = true) }
                single<HasSeenHowToVoteStorageProvider> { mockk(relaxed = true) }
                single<HasSeenHowToVoteKeystoneStorageProvider> { mockk(relaxed = true) }

                // DataSources (owned by ui-lib, not feature-voting)
                single<AccountDataSource> { mockk(relaxed = true) }

                // Concrete preference class that featureVotingModule's own providers/repositories
                // (VotingHotkeySeedProviderImpl, VotingConfigRepositoryImpl,
                // VotingChainConfigRepositoryImpl, VotingRecoveryRepositoryImpl) inject directly.
                single<EncryptedPreferenceProvider> { mockk(relaxed = true) }

                // Args data-classes (carried as constructor params for VMs that accept navigation
                // args). Providing a canonical instance is the simplest way to satisfy the Koin
                // type lookup.
                factory { SignKeystoneVotingArgs(roundIdHex = "round-1") }
                factory { VoteConfirmSubmissionArgs(roundIdHex = "round-1", choicesJson = "{}") }
                factory { VoteProposalListArgs() }
                factory { VoteProposalDetailArgs(proposalId = 1, roundId = "round-1") }
                factory { VoteResultsArgs(roundIdHex = "round-1") }
                factory { VoteTallyingArgs(roundIdHex = "round-1") }
                factory { ScanKeystoneVotingPCZTRequest(roundIdHex = "round-1", bundleIndex = 0, actionIndex = 0) }
            }

        koin =
            koinApplication {
                modules(featureVotingModule, stubsModule)
            }
    }

    @AfterTest
    fun tearDown() {
        koin.close()
    }

    // ── individual VM resolution tests ────────────────────────────────────────
    // Each test calls koin.koin.get<VM>() which exercises the same reflective
    // constructor-argument lookup that Koin's viewModelOf lambda performs at
    // runtime. A missing binding throws NoDefinitionFoundException → test fails.

    @Test
    fun voteCoinholderPollingVM_resolvesFromKoin() {
        koin.koin.get<VoteCoinholderPollingVM>()
    }

    @Test
    fun voteChainConfigVM_resolvesFromKoin() {
        koin.koin.get<VoteChainConfigVM>()
    }

    @Test
    fun voteHowToVoteVM_resolvesFromKoin() {
        koin.koin.get<VoteHowToVoteVM>()
    }

    @Test
    fun voteProposalListVM_resolvesFromKoin() {
        koin.koin.get<VoteProposalListVM>()
    }

    @Test
    fun voteProposalDetailVM_resolvesFromKoin() {
        koin.koin.get<VoteProposalDetailVM>()
    }

    @Test
    fun voteConfirmSubmissionVM_resolvesFromKoin() {
        koin.koin.get<VoteConfirmSubmissionVM>()
    }

    @Test
    fun voteTallyingVM_resolvesFromKoin() {
        koin.koin.get<VoteTallyingVM>()
    }

    @Test
    fun voteResultsVM_resolvesFromKoin() {
        koin.koin.get<VoteResultsVM>()
    }

    @Test
    fun signKeystoneVotingVM_resolvesFromKoin() {
        koin.koin.get<SignKeystoneVotingVM>()
    }

    @Test
    fun scanKeystoneVotingPCZTViewModel_resolvesFromKoin() {
        koin.koin.get<ScanKeystoneVotingPCZTViewModel>()
    }
}
