package co.electriccoin.zcash.ui.common.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.core.net.toUri
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import co.electriccoin.zcash.ui.design.component.EnhancedTextFieldState
import co.electriccoin.zcash.ui.design.component.InnerTextFieldState
import co.electriccoin.zcash.ui.design.component.TextSelection
import co.electriccoin.zcash.ui.design.component.ZashiTextField
import co.electriccoin.zcash.ui.design.component.ZashiTextFieldColors
import co.electriccoin.zcash.ui.design.component.ZashiTextFieldDefaults
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getString
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes

@Suppress("LongParameterList")
@Composable
fun ZashiEndpointTextField(
    state: EndpointTextFieldState,
    modifier: Modifier = Modifier,
    innerModifier: Modifier = ZashiTextFieldDefaults.innerModifier,
    textStyle: TextStyle = ZashiTypography.textMd.copy(fontWeight = FontWeight.Medium),
    placeholder: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = ZashiTextFieldDefaults.shape,
    contentPadding: PaddingValues =
        ZashiTextFieldDefaults.contentPadding(
            leadingIcon = leadingIcon,
            suffix = suffix,
            trailingIcon = null,
            prefix = prefix
        ),
    colors: ZashiTextFieldColors = ZashiTextFieldDefaults.defaultColors()
) {
    val textFieldState = createTextFieldState(state)
    ZashiTextField(
        state = textFieldState,
        modifier = modifier,
        innerModifier = innerModifier,
        textStyle = textStyle,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = null,
        prefix = prefix,
        suffix = suffix,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        maxLines = 1,
        minLines = 1,
        interactionSource = interactionSource,
        shape = shape,
        contentPadding = contentPadding,
        colors = colors,
    )
}

@Composable
private fun createTextFieldState(state: EndpointTextFieldState): EnhancedTextFieldState {
    val context = LocalContext.current
    val text =
        state.innerState.innerTextFieldState.value
            .getValue()
    val selection =
        when (val selection = state.innerState.innerTextFieldState.selection) {
            is TextSelection.ByTextRange -> {
                TextSelection.ByTextRange(
                    TextRange(selection.range.start, selection.range.end.coerceAtMost(text.length))
                )
            }

            TextSelection.End -> {
                selection
            }

            TextSelection.Start -> {
                selection
            }
        }

    return EnhancedTextFieldState(
        innerState =
            state.innerState.innerTextFieldState.copy(
                value = stringRes(text),
                selection = selection
            ),
        isEnabled = state.isEnabled,
        error = state.explicitError ?: state.defaultEndpointError.takeIf { state.innerState.isError },
        onValueChange = { innerState ->
            val newText = innerState.value.getString(context)
            val endpoint: LightWalletEndpoint?
            val lastValidEndpoint: LightWalletEndpoint?

            if (newText != text) {
                endpoint = ZashiEndpointTextFieldParser.toEndpointOrNull(newText)
                lastValidEndpoint = endpoint ?: state.innerState.lastValidEndpoint
            } else {
                endpoint = state.innerState.endpoint
                lastValidEndpoint = state.innerState.lastValidEndpoint
            }

            val new =
                state.innerState.copy(
                    innerTextFieldState =
                        state.innerState.innerTextFieldState.copy(
                            value = stringRes(newText),
                            selection = innerState.selection
                        ),
                    endpoint = endpoint,
                    lastValidEndpoint = lastValidEndpoint
                )
            state.onValueChange(new)
        }
    )
}

data class EndpointTextFieldState(
    val innerState: EndpointTextFieldInnerState = EndpointTextFieldInnerState(),
    val isEnabled: Boolean = true,
    val explicitError: StringResource? = null,
    val defaultEndpointError: StringResource = stringRes(""),
    val onValueChange: (EndpointTextFieldInnerState) -> Unit,
) {
    val isError = explicitError != null || innerState.isError
}

data class EndpointTextFieldInnerState(
    val innerTextFieldState: InnerTextFieldState =
        InnerTextFieldState(
            value = stringRes(value = ""),
            selection = TextSelection.Start
        ),
    val endpoint: LightWalletEndpoint? = null,
    val lastValidEndpoint: LightWalletEndpoint? = null,
) {
    val isError = endpoint == null && !innerTextFieldState.value.isEmpty()

    companion object {
        fun fromEndpoint(endpoint: LightWalletEndpoint) =
            EndpointTextFieldInnerState(
                innerTextFieldState =
                    InnerTextFieldState(
                        value = stringRes("${endpoint.host}:${endpoint.port}"),
                        selection = TextSelection.Start
                    ),
                endpoint = endpoint,
                lastValidEndpoint = endpoint
            )
    }
}

object ZashiEndpointTextFieldParser {
    /**
     * Convert user's [input] to a [LightWalletEndpoint]. The input may include a protocol scheme
     * (e.g. `https://host:port`) or omit it (`host:port`). Returns `null` if the input is not a
     * well-formed endpoint.
     */
    @Suppress("ComplexCondition", "TooGenericExceptionCaught", "ReturnCount", "MagicNumber")
    fun toEndpointOrNull(input: String): LightWalletEndpoint? {
        return try {
            if (!ENDPOINT_REGEX.toRegex().matches(input)) return null

            val uri =
                if (input.contains("://")) {
                    input.toUri()
                } else {
                    "https://$input".toUri()
                }

            val host = uri.host
            val port = uri.port

            if (host.isNullOrBlank() ||
                host.startsWith(".") ||
                host.endsWith(".") ||
                host.contains("..")
            ) {
                return null
            }

            if (port !in 1..65535) {
                return null
            }

            LightWalletEndpoint(host, port, true)
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("MaxLineLength", "ktlint:standard:max-line-length")
    private const val ENDPOINT_REGEX =
        "^(([^:/?#\\s]+)://)?([^:/?#\\s.][^:/?#\\s]*[^:/?#\\s.]):([1-9]|[1-9][0-9]{1,3}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5])$"
}
