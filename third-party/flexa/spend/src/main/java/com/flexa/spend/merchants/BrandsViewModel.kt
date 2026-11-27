package com.flexa.spend.merchants

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flexa.core.shared.Brand
import com.flexa.spend.Spend
import com.flexa.spend.domain.ISpendInteractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BrandsViewModel(
    private val interactor: ISpendInteractor = Spend.interactor
) : ViewModel() {

    private val _itemsA = mutableStateListOf<BrandListItem>()
    private val _itemsB = mutableStateListOf<BrandListItem>()
    val itemsA: List<BrandListItem> = _itemsA
    val itemsB: List<BrandListItem> = _itemsB

    private var error = false
    private val _addMerchantId = MutableStateFlow<String?>(null)
    val addMerchantId = _addMerchantId.asStateFlow()
        .onStart { initBrands()  }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val slideStates = mutableStateMapOf<BrandListItem, SlideState>()
        .apply {
            _itemsA.associateWith { SlideState.NONE }.also { putAll(it) }
        }

    init {
        listenConnection()
    }

    internal fun setAddMerchantId(id: String?) {
        _addMerchantId.value = id
    }

    internal fun reorderItem(currentIndex: Int, destinationIndex: Int) {
        viewModelScope.launch {
            val item = _itemsA[currentIndex]
            _itemsA.removeAt(currentIndex)
            _itemsA.add(destinationIndex, item)
            interactor.savePinnedBrands(_itemsA.map { it.id })
        }
    }

    internal fun pinItem(item: BrandListItem) {
        viewModelScope.launch {
            _addMerchantId.value = item.id
            val newItem = item.copy(id = item.id, isDraggable = true)
            _itemsA.add(newItem)
            _itemsB.remove(item)
            interactor.savePinnedBrands(_itemsA.map { it.id })
        }
    }

    internal fun unpinItem(item: BrandListItem) {
        viewModelScope.launch {
            _itemsA.remove(item)
            interactor.savePinnedBrands(_itemsA.map { it.id })
            val newItem = item.copy(id = item.id, isDraggable = false)
            _itemsB.add(newItem)
            _itemsB.sortBy { it.brand.name }
        }
    }

    private fun listenConnection() {
        viewModelScope.launch {
            interactor.getConnectionListener()
                ?.distinctUntilChanged()
                ?.collect {
                    if (error) initBrands()
                }
        }
    }

    private fun initBrands() {
        viewModelScope.launch {
            populateBrands(interactor.getDbBrands())
            runCatching {
                val brands = interactor.getBrands(true)
                interactor.deleteBrands()
                interactor.saveBrands(brands)
                brands
            }.onSuccess {
                error = false
                populateBrands(it)
            }.onFailure {
                error = true
            }
        }
    }

    private fun populateBrands(brands: List<Brand>) {
        viewModelScope.launch {
            val pinnedBrandsIds = interactor.getPinnedBrands()

            val (matchedBrands, unmatchedBrands) = brands.partition { it.id in pinnedBrandsIds }
            val idIndexMap = pinnedBrandsIds.withIndex().associate { it.value to it.index }

            val matchedItems = matchedBrands.map { item ->
                BrandListItem(id = item.id, isDraggable = true, brand = item)
            }.sortedBy { idIndexMap[it.id] }

            val unmatchedItems = unmatchedBrands.map { item ->
                BrandListItem(id = item.id, isDraggable = false, brand = item)
            }

            _itemsA.clear()
            _itemsA.addAll(matchedItems)
            _itemsB.clear()
            _itemsB.addAll(unmatchedItems)
        }
    }
}
