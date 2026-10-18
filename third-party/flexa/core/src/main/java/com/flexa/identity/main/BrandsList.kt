package com.flexa.identity.main

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flexa.core.theme.FlexaTheme
import com.flexa.core.view.FlexaLogo

enum class ItemsType {
    TOP, BOTTOM
}

internal class BrandsIconsBundle(val items: List<String>, val type: ItemsType)

@Composable
internal fun MerchantsIconsList(
    modifier: Modifier = Modifier,
    bundle: BrandsIconsBundle
) {
    val previewMode = LocalInspectionMode.current
    val modifierProvider = remember {
        when (bundle.type) {
            ItemsType.TOP -> TopModifierProvider()
            ItemsType.BOTTOM -> BottomModifierProvider()
        }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(bundle.items) {
        if (bundle.type == ItemsType.TOP) {
            listState.scrollToItem(0)
            listState.scrollBy(130.dp.value)
        }
    }

    LazyRow(
        modifier = modifier
            .height(92.dp)
            .alpha(.8F),
        state = listState,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        userScrollEnabled = false,
    ) {
        var j = 0
        if (!previewMode) {
            for (i in bundle.items.indices) {
                item {
                    if (j > 4) j = 0
                    AsyncImage(
                        modifier = modifierProvider.getModifier(j),
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(bundle.items[i])
                            .crossfade(true)
//                            .crossfade(1000)
                            .build(),
                        contentDescription = null,
                        error = painterResource(id = com.flexa.R.drawable.ic_flexa)
                    )
                    ++j
                }
            }
        } else {
            for (i in 0..50) {
                item {
                    if (j > 4) j = 0
                    FlexaLogo(
                        modifier = modifierProvider.getModifier(j)
                    )
                    ++j
                }
            }

        }
    }
}

internal interface ModifierProvider {
    fun getModifier(index: Int): Modifier
}

private class TopModifierProvider : ModifierProvider {
    override fun getModifier(index: Int): Modifier =
        when (index) {
            0 -> Modifier
                .padding(start = 10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color = Color.White)
                .size(72.dp)

            1 -> Modifier
                .padding(start = 26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color = Color.White)
                .size(50.dp)
                .blur(3.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)

            2 -> Modifier
                .padding(start = 28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color = Color.White)
                .size(64.dp)
                .blur(2.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)

            3 -> Modifier
                .padding(start = 30.dp, end =20.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color = Color.White)
                .size(72.dp)

            else -> Modifier
                .padding(end =20.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color = Color.White)
                .size(50.dp)
                .blur(4.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
        }
}

private class BottomModifierProvider : ModifierProvider {
    override fun getModifier(index: Int): Modifier =
        when (index) {
            0 -> Modifier
                .padding(start = 20.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color = Color.White)
                .size(44.dp)
                .blur(3.dp, BlurredEdgeTreatment.Unbounded)

            1 -> Modifier
                .padding(start = 26.dp, end = 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color = Color.White)
                .size(54.dp)

            2 -> Modifier
                .padding(start = 46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color = Color.White)
                .size(56.dp)
                .blur(2.dp, BlurredEdgeTreatment.Unbounded)

            3 -> Modifier
                .padding(start = 36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color = Color.White)
                .size(56.dp)

            else -> Modifier
                .padding(start = 30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color = Color.White)
                .size(62.dp)
        }
}

@Preview
@Composable
private fun MerchantsIconsListPreview() {
    FlexaTheme {
        Column {
            MerchantsIconsList(
                bundle = BrandsIconsBundle(
                    listOf(
                        "https://flexa.network/static/4bbb1733b3ef41240ca0f0675502c4f7/d8419/flexa-logo%403x.png",
                        "https://flexa.network/static/4bbb1733b3ef41240ca0f0675502c4f7/d8419/flexa-logo%403x.png",
                        "https://flexa.network/static/4bbb1733b3ef41240ca0f0675502c4f7/d8419/flexa-logo%403x.png",
                        "https://flexa.network/static/4bbb1733b3ef41240ca0f0675502c4f7/d8419/flexa-logo%403x.png",
                        "https://flexa.network/static/4bbb1733b3ef41240ca0f0675502c4f7/d8419/flexa-logo%403x.png",
                    ),
                    ItemsType.TOP
                )
            )
            MerchantsIconsList(
                bundle = BrandsIconsBundle(
                    listOf(
                        "https://flexa.network/static/4bbb1733b3ef41240ca0f0675502c4f7/d8419/flexa-logo%403x.png",
                        "https://flexa.network/static/4bbb1733b3ef41240ca0f0675502c4f7/d8419/flexa-logo%403x.png",
                        "https://flexa.network/static/4bbb1733b3ef41240ca0f0675502c4f7/d8419/flexa-logo%403x.png",
                        "https://flexa.network/static/4bbb1733b3ef41240ca0f0675502c4f7/d8419/flexa-logo%403x.png",
                        "https://flexa.network/static/4bbb1733b3ef41240ca0f0675502c4f7/d8419/flexa-logo%403x.png",
                    ),
                    ItemsType.BOTTOM
                )
            )
        }
    }
}
