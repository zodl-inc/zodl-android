@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.theme.ZcashTheme

@Preview(showBackground = true)
@Composable
private fun ReferenceComposablePreview() {
    ZcashTheme(forceDarkMode = false) {
        BlankSurface {
            Column {
                Reference(
                    text = "Test reference text",
                    onClick = {},
                    modifier = Modifier.padding(all = ZcashTheme.dimens.spacingDefault)
                )
                Reference(
                    text = "Reference with icon",
                    imageVector = Icons.Outlined.AccountBox,
                    onClick = {},
                    modifier = Modifier.padding(all = ZcashTheme.dimens.spacingDefault)
                )
                Reference(
                    text = "Normal font weight reference",
                    onClick = {},
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(all = ZcashTheme.dimens.spacingDefault)
                )
            }
        }
    }
}

// Add previews for the rest of the composables

@Composable
fun Header(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = ZcashTheme.colors.textPrimary,
) {
    Text(
        text = text,
        color = color,
        textAlign = textAlign,
        modifier = modifier,
        style = ZcashTheme.typography.primary.headlineLarge,
    )
}

@Composable
fun SubHeader(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = ZcashTheme.colors.textPrimary,
) {
    Text(
        text = text,
        color = color,
        textAlign = textAlign,
        modifier = modifier,
        style = ZcashTheme.typography.secondary.headlineSmall,
    )
}

@Composable
@Suppress("LongParameterList")
fun BodySmall(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign = TextAlign.Start,
    textFontWeight: FontWeight = FontWeight.Normal,
    color: Color = ZcashTheme.colors.textPrimary,
) {
    Text(
        text = text,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = textFontWeight
    )
}

@Composable
@Suppress("LongParameterList")
fun Body(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign = TextAlign.Start,
    textFontWeight: FontWeight = FontWeight.Normal,
    color: Color = ZcashTheme.colors.textPrimary,
) {
    Text(
        text = text,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        modifier = modifier,
        style = ZcashTheme.typography.primary.bodyLarge,
        fontWeight = textFontWeight
    )
}

@Composable
@Suppress("LongParameterList")
fun TitleLarge(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = ZcashTheme.colors.textPrimary,
) {
    Text(
        text = text,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
@Suppress("LongParameterList")
fun Small(
    text: String,
    modifier: Modifier = Modifier,
    textFontWeight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = ZcashTheme.colors.textPrimary,
) {
    Text(
        text = text,
        color = color,
        fontWeight = textFontWeight,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
@Suppress("LongParameterList")
fun Tiny(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = ZcashTheme.colors.textPrimary,
) {
    Text(
        text = text,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
@Suppress("LongParameterList")
fun TextWithIcon(
    iconVector: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    iconContentDescription: String? = null,
    iconTintColor: Color? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign = TextAlign.Start,
    style: TextStyle = LocalTextStyle.current,
    color: Color = ZcashTheme.colors.textPrimary,
    fontWeight: FontWeight? = null,
) {
    Row(
        modifier =
            Modifier.then(modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconTintColor != null) {
            Image(
                imageVector = iconVector,
                colorFilter = ColorFilter.tint(color = iconTintColor),
                contentDescription = iconContentDescription,
            )
        } else {
            Image(
                imageVector = iconVector,
                contentDescription = iconContentDescription
            )
        }

        Spacer(modifier = Modifier.padding(3.dp))

        Text(
            text = text,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = textAlign,
            style = style,
            fontWeight = fontWeight
        )
    }
}

@Suppress("LongParameterList")
@Composable
fun Reference(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.SemiBold,
    textAlign: TextAlign = TextAlign.Center,
    textStyle: TextStyle = ZcashTheme.typography.primary.bodyLarge,
    color: Color = ZcashTheme.colors.reference,
    imageVector: ImageVector? = null,
    imageContentDescription: String? = null
) {
    Row(
        modifier =
            Modifier
                .wrapContentSize()
                .clip(RoundedCornerShape(ZcashTheme.dimens.regularRippleEffectCorner))
                .clickable { onClick() }
                .then(modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        imageVector?.let {
            Icon(
                imageVector = imageVector,
                contentDescription = imageContentDescription
            )
        }
        Spacer(modifier = Modifier.padding(ZcashTheme.dimens.spacingTiny))
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style =
                textStyle.merge(
                    TextStyle(
                        color = color,
                        textAlign = textAlign,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = fontWeight
                    )
                )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NavigationTabTextPreview() {
    ZcashTheme(forceDarkMode = false) {
        BlankSurface {
            Column {
                NavigationTabText(
                    text = "Account",
                    selected = false,
                    modifier = Modifier,
                    onClick = {}
                )
            }
        }
    }
}

@Composable
fun NavigationTabText(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = ZcashTheme.extendedTypography.textNavTab,
        textAlign = TextAlign.Center,
        fontWeight =
            if (selected) {
                FontWeight.Black
            } else {
                FontWeight.Normal
            },
        maxLines = 1,
        overflow = TextOverflow.Visible,
        color = ZcashTheme.colors.textPrimary,
        modifier =
            Modifier
                .clip(RoundedCornerShape(ZcashTheme.dimens.regularRippleEffectCorner))
                .clickable { onClick() }
                .then(modifier)
    )
}
