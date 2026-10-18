package com.flexa.spend.main.assets

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flexa.core.entity.AvailableAsset
import com.flexa.core.theme.FlexaTheme
import com.flexa.spend.MockFactory
import com.flexa.spend.R
import com.flexa.spend.logo
import com.flexa.spend.main.ui_utils.SpendAsyncImage
import java.math.BigDecimal

@Composable
fun AssetItemCompose(
    modifier: Modifier = Modifier,
    asset: AvailableAsset,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
    toDetails: (AvailableAsset) -> Unit
) {
    val palette = MaterialTheme.colorScheme

    val icon by remember { mutableStateOf(asset.logo()) }

    val assetName by remember {
        mutableStateOf(
            asset.assetData?.displayName ?: ""
        )
    }

    ListItem(
        modifier = modifier,
        colors = ListItemDefaults.colors(
            containerColor = color
        ),
        leadingContent = {
            if (icon == null) {
                Canvas(
                    modifier = Modifier.size(38.dp),
                    onDraw = { drawCircle(palette.tertiary.copy(.5F)) })
            } else {
                SpendAsyncImage(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape),
                    imageUrl = icon,
                )
            }
        },
        headlineContent = {
            Text(
                text = assetName,
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W400,
                    color = palette.onBackground
                )
            )
        },
        supportingContent = {
            AnimatedContent(
                targetState = asset.balanceBundle?.total ?:BigDecimal.ZERO,
                transitionSpec = {
                    if (targetState < initialState) {
                        (slideInHorizontally { width -> width } +
                                fadeIn()).togetherWith(
                            slideOutHorizontally()
                        { width -> -width } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> -width } +
                                fadeIn()).togetherWith(
                            slideOutHorizontally()
                        { width -> width } + fadeOut())
                    }.using(SizeTransform(clip = false))
                }, label = ""
            ) { state ->
                state
                Text(
                    modifier = Modifier.animateContentSize(),
                    text = asset.balanceBundle?.totalLabel ?: stringResource(R.string.updating),
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W400,
                        color = palette.onSurfaceVariant
                    )
                )
            }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                VerticalDivider(
                    modifier = Modifier
                        .offset(x = 4.dp)
                        .height(25.dp)
                        .width(1.dp),
                    color = palette.outline
                )
                IconButton(
                    modifier = Modifier.offset(x = 10.dp),
                    onClick = { toDetails(asset) },
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        tint = palette.onSurfaceVariant,
                        contentDescription = null
                    )
                }
            }
        }
    )
}

@Preview
@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
private fun AssetItemPreview() {
    FlexaTheme {
        AssetItemCompose(
            modifier = Modifier,
            asset = MockFactory.getMockSelectedAsset().asset.copy(
                balanceBundle = MockFactory.getBalanceBundle()
            ),
            toDetails = {}
        )
    }
}
