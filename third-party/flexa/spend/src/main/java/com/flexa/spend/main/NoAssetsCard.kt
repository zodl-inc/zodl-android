package com.flexa.spend.main

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flexa.core.theme.FlexaTheme
import com.flexa.spend.R
import com.flexa.spend.main.assets.AssetsState

@Composable
fun NoAssetsCard(
    modifier: Modifier = Modifier,
    assetsState: AssetsState,
    toAddAssets: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(color = MaterialTheme.colorScheme.onPrimary),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(id = R.string.nothing_to_spend),
                style = MaterialTheme.typography.headlineSmall
                    .copy(color = MaterialTheme.colorScheme.onBackground)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                modifier = Modifier.widthIn(0.dp, 200.dp),
                text = if (assetsState is AssetsState.NoAssets) {
                    when {
                        assetsState.incorrectAssetTickersList.size == 1 -> {
                            stringResource(id = R.string.few_wrong_assets_copy, assetsState.incorrectAssetTickersList.first())
                        }
                        assetsState.incorrectAssetTickersList.size == 2 -> {
                            val split = "${assetsState.incorrectAssetTickersList.first()} or ${assetsState.incorrectAssetTickersList[1]}"
                            stringResource(id = R.string.few_wrong_assets_copy, split)
                        }
                        assetsState.incorrectAssetTickersList.size > 2 -> {
                            val split = "${assetsState.incorrectAssetTickersList.first()}, or ${assetsState.incorrectAssetTickersList[1]}, or"
                            stringResource(id = R.string.many_wrong_assets_copy, split)
                        }
                        else -> stringResource(id = R.string.not_passed_assets_copy)
                    }
                } else "",
                style = TextStyle(
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 14.sp, fontWeight = FontWeight.Normal
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(40.dp))
            Button(
                modifier = Modifier
                    .widthIn(150.dp, 200.dp),
                contentPadding = PaddingValues(0.dp),
                onClick = { toAddAssets.invoke() },
            ) { Text(text = stringResource(id = R.string.add_assets)) }
        }
    }
}

@Preview(backgroundColor = 0xFF03A9F4, showBackground = true)
@Preview(
    backgroundColor = 0xFF03A9F4, showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
fun NoAssetsCardPreview() {
    FlexaTheme {
        NoAssetsCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp),
            assetsState = AssetsState.NoAssets(
                listOf("PEPE", "SUGAR", "YETI", "SUGAR")
            ),
            toAddAssets = {}
        )
    }
}
