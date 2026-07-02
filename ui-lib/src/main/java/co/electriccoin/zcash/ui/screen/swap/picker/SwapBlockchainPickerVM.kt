package co.electriccoin.zcash.ui.screen.swap.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SwapBlockchain
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.usecase.EnsureSwapAssetsLoadedUseCase
import co.electriccoin.zcash.ui.common.usecase.FilterSwapBlockchainsUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSwapAssetsUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectSwapBlockchainUseCase
import co.electriccoin.zcash.ui.common.usecase.SwapBlockchainData
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.component.listitem.ListItemState
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SwapBlockchainPickerVM(
    getSwapAssets: GetSwapAssetsUseCase,
    private val args: SwapBlockchainPickerArgs,
    private val navigateToSelectSwapBlockchain: NavigateToSelectSwapBlockchainUseCase,
    private val filterSwapBlockchains: FilterSwapBlockchainsUseCase,
    private val swapRepository: SwapRepository,
    ensureSwapAssetsLoaded: EnsureSwapAssetsLoadedUseCase,
) : ViewModel() {
    init {
        // Ensure assets are loaded even if user hasn't visited swap screen before
        viewModelScope.launch {
            ensureSwapAssetsLoaded()
        }
    }

    private val searchText = MutableStateFlow("")

    private val searchTextFieldState =
        searchText
            .map {
                createTextFieldState(it)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = createTextFieldState(searchText.value)
            )

    private val filteredSwapBlockchains =
        combine(getSwapAssets.observe(), searchText) { assets, text ->
            filterSwapBlockchains(assets, text)
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue =
                    filterSwapBlockchains(
                        assets = getSwapAssets.get(),
                        text = searchText.value
                    )
            )

    val state: StateFlow<SwapAssetPickerState> =
        combine(filteredSwapBlockchains, searchTextFieldState) { assets, search ->
            createState(assets, search)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = createState(filteredSwapBlockchains.value, searchTextFieldState.value)
        )

    private fun createState(assets: SwapBlockchainData, search: TextFieldState): SwapAssetPickerState =
        SwapAssetPickerState(
            data =
                when {
                    assets.data != null -> {
                        SwapAssetPickerDataState.Success(
                            assets.data.map {
                                ListItemState(
                                    bigIcon = it.chainIcon,
                                    smallIcon = null,
                                    title = it.chainName,
                                    subtitle = null,
                                    onClick = { onBlockchainClick(it) },
                                    contentType = "blockchain",
                                    key = it.chainTicker
                                )
                            }
                        )
                    }

                    assets.isLoading -> {
                        SwapAssetPickerDataState.Loading
                    }

                    else -> {
                        SwapAssetPickerDataState.Error(
                            stringRes(co.electriccoin.zcash.ui.design.R.string.coinVote_error_title),
                            stringRes(co.electriccoin.zcash.ui.design.R.string.swapAndPay_failure_wrongDesc),
                            ButtonState(
                                text = stringRes(co.electriccoin.zcash.ui.design.R.string.disconnectHWWallet_tryAgain),
                                onClick = ::onRetry
                            )
                        )
                    }
                },
            onBack = ::onBack,
            search = search,
            title = stringRes(R.string.swapAndPay_addressBookSelectChain)
        )

    private fun createTextFieldState(it: String): TextFieldState =
        TextFieldState(
            value = stringRes(it),
            onValueChange = ::onSearchTextChange,
        )

    private fun onSearchTextChange(new: String) = searchText.update { new }

    private fun onBlockchainClick(asset: SwapBlockchain) =
        viewModelScope.launch { navigateToSelectSwapBlockchain.onSelected(asset, args) }

    private fun onBack() = viewModelScope.launch { navigateToSelectSwapBlockchain.onSelectionCancelled(args) }

    private fun onRetry() = swapRepository.requestRefreshAssets()
}
