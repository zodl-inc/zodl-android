package co.electriccoin.zcash.ui.screen.voting.chainconfig

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.CircularScreenProgressIndicator
import co.electriccoin.zcash.ui.design.component.RadioButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationBottomSheet
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.ZashiTextField
import co.electriccoin.zcash.ui.design.component.ZashiTextFieldDefaults
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.voting.component.VoteAppBar
import kotlinx.coroutines.delay

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun VoteChainConfigView(state: VoteChainConfigState?) {
    if (state == null) {
        CircularScreenProgressIndicator()
        return
    }

    ZashiConfirmationBottomSheet(state = state.errorSheet)
    state.editor?.let { editor ->
        ZashiScreenModalBottomSheet(
            onDismissRequest = editor.cancelButton.onClick
        ) { contentPadding ->
            EditorSheet(
                state = editor,
                contentPadding = contentPadding
            )
        }
    }
    BlankBgScaffold(
        topBar = {
            VoteAppBar(
                title = stringResource(R.string.vote_chain_config_title),
                onBack = state.onBack,
            )
        },
        bottomBar = {
            if (state.editor == null) {
                BottomActions(state)
            }
        },
        content = { padding ->
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = padding.calculateTopPadding()),
                contentPadding =
                    PaddingValues(
                        start = ZashiDimensions.Spacing.spacing3xl,
                        top = ZashiDimensions.Spacing.spacingLg,
                        end = ZashiDimensions.Spacing.spacing3xl,
                        bottom = padding.calculateBottomPadding() + ZashiDimensions.Spacing.spacing3xl
                    ),
                verticalArrangement = Arrangement.spacedBy(ZashiDimensions.Spacing.spacing3xl)
            ) {
                item(key = "intro") {
                    Intro()
                }
                item(key = "chains") {
                    Column(verticalArrangement = Arrangement.spacedBy(ZashiDimensions.Spacing.spacingMd)) {
                        state.chains.forEach { item ->
                            ChainItem(item)
                        }
                    }
                }
                if (state.chains.size == 1) {
                    item(key = "empty_custom") {
                        Text(
                            text = stringResource(R.string.vote_chain_config_custom_empty),
                            style = ZashiTypography.textSm,
                            color = ZashiColors.Text.textTertiary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun Intro() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.vote_chain_config_intro_title),
            style = ZashiTypography.header6,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary
        )
        Text(
            text = stringResource(R.string.vote_chain_config_intro_subtitle),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary
        )
    }
}

@Composable
private fun ChainItem(state: VoteChainConfigItemState) {
    val isSelected = state.radioButtonState.isChecked

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 2.dp,
                            color = ZashiColors.Utility.Gray.utilityGray200,
                            shape = RoundedCornerShape(ZashiDimensions.Radius.radius2xl),
                        )
                    } else {
                        Modifier
                    }
                ).clickable(
                    enabled = !state.radioButtonState.isChecked,
                    role = Role.RadioButton,
                    onClick = state.radioButtonState.onClick
                ),
        shape = RoundedCornerShape(ZashiDimensions.Radius.radius2xl),
        color = if (isSelected) ZashiColors.Surfaces.bgPrimary else ZashiColors.Surfaces.bgSecondary,
        border =
            if (isSelected) {
                BorderStroke(1.dp, ZashiColors.Surfaces.bgAlt)
            } else {
                null
            }
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                width = 2.dp,
                                color = ZashiColors.Utility.Gray.utilityGray200,
                                shape = RoundedCornerShape(ZashiDimensions.Radius.radius2xl)
                            )
                        } else {
                            Modifier
                        }
                    ).padding(
                        start = ZashiDimensions.Spacing.spacing2xl,
                        top = ZashiDimensions.Spacing.spacingXl,
                        end = ZashiDimensions.Spacing.spacingXl,
                        bottom = ZashiDimensions.Spacing.spacingXl
                    ),
            horizontalArrangement = Arrangement.spacedBy(ZashiDimensions.Spacing.spacingXl),
            verticalAlignment = Alignment.Top
        ) {
            RadioIndicator(
                isSelected = isSelected,
                modifier = Modifier.padding(top = 2.dp)
            )

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(end = ZashiDimensions.Spacing.spacingXl)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ZashiDimensions.Spacing.spacingSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.radioButtonState.text.getValue(),
                        style = ZashiTypography.textMd,
                        fontWeight = FontWeight.Medium,
                        color = ZashiColors.Text.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(weight = 1f, fill = false)
                    )
                    if (state.isDefault) {
                        DefaultBadge()
                    }
                }
                Text(
                    text = state.radioButtonState.subtitle?.getValue() ?: state.fullUrl.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }

            state.editButton?.let { editButton ->
                IconButton(
                    onClick = editButton.onClick,
                    enabled = editButton.isEnabled,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_chevron_right),
                        contentDescription = editButton.text.getValue(),
                        tint = ZashiColors.Text.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioIndicator(
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier =
            modifier
                .size(20.dp)
                .background(
                    color = if (isSelected) ZashiColors.Checkboxes.boxOnBg else ZashiColors.Surfaces.bgPrimary,
                    shape = CircleShape
                ).border(
                    width = 1.dp,
                    color = if (isSelected) ZashiColors.Checkboxes.boxOnBg else ZashiColors.Checkboxes.boxOffStroke,
                    shape = CircleShape
                ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(ZashiColors.Surfaces.bgPrimary, CircleShape)
            )
        }
    }
}

@Composable
private fun BottomActions(state: VoteChainConfigState) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(ZashiColors.Surfaces.bgPrimary)
                .navigationBarsPadding()
                .padding(
                    start = ZashiDimensions.Spacing.spacing3xl,
                    end = ZashiDimensions.Spacing.spacing3xl,
                    bottom = ZashiDimensions.Spacing.spacing2xl
                ),
        verticalArrangement = Arrangement.spacedBy(ZashiDimensions.Spacing.spacingLg)
    ) {
        AddCustomSourceButton(state)
        ZashiButton(
            state = state.saveChangesButton,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
        )
    }
}

@Composable
private fun AddCustomSourceButton(state: VoteChainConfigState) {
    ZashiButton(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp),
        state =
            ButtonState(
                text = stringRes(R.string.vote_chain_config_add_custom_source),
                style = ButtonStyle.TERTIARY,
                isEnabled = !state.isValidating,
                onClick = state.onAddCustom
            ),
        defaultTertiaryColors =
            ZashiButtonDefaults.tertiaryColors(
                containerColor = ZashiColors.Surfaces.bgSecondary,
                contentColor = ZashiColors.Text.textPrimary
            ),
        content = { scope ->
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(ZashiDimensions.Spacing.spacingSm))
            scope.Text()
            scope.Loading()
        }
    )
}

@Composable
private fun DefaultBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = ZashiColors.Utility.Gray.utilityGray100,
        border = BorderStroke(1.dp, ZashiColors.Utility.Gray.utilityGray200)
    ) {
        Text(
            text = stringResource(R.string.vote_chain_config_default_badge),
            style = ZashiTypography.textXs,
            fontWeight = FontWeight.Medium,
            color = ZashiColors.Utility.Gray.utilityGray700,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun EditorSheet(
    state: VoteChainConfigEditorState,
    contentPadding: PaddingValues
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = contentPadding.calculateBottomPadding())
    ) {
        SheetHeader(state)

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZashiDimensions.Spacing.spacing3xl)
        ) {
            Text(
                text = state.title.getValue(),
                style = ZashiTypography.header6,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary
            )
            Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacingMd))
            Text(
                text = state.description.getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textPrimary
            )
            Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacing3xl))
            FieldLabel(text = stringResource(R.string.vote_chain_config_name_label))
            Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacingMd))
            ZashiTextField(
                state = state.name,
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                innerModifier = ZashiTextFieldDefaults.innerModifier.height(46.dp),
                colors = sheetTextFieldColors(isFocusedByDefault = true)
            )
            Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacingXl))
            FieldLabel(text = stringResource(R.string.vote_chain_config_url_label))
            Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacingMd))
            ZashiTextField(
                state = state.url,
                placeholder = {
                    Text(
                        text = stringResource(R.string.vote_chain_config_url_placeholder),
                        style = ZashiTypography.textMd,
                        color = ZashiColors.Text.textTertiary
                    )
                },
                trailingIcon =
                    if (state.showsUrlCopyButton) {
                        {
                            IconButton(
                                onClick = state.onUrlCopyClick,
                                enabled = state.url.isEnabled,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_copy),
                                    contentDescription = stringResource(R.string.wbh_copy),
                                    tint = ZashiColors.Text.textTertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else {
                        null
                    },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth(),
                innerModifier = ZashiTextFieldDefaults.innerModifier.height(46.dp),
                colors = sheetTextFieldColors(isFocusedByDefault = false)
            )
            Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacing3xl))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ZashiDimensions.Spacing.spacingLg)
            ) {
                state.deleteButton?.let { deleteButton ->
                    ZashiButton(
                        state = deleteButton,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                    )
                }
                ZashiButton(
                    state = state.saveButton,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(EDITOR_FOCUS_DELAY_MS)
        focusRequester.requestFocus()
        keyboardController?.show()
    }
}

@Composable
private fun SheetHeader(state: VoteChainConfigEditorState) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ZashiDimensions.Spacing.spacingXl)
                .padding(bottom = ZashiDimensions.Spacing.spacing3xl),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = state.sheetTitle.getValue().uppercase(),
            style = ZashiTypography.textMd,
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary
        )
        Surface(
            shape = CircleShape,
            color = ZashiColors.Surfaces.bgSecondary,
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp)
                    .clickable { state.cancelButton.onClick() }
        ) {
            Icon(
                painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_navigation_close),
                contentDescription = null,
                tint = ZashiColors.Text.textSecondary,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = ZashiTypography.textSm,
        fontWeight = FontWeight.Medium,
        color = ZashiColors.Text.textPrimary
    )
}

@Composable
private fun sheetTextFieldColors(isFocusedByDefault: Boolean) =
    ZashiTextFieldDefaults.defaultColors(
        textColor = ZashiColors.Text.textPrimary,
        borderColor = if (isFocusedByDefault) ZashiColors.Surfaces.bgAlt else Color.Unspecified,
        focusedBorderColor = ZashiColors.Surfaces.bgAlt,
        containerColor = if (isFocusedByDefault) ZashiColors.Surfaces.bgPrimary else ZashiColors.Surfaces.bgSecondary,
        focusedContainerColor = ZashiColors.Surfaces.bgPrimary,
        placeholderColor = ZashiColors.Text.textTertiary
    )

private const val EDITOR_FOCUS_DELAY_MS = 100L

@PreviewScreens
@Composable
private fun VoteChainConfigPreview() =
    ZcashTheme {
        VoteChainConfigView(
            state =
                VoteChainConfigState.preview.copy(
                    chains =
                        listOf(
                            VoteChainConfigItemState.preview,
                            VoteChainConfigItemState.preview.copy(
                                id = "custom",
                                radioButtonState =
                                    RadioButtonState(
                                        text = stringRes("Local test"),
                                        subtitle = stringRes("https://example.com/static-voting-config.json"),
                                        isChecked = false,
                                        onClick = {},
                                    ),
                                fullUrl = stringRes("https://example.com/static-voting-config.json"),
                                isDefault = false,
                                editButton = ButtonState(text = stringRes("Edit"), style = ButtonStyle.TERTIARY),
                                deleteButton =
                                    ButtonState(
                                        text = stringRes("Delete"),
                                        style = ButtonStyle.DESTRUCTIVE2
                                    ),
                            ),
                        ),
                )
        )
    }
