package com.flexa.spend.main.main_screen

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flexa.core.shared.Brand
import com.flexa.spend.MockFactory
import com.flexa.spend.R
import com.flexa.spend.main.ui_utils.SpendAsyncImage
import com.flexa.spend.merchants.BrandsViewModel

@Composable
internal fun BrandsRow(
    modifier: Modifier = Modifier,
    viewModel: BrandsViewModel,
    toEdit: () -> Unit,
    onClick: (Brand) -> Unit = {}
) {
    val horizontalPadding: Dp = 18.dp
    viewModel.addMerchantId.collectAsStateWithLifecycle()
    val brands by remember { derivedStateOf { viewModel.itemsA + viewModel.itemsB } }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = horizontalPadding + 8.dp, end = horizontalPadding,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.more_instant_payments),
                fontWeight = FontWeight.W600,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = { toEdit.invoke() }) {
                Icon(
                    modifier = Modifier.size(22.dp),
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        AnimatedVisibility(visible = brands.isNotEmpty()) {
            LazyRow {
                brands.forEachIndexed { index, item ->
                    item {
                        if (index == 0) Spacer(modifier = Modifier.width(horizontalPadding))
                        BrandRowItem(
                            Modifier.height(114.dp),
                            item.brand,
                            onClick = { onClick.invoke(it) }
                        )
                        if (index == brands.size - 1) Spacer(
                            modifier = Modifier.width(
                                horizontalPadding
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BrandRowItem(
    modifier: Modifier = Modifier,
    item: Brand,
    onClick: (Brand) -> Unit = {}
) {
    TextButton(
        modifier = modifier,
        onClick = { onClick.invoke(item) }) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            SpendAsyncImage(
                modifier = Modifier
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .size(54.dp),
                imageUrl = item.logoUrl,
                crossfadeDuration = 500,
            )
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .weight(1f)
            ) {
                Text(
                    modifier = Modifier.align(Center),
                    text = item.name ?: "",
                    maxLines = 2,
                    style = TextStyle(
                        fontWeight = FontWeight.W600,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline
                    ),
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview
@Composable
fun SpendDragHandler(
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val width by remember { mutableIntStateOf(32) }
    val height by remember { mutableIntStateOf(4) }
    Canvas(modifier = Modifier
        .padding(vertical = 10.dp)
        .width(width.dp)
        .height(height.dp),
        onDraw = {
            drawLine(
                start = Offset(x = 0f, y = (height.dp / 2).toPx()),
                end = Offset(x = width.dp.toPx(), y = (height.dp / 2).toPx()),
                color = color,
                strokeWidth = height.dp.toPx(),
                cap = StrokeCap.Round, //add this line for rounded edges
            )
        })
}


@Preview
@Preview(
    name = "dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
private fun MerchantItemPreview() {
    BrandRowItem(
        modifier = Modifier.height(114.dp),
        item = MockFactory.getBrand(),
        onClick = { }
    )
}

