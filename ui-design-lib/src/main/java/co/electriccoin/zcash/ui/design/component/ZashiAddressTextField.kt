package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.theme.typography.RobotoMonoFontFamily
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import kotlin.math.absoluteValue

/**
 * The single-line address field, which truncates the address around an ellipsis while it is not
 * focused. The keyboard is asked not to learn from what is typed, by [WithoutPersonalizedLearning].
 */
@Suppress("LongParameterList")
@Composable
fun ZashiAddressTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    innerModifier: Modifier = ZashiTextFieldDefaults.innerModifier,
    textStyle: TextStyle =
        ZashiTypography.textMd.copy(
            fontWeight = FontWeight.Medium,
            fontFamily = RobotoMonoFontFamily
        ),
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = ZashiTextFieldDefaults.shape,
    contentPadding: PaddingValues =
        ZashiAddressTextFieldDefaults
            .contentPadding(leadingIcon, suffix, trailingIcon, prefix),
    colors: ZashiTextFieldColors = ZashiTextFieldDefaults.defaultColors()
) {
    val isFocused by interactionSource.collectIsFocusedAsState()

    val visualTransformation =
        remember(isFocused) {
            if (isFocused) VisualTransformation.None else ellipsisVisualTransformation()
        }

    WithoutPersonalizedLearning {
        ZashiTextField(
            state = state,
            modifier = modifier,
            innerModifier = innerModifier,
            textStyle = textStyle,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            prefix = prefix,
            suffix = suffix,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            maxLines = 1,
            minLines = 1,
            singleLine = true,
            interactionSource = interactionSource,
            shape = shape,
            contentPadding = contentPadding,
            colors = colors,
        )
    }
}

@Suppress("MagicNumber")
private fun ellipsisVisualTransformation() =
    VisualTransformation { text ->
        val ellipsis = "..."
        val maxLength =
            (text.length / 2)
                .coerceAtMost(text.length / 3)
                .coerceAtMost(8)

        val mapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = 0

                override fun transformedToOriginal(offset: Int): Int =
                    when {
                        text.length <= 16 -> {
                            offset
                        }

                        offset <= maxLength -> {
                            offset
                        }

                        offset in (maxLength + 1)..(maxLength + 2) -> {
                            maxLength
                        }

                        else -> {
                            val whole = maxLength * 2 + 3
                            val fromRight = (offset - whole).absoluteValue
                            text.length - fromRight
                        }
                    }.coerceIn(0, text.length)
            }

        TransformedText(
            AnnotatedString
                .Builder()
                .apply {
                    when {
                        text.length <= 16 -> {
                            append(text)
                        }

                        text.isNotBlank() -> {
                            append(text.take(maxLength))
                            append(ellipsis)
                            append(text.takeLast(maxLength))
                        }
                    }
                }.toAnnotatedString(),
            mapping
        )
    }

object ZashiAddressTextFieldDefaults {
    @Composable
    fun contentPadding(
        leadingIcon: @Composable (() -> Unit)?,
        suffix: @Composable (() -> Unit)?,
        trailingIcon: @Composable (() -> Unit)?,
        prefix: @Composable (() -> Unit)?
    ) = PaddingValues(
        start = if (leadingIcon != null || prefix != null) 8.dp else 14.dp,
        end = if (suffix != null) 4.dp else 12.dp,
        top = getVerticalPadding(trailingIcon, leadingIcon, suffix, prefix),
        bottom = getVerticalPadding(trailingIcon, leadingIcon, suffix, prefix),
    )
}
