package co.electriccoin.zcash.ui.screen.contact

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.PickerState
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.component.ZashiAddressTextField
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiIconButton
import co.electriccoin.zcash.ui.design.component.ZashiPicker
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTextField
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun ABContactView(
    state: ABContactState,
    onSideEffect: (nameFocusRequester: FocusRequester, addressFocusRequester: FocusRequester) -> Unit = { _, _ -> }
) {
    val addressFocusRequester = remember { FocusRequester() }
    val nameFocusRequester = remember { FocusRequester() }
    BlankBgScaffold(
        topBar = {
            ContactTopAppBar(onBack = state.onBack, state = state)
        }
    ) { paddingValues ->
        ContactViewInternal(
            state = state,
            modifier =
                Modifier
                    .fillMaxSize()
                    .scaffoldPadding(paddingValues)
                    .verticalScroll(rememberScrollState()),
            addressFocusRequester = addressFocusRequester,
            nameFocusRequester = nameFocusRequester
        )

        SideEffect {
            onSideEffect(nameFocusRequester, addressFocusRequester)
        }
    }
}

@Composable
private fun ContactViewInternal(
    state: ABContactState,
    addressFocusRequester: FocusRequester,
    nameFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        Text(
            text = stringResource(id = R.string.contact_address_label),
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Inputs.Filled.label
        )
        Spacer(modifier = Modifier.height(6.dp))
        ZashiAddressTextField(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(addressFocusRequester),
            state = state.walletAddress,
            placeholder = {
                Text(
                    text = stringResource(id = R.string.contact_address_hint),
                    style = ZashiTypography.textMd,
                    color = ZashiColors.Inputs.Default.text
                )
            },
            keyboardOptions =
                KeyboardOptions(
                    imeAction = ImeAction.Next
                )
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(id = R.string.contact_name_label),
            style = ZashiTypography.textSm,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Inputs.Filled.label
        )
        Spacer(modifier = Modifier.height(6.dp))
        ZashiTextField(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocusRequester),
            state = state.contactName,
            keyboardOptions =
                KeyboardOptions(
                    imeAction = ImeAction.Done,
                    capitalization = KeyboardCapitalization.Words
                ),
            placeholder = {
                Text(
                    text = stringResource(id = R.string.contact_name_hint),
                    style = ZashiTypography.textMd,
                    color = ZashiColors.Inputs.Default.text
                )
            }
        )

        if (state.chain != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.contact_select_chain),
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Inputs.Filled.label
            )
            Spacer(6.dp)
            ZashiPicker(
                state = state.chain,
                modifier = Modifier.testTag(ABContactTag.CHAIN_SELECTOR)
            )
        }
        Spacer(1f)
        Spacer(24.dp)
        ZashiButton(
            state = state.positiveButton,
            modifier = Modifier.fillMaxWidth().testTag(ABContactTag.SAVE_BUTTON)
        )

        state.negativeButton?.let {
            ZashiButton(
                state = it,
                modifier = Modifier.fillMaxWidth().testTag(ABContactTag.DELETE_BUTTON),
                defaultPrimaryColors = ZashiButtonDefaults.destructive1Colors()
            )
        }
    }
}

@Composable
private fun ContactTopAppBar(
    onBack: () -> Unit,
    state: ABContactState
) {
    ZashiSmallTopAppBar(
        title = state.title.getValue(),
        modifier = Modifier.testTag(ABContactTag.TOP_APP_BAR),
        showTitleLogo = true,
        navigationAction = {
            ZashiTopAppBarBackNavigation(onBack = onBack)
        },
        regularActions = {
            state.info?.let {
                ZashiIconButton(it)
                Spacer(20.dp)
            }
        }
    )
}

@PreviewScreens
@Composable
private fun DataPreview() {
    ZcashTheme {
        ABContactView(
            state =
                ABContactState(
                    info = IconButtonState(R.drawable.ic_help) {},
                    title = stringRes("Title"),
                    walletAddress = TextFieldState(stringRes("Address")) {},
                    contactName = TextFieldState(stringRes("Name")) {},
                    chain =
                        PickerState(
                            bigIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_item_keystone),
                            smallIcon = imageRes(co.electriccoin.zcash.ui.design.R.drawable.ic_item_keystone),
                            text = stringRes("Text"),
                            placeholder = stringRes("Placeholder"),
                            onClick = {}
                        ),
                    negativeButton =
                        ButtonState(
                            text = stringRes("Negative"),
                        ),
                    positiveButton =
                        ButtonState(
                            text = stringRes("Positive"),
                        ),
                    onBack = {}
                ),
        )
    }
}

@PreviewScreens
@Composable
private fun LoadingPreview() {
    ZcashTheme {
        ABContactView(
            state =
                ABContactState(
                    info = null,
                    title = stringRes("Title"),
                    walletAddress = TextFieldState(stringRes("Address")) {},
                    contactName = TextFieldState(stringRes("Name")) {},
                    chain = null,
                    negativeButton =
                        ButtonState(
                            text = stringRes("Add New Contact"),
                        ),
                    positiveButton =
                        ButtonState(
                            text = stringRes("Add New Contact"),
                        ),
                    onBack = {}
                ),
        )
    }
}
