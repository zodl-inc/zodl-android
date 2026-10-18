package com.flexa.spend.merchants

import android.content.res.Configuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flexa.core.shared.Brand
import com.flexa.core.theme.FlexaTheme
import com.flexa.spend.MockFactory
import com.flexa.spend.domain.FakeInteractor
import com.flexa.spend.dragToReorder
import com.flexa.spend.main.ui_utils.SpendAsyncImage

enum class SlideState {
    NONE,
    UP,
    DOWN
}

data class BrandListItem(
    val id: String,
    val isDraggable: Boolean = false,
    val brand: Brand,
)

@Composable
fun MerchantsEdit(
    modifier: Modifier,
    viewModel: BrandsViewModel,
    toBack: () -> Unit
) {
    viewModel.addMerchantId.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background),
    ) {
        Spacer(modifier = Modifier.systemBarsPadding())
        Row(
            modifier = Modifier
                .height(64.dp)
                .align(Alignment.Start),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { toBack.invoke() }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                modifier = Modifier.padding(start = 14.dp),
                text = "Reorder pins",
                style = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onBackground)
            )
        }
        Text(
            modifier = Modifier.padding(start = 64.dp, end = 16.dp),
            text = "Using Flexa at these brands requires an extra tap. To access your favorite brands more quickly, pin them to the beginning of the list.",
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline)
        )
        Spacer(modifier = Modifier.height(24.dp))

        val itemsA = viewModel.itemsA
        val itemsB = viewModel.itemsB
        val slideStates = viewModel.slideStates

        MerchantOrderList(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .weight(1F, true)
                .navigationBarsPadding(),
            viewModel = viewModel,
            itemsA = itemsA,
            itemsB = itemsB,
            slideStates = slideStates,
            updateSlideState = { item, slideState ->
                slideStates[item] = slideState
            },
            updateItemPosition = { currentIndex, destinationIndex ->
                viewModel.reorderItem(currentIndex, destinationIndex)
                slideStates.apply {
                    itemsA.associateWith { SlideState.NONE }.also { putAll(it) }
                }
            },
            onAdd = { item ->
                viewModel.pinItem(item)
            },
            onRemove = { item ->
                viewModel.unpinItem(item)
                slideStates.remove(item)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MerchantOrderList(
    modifier: Modifier,
    viewModel: BrandsViewModel,
    itemsA: List<BrandListItem>,
    itemsB: List<BrandListItem>,
    slideStates: Map<BrandListItem, SlideState>,
    updateSlideState: (item: BrandListItem, slideState: SlideState) -> Unit,
    updateItemPosition: (currentIndex: Int, destinationIndex: Int) -> Unit,
    onAdd: ((BrandListItem) -> Unit)? = null,
    onRemove: ((BrandListItem) -> Unit)? = null,
) {
    val scrollState = rememberLazyListState()
    LazyColumn(
        modifier = modifier,
        state = scrollState
    ) {
        if (itemsA.isNotEmpty())
            stickyHeader {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Text(
                        modifier = Modifier.padding(start = 64.dp, top = 10.dp, bottom = 10.dp),
                        text = "Pinned",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        items(itemsA.size) { index ->
            val merchant = itemsA.getOrNull(index)
            if (merchant != null) {
                key(merchant.id) {
                    val slideState = slideStates[merchant] ?: SlideState.NONE
                    MerchantOrderListItem(
                        modifier = Modifier.animateItem(),
                        viewModel = viewModel,
                        merchant = merchant,
                        slideState = slideState,
                        merchants = itemsA,
                        updateSlideState = updateSlideState,
                        updateItemPosition = updateItemPosition,
                        onAdd = {},
                        onRemove = onRemove
                    )
                }
            }
        }

        if (itemsB.isNotEmpty())
            stickyHeader {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Text(
                        modifier = Modifier.padding(start = 64.dp, top = 10.dp, bottom = 10.dp),
                        text = "All brands",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        items(itemsB.size) { index ->
            val merchant = itemsB.getOrNull(index)
            if (merchant != null) {
                key(merchant.id) {
                    MerchantOrderListItem(
                        modifier = Modifier.animateItem(),
                        viewModel = viewModel,
                        merchant = merchant,
                        slideState = SlideState.NONE,
                        merchants = itemsB,
                        updateSlideState = updateSlideState,
                        updateItemPosition = updateItemPosition,
                        onAdd = onAdd,
                        onRemove = {},
                    )
                }
            }
        }
    }
}

private const val ITEM_HEIGHT = 70

@Composable
fun MerchantOrderListItem(
    modifier: Modifier = Modifier,
    viewModel: BrandsViewModel,
    merchant: BrandListItem,
    slideState: SlideState,
    merchants: List<BrandListItem>,
    updateSlideState: (item: BrandListItem, slideState: SlideState) -> Unit,
    updateItemPosition: (currentIndex: Int, destinationIndex: Int) -> Unit,
    onAdd: ((BrandListItem) -> Unit)? = null,
    onRemove: ((BrandListItem) -> Unit)? = null,
) {
    val addMerchantId by viewModel.addMerchantId.collectAsStateWithLifecycle()
    val remove by remember { derivedStateOf { merchant.id == addMerchantId } }
    val height by animateDpAsState(
        targetValue = if (remove) 0.dp else ITEM_HEIGHT.dp,
        animationSpec = tween(100), label = "item height"
    )
    val pxValue = with(LocalDensity.current) { ITEM_HEIGHT.dp.toPx() }.toInt()
    val itemHeight by remember { mutableStateOf(pxValue) }

    val verticalTranslation by animateIntAsState(
        targetValue = when (slideState) {
            SlideState.UP -> -itemHeight
            SlideState.DOWN -> itemHeight
            else -> 0
        }, label = "verticalTranslation"
    )
    var isDragged by remember { mutableStateOf(false) }
    var isPlaced by remember { mutableStateOf(false) }

    val zIndex = if (isDragged) 1.0f else 0.0f
    val alpha = if (isDragged) .8f else 1.0f

    val currentIndex = remember { mutableStateOf(0) }
    val destinationIndex = remember { mutableStateOf(0) }

    val interactionSource = remember { MutableInteractionSource() }
    val isDraggable by interactionSource.collectIsDraggedAsState()

    LaunchedEffect(isPlaced) {
        if (isPlaced) {
            if (currentIndex.value != destinationIndex.value) {
                updateItemPosition(currentIndex.value, destinationIndex.value)
            }
            isPlaced = false
        }
    }
    LaunchedEffect(merchant.id) {
        viewModel.setAddMerchantId(null)
    }

    val m = if (merchant.isDraggable) {
        modifier
            .height(height)
            .dragToReorder(
                item = merchant,
                items = merchants,
                itemHeight = itemHeight,
                updateSlideState = updateSlideState,
                isDraggable = isDraggable,
                onStartDrag = { isDragged = true },
                onStopDrag = { cIndex, dIndex ->
                    isDragged = false
                    isPlaced = true
                    currentIndex.value = cIndex
                    destinationIndex.value = dIndex
                }
            )
            .offset { IntOffset(0, verticalTranslation) }
            .alpha(alpha)
            .zIndex(zIndex)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    } else {
        modifier
            .height(height)
            .offset { IntOffset(0, verticalTranslation) }
            .zIndex(zIndex)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    }


    Row(
        modifier = m,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row {
            if (merchant.isDraggable) {
                Spacer(modifier = Modifier.width(20.dp))
                Icon(
                    modifier = Modifier.fillMaxHeight(),
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(14.dp))
            } else {
                IconButton(
                    modifier = Modifier.padding(start = 10.dp),
                    onClick = { onAdd?.invoke(merchant) }) {
                    Icon(
                        modifier = Modifier.fillMaxHeight(),
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        val previewMode = LocalInspectionMode.current
        if (!previewMode) {
            SpendAsyncImage(
                modifier = Modifier
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { }
                    .padding(start = 2.dp)
                    .size(34.dp)
                    .clip(RoundedCornerShape(4.dp)),
                imageUrl = merchant.brand.logoUrl,
                crossfadeDuration = 1000,
            )
        } else {
            Icon(
                modifier = Modifier
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { }
                    .padding(start = 2.dp)
                    .size(34.dp)
                    .clip(RoundedCornerShape(40.dp)),
                imageVector = Icons.Rounded.AccountBox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { }
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth(.8F)
                    .align(Alignment.CenterStart)
                    .padding(end = 4.dp),
                text = merchant.brand.name ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W400,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }
        Row(
            modifier = Modifier
                .height(60.dp)
                .clickable(merchant.isDraggable) { },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (merchant.isDraggable) {
                VerticalDivider(
                    modifier = Modifier
                        .clickable { }
                        .height(26.dp)
                        .width(1.dp))
                IconButton(
                    modifier = Modifier.padding(end = 8.dp),
                    onClick = {
                        viewModel.setAddMerchantId(merchant.id)
                        onRemove?.invoke(merchant)
                    }) {
                    Icon(
                        modifier = Modifier.padding(),
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(50.dp))
            }
        }
    }
}

@Composable
@Preview
@Preview(
    device = "spec:parent=pixel_5,orientation=portrait",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
fun MerchantOrderListItemDraggablePreview() {
    val item = BrandListItem(
        "", true, MockFactory.getBrand()
    )
    FlexaTheme {
        MerchantOrderListItem(
            modifier = Modifier,
            viewModel(initializer = {
                BrandsViewModel(
                    interactor = FakeInteractor()
                )
            }),
            item,
            SlideState.NONE,
            arrayListOf(item, item, item),
            { _, _ -> },
            { _, _ -> },
        )
    }
}

@Composable
@Preview(device = "spec:parent=pixel_5,orientation=portrait")
@Preview(
    device = "spec:parent=pixel_5,orientation=portrait",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
fun MerchantOrderListItemPreview() {
    val item = BrandListItem(
        "", false, MockFactory.getBrand()
    )
    FlexaTheme {
        MerchantOrderListItem(
            modifier = Modifier,
            viewModel(initializer = {
                BrandsViewModel(
                    interactor = FakeInteractor()
                )
            }),
            item,
            SlideState.NONE,
            arrayListOf(item, item, item),
            { _, _ -> },
            { _, _ -> },
        )
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
fun MerchantListEditPreview() {
    FlexaTheme {
        MerchantsEdit(
            modifier = Modifier,
            viewModel = viewModel(initializer = {
                BrandsViewModel(
                    interactor = FakeInteractor()
                )
            }),
            toBack = {}
        )
    }
}
