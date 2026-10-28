package co.electriccoin.zcash.ui.design.component.colorpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography

/**
 * The small rounded box next to a picker slider that shows its value, such as "100%" or "190", in
 * 17/22 SemiBold, so with its 6/14 dp padding the box is 34 dp tall.
 * With [onValueChange] it is a one-line text field of [keyboardType], 48 dp tall to tap, which
 * accessibility services announce with [label]; without it, plain text.
 */
@Composable
fun ZodlColorValueField(
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: ((String) -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Number,
    label: String? = null,
) {
    val style = valueTextStyle()
    if (onValueChange == null) {
        ValueBox(modifier = modifier) {
            Text(text = value, style = style)
        }
    } else {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                modifier
                    .heightIn(min = TOUCH_HEIGHT)
                    .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
            textStyle = style,
            singleLine = true,
            cursorBrush = SolidColor(ZashiColors.Text.textPrimary),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done
                ),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.Center) {
                    ValueBox(modifier = Modifier.fillMaxWidth()) { innerTextField() }
                }
            }
        )
    }
}

@Composable
private fun ValueBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier =
            modifier
                .background(ZashiColors.Surfaces.bgPrimary, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun valueTextStyle() =
    ZashiTypography.textMd.merge(
        TextStyle(
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
            textAlign = TextAlign.Center
        )
    )

private val TOUCH_HEIGHT = 48.dp

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            ZodlColorValueField(value = "100%")
        }
    }
