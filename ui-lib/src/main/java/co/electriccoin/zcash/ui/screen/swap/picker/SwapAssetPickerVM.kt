package co.electriccoin.zcash.ui.screen.swap.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.CompositeSwapAsset
import co.electriccoin.zcash.ui.common.model.SwapAsset
import co.electriccoin.zcash.ui.common.model.SwapProvider
import co.electriccoin.zcash.ui.common.model.assetFor
import co.electriccoin.zcash.ui.common.model.near.NearSwapAsset
import co.electriccoin.zcash.ui.common.repository.MetadataRepository
import co.electriccoin.zcash.ui.common.repository.SwapAssetsData
import co.electriccoin.zcash.ui.common.repository.SwapRepository
import co.electriccoin.zcash.ui.common.usecase.FilterSwapAssetsUseCase
import co.electriccoin.zcash.ui.common.usecase.GetSwapAssetsUseCase
import co.electriccoin.zcash.ui.common.usecase.NavigateToSwapAssetPickerUseCase
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.component.listitem.ListItemState
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SwapAssetPickerVM(
    private val args: SwapAssetPickerArgs,
    getSwapAssets: GetSwapAssetsUseCase,
    metadataRepository: MetadataRepository,
    private val navigateToSwapAssetPicker: NavigateToSwapAssetPickerUseCase,
    private val filterSwapAssets: FilterSwapAssetsUseCase,
    private val swapRepository: SwapRepository,
) : ViewModel() {
    private val searchText = MutableStateFlow("")

    private val searchTextFieldState = searchText.map { createTextFieldState(it) }

    // MOB-1396: the Pay flow opens the picker with nearOnly=true → expose only NEAR sub-assets.
    private val swapAssets =
        getSwapAssets.observe().map { if (args.nearOnly) it.toNearOnly() else it }

    private val filteredSwapAssets =
        combine(
            swapAssets,
            metadataRepository.observeLastUsedAssetHistory(),
            searchText
        ) { assets, latestUsedAssets, text ->
            filterSwapAssets(
                assets = assets,
                latestUsedAssets = latestUsedAssets,
                text = text,
                onlyChainTicker = args.onlyChainTicker
            )
        }

    val state: StateFlow<SwapAssetPickerState?> =
        combine(filteredSwapAssets, searchTextFieldState) { assets, search ->
            createState(assets, search)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            initialValue = null
        )

    private fun createState(assets: SwapAssetsData, search: TextFieldState): SwapAssetPickerState =
        SwapAssetPickerState(
            data =
                when {
                    assets.data != null -> {
                        SwapAssetPickerDataState.Success(
                            assets.data.map {
                                ListItemState(
                                    bigIcon = it.tokenIcon,
                                    smallIcon = it.chainIcon,
                                    title = stringRes(it.tokenTicker),
                                    subtitle = it.chainName,
                                    onClick = { onSwapAssetClick(it) },
                                    contentType = "token",
                                    key = "${it.chainTicker}_${it.tokenTicker}_${it.assetId}"
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
                                onClick = ::onRetryClick
                            )
                        )
                    }
                },
            onBack = ::onBack,
            search = search,
            title = stringRes(R.string.swap_select_token)
        )

    private fun createTextFieldState(it: String): TextFieldState =
        TextFieldState(
            value = stringRes(it),
            onValueChange = ::onSearchTextChange,
        )

    private fun onSearchTextChange(new: String) = searchText.update { new }

    private fun onSwapAssetClick(asset: SwapAsset) {
        viewModelScope.launch { navigateToSwapAssetPicker.onSelected(asset, args) }
    }

    private fun onBack() {
        viewModelScope.launch { navigateToSwapAssetPicker.onSelectionCancelled(args) }
    }

    private fun onRetryClick() = swapRepository.requestRefreshAssets()

    private fun SwapAssetsData.toNearOnly(): SwapAssetsData =
        copy(
            data = data?.mapNotNull { it.nearSubAsset() },
            zecAsset = zecAsset?.nearSubAsset()
        )

    private fun SwapAsset.nearSubAsset(): SwapAsset? =
        when (this) {
            is CompositeSwapAsset -> assetFor(SwapProvider.NEAR)
            is NearSwapAsset -> this
            else -> null
        }
}
