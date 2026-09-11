package co.electriccoin.zcash.ui.screen.voting.chainconfig

import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.voting.PinnedConfigSource
import co.electriccoin.zcash.ui.common.model.voting.StaticVotingConfig
import co.electriccoin.zcash.ui.common.provider.VotingApiProvider
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigRepository
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigSelection
import co.electriccoin.zcash.ui.common.repository.VotingChainConfigState
import co.electriccoin.zcash.ui.common.repository.VotingCustomChainConfig
import co.electriccoin.zcash.ui.common.usecase.CopyToClipboardUseCase
import co.electriccoin.zcash.ui.design.util.stringRes
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers MOB-1809's default-detection fix: [isBundledDefaultUrl] directly (a bundled mirror URL
 * with a matching checksum counts as the default; the same URL re-pinned with a different
 * checksum must stay custom), plus the two VM flows that observably depend on it —
 * [VoteChainConfigVM]'s duplicate-of-default save error and its default-collapse on selecting an
 * existing custom chain that already matches a bundled source.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoteChainConfigVMTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region isBundledDefaultUrl

    @Test
    fun bundledMirrorUrlWithMatchingChecksumIsBundledDefault() {
        val source = PinnedConfigSource.parse(StaticVotingConfig.BUNDLED_PINNED_SOURCE_MIRROR)

        assertTrue(source.isBundledDefaultUrl())
    }

    @Test
    fun bundledUrlWithDifferentChecksumIsNotBundledDefault() {
        val source = PinnedConfigSource.parse(rePinnedBundledMirrorUrl())

        assertFalse(source.isBundledDefaultUrl())
    }

    // endregion

    // region VM: selecting an existing custom chain that already matches a bundled source

    @Test
    fun selectingCustomChainMatchingBundledMirrorCollapsesToDefault() =
        runTest {
            val repository =
                FakeVotingChainConfigRepository(
                    VotingChainConfigState(
                        selected = VotingChainConfigSelection.Default,
                        customChains =
                            listOf(
                                VotingCustomChainConfig(
                                    id = CUSTOM_CHAIN_ID,
                                    name = "Bundled mirror copy",
                                    pinnedSource = StaticVotingConfig.BUNDLED_PINNED_SOURCE_MIRROR
                                )
                            )
                    )
                )
            val vm = vm(repository = repository)
            val collectJob = launch { vm.state.collect {} }
            advanceUntilIdle()

            val customItem =
                vm.state.value
                    ?.chains
                    ?.first { item -> item.id == CUSTOM_CHAIN_ID }
            customItem?.radioButtonState?.onClick?.invoke()
            advanceUntilIdle()

            assertEquals(1, repository.selectDefaultCalls)
            assertTrue(repository.selectCustomCalls.isEmpty())
            collectJob.cancel()
        }

    @Test
    fun selectingCustomChainWithRePinnedBundledUrlStaysCustom() =
        runTest {
            val repository =
                FakeVotingChainConfigRepository(
                    VotingChainConfigState(
                        selected = VotingChainConfigSelection.Default,
                        customChains =
                            listOf(
                                VotingCustomChainConfig(
                                    id = CUSTOM_CHAIN_ID,
                                    name = "Re-pinned mirror",
                                    pinnedSource = rePinnedBundledMirrorUrl()
                                )
                            )
                    )
                )
            val vm = vm(repository = repository)
            val collectJob = launch { vm.state.collect {} }
            advanceUntilIdle()

            val customItem =
                vm.state.value
                    ?.chains
                    ?.first { item -> item.id == CUSTOM_CHAIN_ID }
            customItem?.radioButtonState?.onClick?.invoke()
            advanceUntilIdle()

            assertEquals(listOf(CUSTOM_CHAIN_ID), repository.selectCustomCalls)
            assertEquals(0, repository.selectDefaultCalls)
            collectJob.cancel()
        }

    // endregion

    // region VM: saving a new custom source that duplicates the bundled default

    @Test
    fun savingNewSourceMatchingBundledMirrorShowsDuplicateDefaultError() =
        runTest {
            val repository = FakeVotingChainConfigRepository(VotingChainConfigState())
            val vm = vm(repository = repository)
            val collectJob = launch { vm.state.collect {} }
            advanceUntilIdle()

            vm.state.value
                ?.onAddCustom
                ?.invoke()
            advanceUntilIdle()
            vm.state.value
                ?.editor
                ?.url
                ?.onValueChange
                ?.invoke(StaticVotingConfig.BUNDLED_PINNED_SOURCE_MIRROR)
            advanceUntilIdle()
            vm.state.value
                ?.editor
                ?.saveButton
                ?.onClick
                ?.invoke()
            advanceUntilIdle()

            assertEquals(
                stringRes(R.string.vote_chain_config_error_duplicate_default),
                vm.state.value
                    ?.errorSheet
                    ?.message
            )
            assertTrue(repository.addCustomCalls.isEmpty())
            assertEquals(0, repository.selectDefaultCalls)
            collectJob.cancel()
        }

    // endregion

    private fun vm(
        repository: VotingChainConfigRepository,
        votingApiProvider: VotingApiProvider = mockk { coEvery { validateConfigSource(any()) } just Runs },
        navigationRouter: NavigationRouter = mockk(relaxed = true),
        copyToClipboard: CopyToClipboardUseCase = mockk(relaxed = true),
    ) = VoteChainConfigVM(
        votingChainConfigRepository = repository,
        votingApiProvider = votingApiProvider,
        navigationRouter = navigationRouter,
        copyToClipboard = copyToClipboard,
    )

    private fun rePinnedBundledMirrorUrl(): String =
        StaticVotingConfig.BUNDLED_PINNED_SOURCE_MIRROR.substringBeforeLast("checksum=sha256:") +
            "checksum=sha256:" +
            DIFFERENT_CHECKSUM_HEX

    private companion object {
        const val CUSTOM_CHAIN_ID = "custom-chain"
        val DIFFERENT_CHECKSUM_HEX = "0".repeat(64)
    }
}

private class FakeVotingChainConfigRepository(
    initial: VotingChainConfigState
) : VotingChainConfigRepository {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<VotingChainConfigState> = mutableState

    var selectDefaultCalls = 0
        private set
    val selectCustomCalls = mutableListOf<String>()
    val addCustomCalls = mutableListOf<Pair<String, String>>()

    override suspend fun get(): VotingChainConfigState = mutableState.value

    override suspend fun selectDefault() {
        selectDefaultCalls += 1
        mutableState.value = mutableState.value.copy(selected = VotingChainConfigSelection.Default)
    }

    override suspend fun selectCustom(id: String) {
        selectCustomCalls += id
        mutableState.value = mutableState.value.copy(selected = VotingChainConfigSelection.Custom(id))
    }

    override suspend fun addCustom(
        name: String,
        pinnedSource: String
    ): VotingCustomChainConfig {
        addCustomCalls += name to pinnedSource
        val chain = VotingCustomChainConfig(id = "new-chain", name = name, pinnedSource = pinnedSource)
        mutableState.value = mutableState.value.copy(customChains = mutableState.value.customChains + chain)
        return chain
    }

    override suspend fun updateCustom(
        id: String,
        name: String,
        pinnedSource: String
    ) {
        mutableState.value =
            mutableState.value.copy(
                customChains =
                    mutableState.value.customChains.map { chain ->
                        if (chain.id == id) chain.copy(name = name, pinnedSource = pinnedSource) else chain
                    }
            )
    }

    override suspend fun deleteCustom(id: String) {
        mutableState.value =
            mutableState.value.copy(
                customChains = mutableState.value.customChains.filterNot { chain -> chain.id == id }
            )
    }
}
