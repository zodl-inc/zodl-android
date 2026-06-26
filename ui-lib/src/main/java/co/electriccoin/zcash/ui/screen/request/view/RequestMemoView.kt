package co.electriccoin.zcash.ui.screen.request.view

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import cash.z.ecc.android.sdk.model.Memo
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ZashiBadge
import co.electriccoin.zcash.ui.design.component.ZashiBadgeColors
import co.electriccoin.zcash.ui.design.component.ZashiTextField
import co.electriccoin.zcash.ui.design.component.ZashiTextFieldDefaults
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.screen.request.model.MemoState
import co.electriccoin.zcash.ui.screen.request.model.RequestState

@Composable
internal fun RequestMemoView(
    state: RequestState.Memo,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ZcashTheme.dimens.spacingLarge),
    ) {
        Spacer(Modifier.height(ZcashTheme.dimens.spacingDefault))

        Image(
            modifier = Modifier.size(64.dp),
            painter = painterResource(id = state.icon),
            contentDescription = null
        )

        Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingLarge))

        ZashiBadge(
            text = stringResource(id = R.string.component_maxPrivacy),
            leadingIconVector = painterResource(id = R.drawable.ic_solid_check),
            colors =
                ZashiBadgeColors(
                    border = ZashiColors.Utility.Purple.utilityPurple200,
                    text = ZashiColors.Utility.Purple.utilityPurple700,
                    container = ZashiColors.Utility.Purple.utilityPurple50,
                )
        )

        Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingDefault))

        Text(
            text = stringResource(id = R.string.send_requestPayment_title),
            style = ZashiTypography.textLg,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Text.textTertiary
        )

        Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingMid))

        RequestMemoZecAmountView(
            state = state,
            modifier = Modifier.padding(horizontal = ZcashTheme.dimens.spacingSmall)
        )

        Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingBig))

        RequestMemoTextField(state = state)

        Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingDefault))
    }
}

@Composable
private fun RequestMemoZecAmountView(
    state: RequestState.Memo,
    modifier: Modifier = Modifier
) {
    val zecText =
        buildAnnotatedString {
            withStyle(style = SpanStyle(color = ZashiColors.Text.textPrimary)) {
                append(state.request.memoState.zecAmount)
            }
            append("\u2009") // Add an extra thin space between the texts
            withStyle(style = SpanStyle(color = ZashiColors.Text.textQuaternary)) {
                append(state.zcashCurrency.localizedName(LocalContext.current))
            }
        }

    AutoSizingText(
        text = zecText,
        style =
            ZashiTypography.header1.copy(
                fontWeight = FontWeight.SemiBold
            ),
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RequestMemoTextField(
    state: RequestState.Memo,
    modifier: Modifier = Modifier
) {
    val memoState = state.request.memoState
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    Column(
        modifier =
            modifier
                // Animate error show/hide
                .animateContentSize()
                // Scroll TextField above ime keyboard
                .bringIntoViewRequester(bringIntoViewRequester)
                .focusRequester(focusRequester),
    ) {
        ZashiTextField(
            minLines = 3,
            value = memoState.text,
            // Empty error message as the length counter color is used for error signaling
            error = if (memoState.isValid()) null else "",
            onValueChange = {
                state.onMemo(MemoState.new(it, memoState.zecAmount))
            },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Default,
                    capitalization = KeyboardCapitalization.Sentences
                ),
            placeholder = {
                Text(
                    text = stringResource(id = R.string.requestZec_whatFor),
                    style = ZashiTypography.textMd,
                    color = ZashiColors.Inputs.Default.text
                )
            },
            colors =
                if (memoState.isValid()) {
                    ZashiTextFieldDefaults.defaultColors()
                } else {
                    ZashiTextFieldDefaults.defaultColors(
                        disabledTextColor = ZashiColors.Inputs.Disabled.text,
                        disabledHintColor = ZashiColors.Inputs.Disabled.hint,
                        disabledBorderColor = Color.Unspecified,
                        disabledContainerColor = ZashiColors.Inputs.Disabled.bg,
                        disabledPlaceholderColor = ZashiColors.Inputs.Disabled.text,
                    )
                },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text =
                stringResource(
                    id = R.string.request_memo_bytes_counter,
                    Memo.MAX_MEMO_LENGTH_BYTES - memoState.byteSize,
                    Memo.MAX_MEMO_LENGTH_BYTES
                ),
            color =
                if (memoState.isValid()) {
                    ZashiColors.Inputs.Default.hint
                } else {
                    ZashiColors.Inputs.Filled.required
                },
            textAlign = TextAlign.End,
            style = ZashiTypography.textSm,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = ZcashTheme.dimens.spacingTiny)
        )

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}
