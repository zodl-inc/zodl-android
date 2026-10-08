package co.electriccoin.zcash.ui.screen.redeemgift.paste

import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTextField
import co.electriccoin.zcash.ui.design.component.ZashiTextFieldDefaults
import co.electriccoin.zcash.ui.design.component.ZashiTextFieldPlaceholder
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.component.zashiFrostedHeader
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.Compose
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding

@Composable
fun PasteGiftCardLinkView(state: PasteGiftCardLinkState) {
    val hazeState = rememberZashiFrostState()
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostedHeader(hazeState),
                title = state.title.getValue(),
                navigationAction = {
                    ZashiTopAppBarBackNavigation(onBack = state.onBack)
                },
                colors =
                    ZcashTheme.colors.topAppBarColors.copyColors(
                        containerColor = Color.Transparent
                    ),
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .zashiFrostSource(hazeState)
        ) {
            Content(
                state = state,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .scaffoldPadding(paddingValues)
            )
        }
    }
}

@Composable
private fun Content(
    state: PasteGiftCardLinkState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacing3xl))
        state.image.Compose(modifier = Modifier.size(108.dp))
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacing3xl))
        Text(
            text = state.heading.getValue(),
            style = ZashiTypography.textXl,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacingMd))
        Text(
            text = state.subtitle.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacing4xl))
        LinkField(state)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacing3xl))
        ZashiButton(
            state = state.continueButton,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(PasteGiftCardLinkTag.CONTINUE_BUTTON)
        )
    }
}

@Composable
private fun LinkField(state: PasteGiftCardLinkState) {
    val invalidHint = state.invalidHint?.getValue()
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = state.fieldLabel.getValue(),
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Inputs.Default.label
        )
        Spacer(Modifier.height(ZashiDimensions.Spacing.spacingSm))
        WithoutPersonalizedLearning {
            ZashiTextField(
                state = state.field,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(PasteGiftCardLinkTag.FIELD),
                innerModifier =
                    if (invalidHint == null) {
                        ZashiTextFieldDefaults.innerModifier
                    } else {
                        ZashiTextFieldDefaults.innerModifier.semantics { error(invalidHint) }
                    },
                singleLine = true,
                placeholder = { ZashiTextFieldPlaceholder(state.placeholder) },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false
                    ),
                keyboardActions =
                    KeyboardActions(
                        onDone = { if (state.continueButton.isEnabled) state.continueButton.onClick() }
                    ),
                trailingIcon = {
                    ZashiButton(
                        state = state.fieldButton,
                        modifier = Modifier.testTag(PasteGiftCardLinkTag.FIELD_BUTTON),
                        style = ZashiTypography.textSm,
                        contentPadding = PaddingValues(horizontal = ZashiDimensions.Spacing.spacingXl),
                        defaultSecondaryColors =
                            ZashiButtonDefaults.secondaryColors(containerColor = Color.Transparent),
                    )
                }
            )
        }
        if (invalidHint != null) {
            Spacer(Modifier.height(ZashiDimensions.Spacing.spacingSm))
            Text(
                modifier = Modifier.testTag(PasteGiftCardLinkTag.INVALID_HINT),
                text = invalidHint,
                style = ZashiTypography.textXs,
                color = ZashiColors.Inputs.ErrorDefault.hint
            )
        }
    }
}

/**
 * Asks the keyboard not to learn from or personalise on what is typed into [content]: the link carries a spending
 * secret. Compose's keyboard options cannot set [EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING], so it is added to the
 * editor info of every input connection started below.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WithoutPersonalizedLearning(content: @Composable () -> Unit) {
    val interceptor =
        remember {
            PlatformTextInputInterceptor { request, nextHandler ->
                nextHandler.startInputMethod(
                    PlatformTextInputMethodRequest { outAttributes ->
                        request.createInputConnection(outAttributes).also {
                            outAttributes.imeOptions =
                                outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                        }
                    }
                )
            }
        }
    InterceptPlatformTextInput(interceptor = interceptor, content = content)
}

object PasteGiftCardLinkTag {
    const val FIELD = "paste_gift_card_link_field"
    const val FIELD_BUTTON = "paste_gift_card_link_field_button"
    const val INVALID_HINT = "paste_gift_card_link_invalid_hint"
    const val CONTINUE_BUTTON = "paste_gift_card_link_continue_button"
}

@PreviewScreens
@Composable
private fun EmptyPreview() =
    ZcashTheme {
        PasteGiftCardLinkView(PasteGiftCardLinkState.preview)
    }

@PreviewScreens
@Composable
private fun PastedPreview() =
    ZcashTheme {
        PasteGiftCardLinkView(PasteGiftCardLinkState.previewPasted)
    }

@PreviewScreens
@Composable
private fun InvalidPreview() =
    ZcashTheme {
        PasteGiftCardLinkView(PasteGiftCardLinkState.previewInvalid)
    }
