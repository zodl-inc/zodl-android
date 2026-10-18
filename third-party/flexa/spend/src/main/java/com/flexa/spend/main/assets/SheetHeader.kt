package com.flexa.spend.main.assets

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun AssetsSheetHeader(
    title: String,
    toSettings: () -> Unit = {}
) {
    val palette = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            modifier = Modifier.offset(x = 10.dp),
            text = title,
            style = TextStyle(
                color = palette.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        )
        if (false)
            IconButton(
                onClick = { toSettings() }) {
                Icon(
                    modifier = Modifier.size(21.dp),
                    imageVector = Icons.Outlined.Settings,
                    tint = palette.onSurfaceVariant,
                    contentDescription = null
                )
            }
    }
}

@Composable
fun AssetsSettingsSheetHeader(
    toBack: () -> Unit = {}
) {
    val palette = MaterialTheme.colorScheme
    BackHandler { toBack() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = { toBack() }) {
            Icon(
                modifier = Modifier.size(21.dp),
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                tint = palette.onSurfaceVariant,
                contentDescription = null
            )
        }
        Text(
            modifier = Modifier
                .padding(start = 14.dp)
                .weight(1F, true),
            text = stringResource(id = R.string.settings),
            style = TextStyle(
                color = palette.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.W400
            )
        )
    }
}

@Composable
fun AssetInfoSheetHeader(
    modifier: Modifier = Modifier,
    asset: AvailableAsset,
    toSettings: () -> Unit = {},
    toBack: () -> Unit = {},
    backNavigation: Boolean = false
) {
    val palette = MaterialTheme.colorScheme
    BackHandler(backNavigation) { toBack() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (backNavigation) {
            IconButton(
                onClick = { toBack() }) {
                Icon(
                    modifier = Modifier.size(21.dp),
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    tint = palette.onSurfaceVariant,
                    contentDescription = null
                )
            }
        }
        Text(
            modifier = Modifier
                .padding(start = 14.dp)
                .weight(1F, true),
            text = asset.assetData?.displayName ?: "",
            style = TextStyle(
                color = palette.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        )
        if (false)
            if (backNavigation) {
                IconButton(
                    onClick = { toSettings() }) {
                    Icon(
                        modifier = Modifier.size(21.dp),
                        imageVector = Icons.Outlined.Settings,
                        tint = palette.onSurfaceVariant,
                        contentDescription = null
                    )
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsSheetStickyHeader(
    modifier: Modifier = Modifier,
    text: String,
    backgroundColor: Color = BottomSheetDefaults.ContainerColor
) {
    val palette = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            modifier = Modifier.offset(x = 6.dp),
            text = text,
            style = TextStyle(
                color = palette.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.W500
            )
        )
    }
}

@Composable
fun HideShortBalances(
    modifier: Modifier = Modifier,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    val palette = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(30.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.hide_short_balances),
                style = TextStyle(
                    fontSize = 17.sp, fontWeight = FontWeight.W500,
                    color = palette.onBackground
                )
            )
            Switch(
                colors = SwitchDefaults.colors(),
                checked = checked, onCheckedChange = {
                    onChecked.invoke(it)
                },
                thumbContent = {}
            )
        }
        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Preview
@Composable
private fun AssetsSheetStickyHeaderPreview() {
    FlexaTheme {
        AssetsSheetStickyHeader(
            text = "Flexa wallet",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AssetsSheetFooterPreview() {
    FlexaTheme {
        HideShortBalances(
            modifier = Modifier.fillMaxWidth(),
            checked = false,
            onChecked = {

            }
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun AssetsSheetHeaderPreview() {
    FlexaTheme {
        AssetsSheetHeader(title = stringResource(id = R.string.pay_using))
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun AssetsSettingsSheetHeaderPreview() {
    FlexaTheme {
        AssetsSettingsSheetHeader()
    }
}

@Preview
@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
private fun AssetInfoSheetHeaderPreview() {
    FlexaTheme {
        AssetInfoSheetHeader(
            backNavigation = true,
            asset = MockFactory.getMockSelectedAsset().asset
        )
    }
}
