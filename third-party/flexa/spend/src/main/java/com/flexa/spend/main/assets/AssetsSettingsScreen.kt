package com.flexa.spend.main.assets

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flexa.core.theme.FlexaTheme

@Composable
fun AssetsSettingsScreen(
    modifier: Modifier = Modifier,
    filtered: Boolean,
    toLearnMore: () -> Unit,
    onFiltered: (Boolean) -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val palette = MaterialTheme.colorScheme
        HideShortBalances(
            modifier = Modifier
                .padding(horizontal = 20.dp),
            checked = filtered, onChecked = { onFiltered(it) })
        Spacer(modifier = Modifier.height(76.dp))
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 1.dp,
            color = palette.outline.copy(alpha = .5F)
        )
        Spacer(modifier = Modifier.height(8.dp))
        AssetInfoFooter(
            modifier = Modifier.padding(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 32.dp
            )
        ) { toLearnMore() }
        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    showBackground = true, backgroundColor = 0xFF242323
)
@Composable
fun AssetsSettingsContentPreview() {
    FlexaTheme {
        AssetsSettingsScreen(
            filtered = false,
            onFiltered = {},
            toLearnMore = {}
        )
    }
}
