package co.electriccoin.zcash.ui.screen.voting.chainconfig

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import co.electriccoin.zcash.ui.design.component.ZashiConfirmationBottomSheet
import co.electriccoin.zcash.ui.design.component.ZashiRadioButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTextField
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.orDark
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun VoteChainConfigView(state: VoteChainConfigState?) {
    if (state == null) {
        CircularScreenProgressIndicator()
        return
    }

    ZashiConfirmationBottomSheet(state = state.errorSheet)
    BlankBgScaffold(
        topBar = { AppBar(state) },
        content = { padding ->
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .scaffoldPadding(padding),
                contentPadding = PaddingValues(
                    horizontal = ZashiDimensions.Spacing.spacingMd,
                    vertical = ZashiDimensions.Spacing.spacingMd
                ),
                verticalArrangement = Arrangement.spacedBy(ZashiDimensions.Spacing.spacingLg)
            ) {
                item(key = "intro") {
                    Intro()
                }
                state.editor?.let { editor ->
                    item(key = "editor") {
                        Editor(editor)
                    }
                }
                items(state.chains, key = { item -> item.id }) { item ->
                    ChainItem(item)
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
private fun AppBar(state: VoteChainConfigState) {
    ZashiSmallTopAppBar(
        title = stringResource(R.string.vote_chain_config_title),
        navigationAction = {
            ZashiTopAppBarBackNavigation(onBack = state.onBack)
        },
        regularActions = {
            TextButton(onClick = state.onAddCustom) {
                Text(
                    text = stringResource(R.string.vote_chain_config_add),
                    style = ZashiTypography.textSm,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary
                )
            }
        },
        colors = ZcashTheme.colors.topAppBarColors orDark
            ZcashTheme.colors.topAppBarColors.copyColors(
                containerColor = Color.Transparent
            )
    )
}

@Composable
private fun Intro() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.vote_chain_config_intro_title),
            style = ZashiTypography.textXl,
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
    Surface(
        shape = RoundedCornerShape(ZashiDimensions.Radius.radiusXl),
        color = ZashiColors.Surfaces.bgPrimary,
        border = BorderStroke(1.dp, ZashiColors.Surfaces.strokeSecondary)
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ZashiRadioButton(
                state = state.radioButtonState,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = state.fullUrl.getValue(),
                style = ZashiTypography.textXs,
                color = ZashiColors.Text.textTertiary,
                maxLines = 3,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            if (state.editButton != null && state.deleteButton != null) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = state.editButton.onClick) {
                        Text(
                            text = state.editButton.text.getValue(),
                            style = ZashiTypography.textSm,
                            fontWeight = FontWeight.SemiBold,
                            color = ZashiColors.Text.textPrimary
                        )
                    }
                    TextButton(onClick = state.deleteButton.onClick) {
                        Text(
                            text = state.deleteButton.text.getValue(),
                            style = ZashiTypography.textSm,
                            fontWeight = FontWeight.SemiBold,
                            color = ZashiColors.Utility.ErrorRed.utilityError700
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Editor(state: VoteChainConfigEditorState) {
    Surface(
        shape = RoundedCornerShape(ZashiDimensions.Radius.radiusXl),
        color = ZashiColors.Surfaces.bgSecondary,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(ZashiDimensions.Spacing.spacingLg),
            verticalArrangement = Arrangement.spacedBy(ZashiDimensions.Spacing.spacingMd)
        ) {
            Text(
                text = state.title.getValue(),
                style = ZashiTypography.textMd,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary
            )
            ZashiTextField(
                state = state.name,
                placeholder = {
                    Text(text = stringResource(R.string.vote_chain_config_name_label))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ZashiTextField(
                state = state.url,
                placeholder = {
                    Text(text = stringResource(R.string.vote_chain_config_url_label))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                ZashiButton(
                    state = state.cancelButton,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                ZashiButton(
                    state = state.saveButton,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@PreviewScreens
@Composable
private fun VoteChainConfigPreview() =
    ZcashTheme {
        VoteChainConfigView(
            state = VoteChainConfigState(
                chains = listOf(
                    VoteChainConfigItemState(
                        id = "default",
                        radioButtonState = RadioButtonState(
                            text = stringRes("Default"),
                            subtitle = stringRes("https://voting.valargroup.org/static-voting-config.json"),
                            isChecked = true,
                            onClick = {}
                        ),
                        fullUrl = stringRes(
                            "https://voting.valargroup.org/static-voting-config.json?checksum=sha256:abc"
                        ),
                        editButton = null,
                        deleteButton = null
                    ),
                    VoteChainConfigItemState(
                        id = "custom",
                        radioButtonState = RadioButtonState(
                            text = stringRes("Local test"),
                            subtitle = stringRes("https://example.com/static-voting-config.json"),
                            isChecked = false,
                            onClick = {}
                        ),
                        fullUrl = stringRes("https://example.com/static-voting-config.json"),
                        editButton = ButtonState(
                            text = stringRes("Edit"),
                            style = ButtonStyle.TERTIARY
                        ),
                        deleteButton = ButtonState(
                            text = stringRes("Delete"),
                            style = ButtonStyle.DESTRUCTIVE2
                        )
                    )
                ),
                editor = null,
                errorSheet = null,
                onBack = {},
                onAddCustom = {}
            )
        )
    }
