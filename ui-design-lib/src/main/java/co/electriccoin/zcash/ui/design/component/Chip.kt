package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.spackle.model.Index
import co.electriccoin.zcash.ui.design.theme.AppearanceMode
import co.electriccoin.zcash.ui.design.theme.ZcashTheme

@Preview
@Composable
private fun ComposableChipPreview() {
    ZcashTheme {
        Box(modifier = Modifier.padding(all = 12.dp)) {
            Chip("route")
        }
    }
}

@Preview
@Composable
private fun ComposableChipIndexedPreview() {
    ZcashTheme {
        Box(modifier = Modifier.padding(all = 12.dp)) {
            ChipIndexed(Index(0), "edict")
        }
    }
}

@Preview
@Composable
private fun ComposableLongChipPreview() {
    ZcashTheme {
        Box(modifier = Modifier.padding(all = 12.dp)) {
            ChipIndexed(
                Index(1),
                "a_very_long_seed_word_that_does_not_fit_into_the_chip_and_thus_needs_to_be_truncated"
            )
        }
    }
}

@Preview
@Composable
private fun ComposableChipOnSurfacePreview() {
    ZcashTheme {
        Box(modifier = Modifier.padding(all = 12.dp)) {
            ChipOnSurface({}, "ribbon")
        }
    }
}

@Preview
@Composable
private fun ComposableChipOnSurfaceDarkPreview() {
    ZcashTheme(appearanceMode = AppearanceMode.DARK) {
        Box(modifier = Modifier.padding(all = 12.dp)) {
            ChipOnSurface({}, "ribbon")
        }
    }
}

@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = ZcashTheme.colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.then(Modifier.testTag(CommonTag.CHIP))
    )
}

@Composable
fun ChipIndexed(
    index: Index,
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = "${index.value + 1}. $text",
        style = MaterialTheme.typography.bodyLarge,
        color = ZcashTheme.colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.then(Modifier.testTag(CommonTag.CHIP))
    )
}

@Composable
fun ChipOnSurface(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(size = ZcashTheme.dimens.regularRippleEffectCorner),
        modifier =
            modifier
                .padding(horizontal = ZcashTheme.dimens.spacingTiny)
                .border(
                    border =
                        BorderStroke(
                            width = ZcashTheme.dimens.chipStroke,
                            color = ZcashTheme.colors.layoutStrokeSecondary
                        ),
                    shape = RoundedCornerShape(size = ZcashTheme.dimens.regularRippleEffectCorner),
                ).clickable { onClick() },
        color = ZcashTheme.colors.primaryColor,
        shadowElevation = ZcashTheme.dimens.chipShadowElevation,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = ZcashTheme.colors.textPrimary,
            modifier =
                Modifier
                    .padding(
                        vertical = ZcashTheme.dimens.spacingMid,
                        horizontal = ZcashTheme.dimens.spacingDefault
                    ).testTag(CommonTag.CHIP)
        )
    }
}
