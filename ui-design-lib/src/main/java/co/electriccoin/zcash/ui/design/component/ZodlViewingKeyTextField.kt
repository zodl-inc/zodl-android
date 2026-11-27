package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography

/**
 * The single-line field a viewing key is pasted into. It truncates the key to its head and tail
 * around an ellipsis while it is not focused, exactly as [ZashiAddressTextField] does for an address,
 * and shows the whole key while it is. It differs from the address field only in its defaults: the
 * 14 sp text and a keyboard that does not suggest, store or correct what is typed.
 *
 * A button at the end of the field goes in [suffix], as in the Crosspay address field: the field pads
 * a suffix 4 dp on every side, so a 36 dp button leaves 4 dp around it.
 */
@Suppress("LongParameterList")
@Composable
fun ZodlViewingKeyTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    innerModifier: Modifier = ZashiTextFieldDefaults.innerModifier,
    textStyle: TextStyle = ZashiTypography.textSm.copy(fontWeight = FontWeight.Normal),
    placeholder: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = ZodlViewingKeyTextFieldDefaults.keyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = ZashiTextFieldDefaults.shape,
    contentPadding: PaddingValues = ZashiAddressTextFieldDefaults.contentPadding(null, suffix, null, null),
    colors: ZashiTextFieldColors = ZashiTextFieldDefaults.defaultColors()
) {
    ZashiAddressTextField(
        state = state,
        modifier = modifier,
        innerModifier = innerModifier,
        textStyle = textStyle,
        placeholder = placeholder,
        suffix = suffix,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        shape = shape,
        contentPadding = contentPadding,
        colors = colors,
    )
}

object ZodlViewingKeyTextFieldDefaults {
    val keyboardOptions: KeyboardOptions
        get() =
            KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
            )
}
