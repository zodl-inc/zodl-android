@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.screen.chooseserver

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.component.EndpointTextFieldState
import co.electriccoin.zcash.ui.common.component.ZashiEndpointTextField
import co.electriccoin.zcash.ui.design.R.drawable
import co.electriccoin.zcash.ui.design.component.AlertDialogState
import co.electriccoin.zcash.ui.design.component.AppAlertDialog
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CircularScreenProgressIndicator
import co.electriccoin.zcash.ui.design.component.LottieProgress
import co.electriccoin.zcash.ui.design.component.RadioButtonCheckedContent
import co.electriccoin.zcash.ui.design.component.RadioButtonState
import co.electriccoin.zcash.ui.design.component.RadioButtonUncheckedContent
import co.electriccoin.zcash.ui.design.component.ZashiBadge
import co.electriccoin.zcash.ui.design.component.ZashiBadgeDefaults
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.component.ZashiInfoText
import co.electriccoin.zcash.ui.design.component.ZashiRadioButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTextFieldDefaults
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.orDark
import co.electriccoin.zcash.ui.design.util.stringRes
import java.util.UUID

@Composable
fun ChooseServerView(state: ChooseServerState?) {
    if (state == null) {
        CircularScreenProgressIndicator()
        return
    }

    BlankBgScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ChooseServerTopAppBar(
                onBack = state.onBack,
            )
        },
        bottomBar = {
            ChooseServerBottomBar(
                saveButtonState = state.saveButton
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(bottom = paddingValues.calculateBottomPadding()),
            contentPadding =
                PaddingValues(
                    top = paddingValues.calculateTopPadding() + ZcashTheme.dimens.spacingDefault,
                    bottom = ZcashTheme.dimens.spacingDefault,
                )
        ) {
            item(
                key = "connection_mode",
                contentType = "connection_mode"
            ) {
                ConnectionModeSection(state.connectionMode)
            }

            if (state.connectionMode.isManualSelected) {
                if (state.fastest.servers.isNotEmpty()) {
                    serverListItems(state.fastest)
                }

                serverListItems(state.other)
            }
        }

        if (state.dialogState != null) {
            ErrorDialog(dialogState = state.dialogState)
        }
    }
}

@Composable
private fun ConnectionModeSection(state: ServerConnectionModeState) {
    Column {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            ServerHeader(text = stringRes(R.string.choose_server_connection_mode))
            Spacer(modifier = Modifier.height(8.dp))
        }

        ZashiRadioButton(
            state = state.automatic,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .then(
                        if (state.automatic.isChecked) {
                            Modifier.background(ZashiColors.Surfaces.bgSecondary, RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        }
                    ),
            checkedContent = {
                if (state.automaticBadge != null) {
                    LottieProgress(size = 20.dp)
                } else {
                    RadioButtonCheckedContent(state.automatic)
                }
            },
            uncheckedContent = {
                if (!(state.automaticBadge != null && state.automatic.isChecked)) {
                    RadioButtonUncheckedContent(state.automatic)
                }
            },
            trailingContent = {
                if (state.automaticBadge != null && state.automatic.isChecked) {
                    ZashiBadge(
                        text = state.automaticBadge,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        colors = ZashiBadgeDefaults.warningColors()
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(4.dp))
        ZashiRadioButton(
            state = state.manual,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .then(
                        if (state.manual.isChecked) {
                            Modifier.background(ZashiColors.Surfaces.bgSecondary, RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        }
                    ),
            checkedContent = {
                if (state.automaticBadge != null) {
                    LottieProgress(size = 20.dp)
                } else {
                    RadioButtonCheckedContent(state.manual)
                }
            },
            uncheckedContent = {
                if (!(state.automaticBadge != null && state.isManualSelected)) {
                    RadioButtonUncheckedContent(state.manual)
                }
            },
            trailingContent = {
                if (state.automaticBadge != null && state.isManualSelected) {
                    ZashiBadge(
                        text = state.automaticBadge,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        colors = ZashiBadgeDefaults.warningColors()
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ErrorDialog(dialogState: ServerDialogState) {
    // TODO [#1276]: Once we ensure that the reason contains a localized message, we can leverage it for the UI prompt
    // TODO [#1276]: Consider adding support for a specific exception in AppAlertDialog
    // TODO [#1276]: https://github.com/Electric-Coin-Company/zashi-android/issues/1276

    when (dialogState) {
        is ServerDialogState.Validation -> {
            AppAlertDialog(
                title = dialogState.state.title.getValue(),
                text = {
                    Column(
                        Modifier.verticalScroll(rememberScrollState())
                    ) {
                        Text(dialogState.state.text.getValue())

                        if (dialogState.reason != null) {
                            Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingDefault))

                            Text(
                                text = dialogState.reason.getValue(),
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                },
                confirmButtonText =
                    dialogState.state.confirmButtonState
                        ?.text
                        ?.getValue(),
                onConfirmButtonClick = dialogState.state.confirmButtonState?.onClick
            )
        }
    }
}

@Composable
private fun MultiServerInfoFooter() {
    ZashiInfoText(
        text = stringResource(R.string.choose_server_multi_server_info),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Top,
    )
}

@Composable
fun ChooseServerBottomBar(saveButtonState: ButtonState) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(ZashiColors.Surfaces.bgPrimary)
    ) {
        MultiServerInfoFooter()
        ZashiHorizontalDivider()
        Spacer(modifier = Modifier.height(20.dp))
        ZashiButton(
            state = saveButtonState,
            modifier =
                Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(48.dp),
            content = { scope ->
                if (saveButtonState.isLoading) {
                    scope.Loading()
                    Spacer(modifier = Modifier.width(6.dp))
                }
                scope.Text()
            }
        )
        Spacer(modifier = Modifier.height(20.dp))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
    }
}

@Composable
private fun ChooseServerTopAppBar(
    onBack: () -> Unit
) {
    ZashiSmallTopAppBar(
        title = stringResource(id = R.string.choose_server_title),
        modifier = Modifier.testTag(CHOOSE_SERVER_TOP_APP_BAR),
        showTitleLogo = true,
        navigationAction = {
            ZashiTopAppBarBackNavigation(onBack = onBack)
        }
    )
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun LazyListScope.serverListItems(state: ServerListState) {
    item(
        key =
            when (state) {
                is ServerListState.Fastest -> "fastest_header"
                is ServerListState.Other -> "other_header"
            },
        contentType =
            when (state) {
                is ServerListState.Fastest -> "fastest_header"
                is ServerListState.Other -> "other_header"
            }
    ) {
        when (state) {
            is ServerListState.Fastest -> FastestServersHeader(state = state)
            is ServerListState.Other -> OtherServersHeader(state = state)
        }
    }

    itemsIndexed(
        items = state.servers,
        contentType = { _, item -> item.contentType },
        key = { _, item -> item.key }
    ) { index, item ->
        when (item) {
            is ServerState.Custom -> {
                CustomServerRadioButton(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp)
                            .then(
                                if (item.radioButtonState.isChecked) {
                                    Modifier.background(ZashiColors.Surfaces.bgSecondary, RoundedCornerShape(12.dp))
                                } else {
                                    Modifier
                                }
                            ),
                    state = item
                )
            }

            is ServerState.Default -> {
                ZashiRadioButton(
                    state = item.radioButtonState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .then(
                                if (item.radioButtonState.isChecked && item.badge == null) {
                                    Modifier.background(ZashiColors.Surfaces.bgSecondary, RoundedCornerShape(12.dp))
                                } else {
                                    Modifier
                                }
                            ),
                    checkedContent = {
                        if (item.badge == null) {
                            RadioButtonCheckedContent(item.radioButtonState)
                        } else {
                            Image(
                                painter =
                                    painterResource(
                                        id =
                                            if (isSystemInDarkTheme()) {
                                                drawable.ic_radio_button_checked_variant_dark
                                            } else {
                                                drawable.ic_radio_button_checked_variant
                                            }
                                    ),
                                contentDescription = item.radioButtonState.text.getValue(),
                            )
                        }
                    },
                    trailingContent = {
                        if (item.badge != null) {
                            ZashiBadge(
                                text = item.badge,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            )
                        }
                    }
                )
            }
        }

        if (index != state.servers.lastIndex) {
            Spacer(modifier = Modifier.height(4.dp))
            ZashiHorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))
        } else if (index == state.servers.lastIndex && state is ServerListState.Fastest) {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FastestServersHeader(state: ServerListState.Fastest) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            ServerHeader(text = state.title)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                enabled = state.retryButton.isEnabled,
                onClick = state.retryButton.onClick
            ) {
                if (state.isLoading) {
                    LottieProgress()
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_retry),
                        contentDescription = state.retryButton.text.getValue(),
                        colorFilter = ColorFilter.tint(ZashiColors.Text.textPrimary)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = state.retryButton.text.getValue(),
                    style = ZashiTypography.textSm,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun OtherServersHeader(state: ServerListState.Other) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        ServerHeader(text = state.title)
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun ServerHeader(text: StringResource) {
    Text(
        text = text.getValue(),
        style = ZashiTypography.textLg,
        fontWeight = FontWeight.SemiBold,
        color = ZashiColors.Text.textPrimary
    )
}

@Suppress("LongMethod")
@Composable
private fun CustomServerRadioButton(
    state: ServerState.Custom,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        ZashiRadioButton(
            state = state.radioButtonState,
            modifier = Modifier.fillMaxWidth(),
            isRippleEnabled = false,
            checkedContent = {
                if (state.badge == null) {
                    RadioButtonCheckedContent(state.radioButtonState)
                } else {
                    Image(
                        painter =
                            painterResource(
                                id =
                                    if (isSystemInDarkTheme()) {
                                        drawable.ic_radio_button_checked_variant_dark
                                    } else {
                                        drawable.ic_radio_button_checked_variant
                                    }
                            ),
                        contentDescription = state.radioButtonState.text.getValue(),
                    )
                }
            },
            trailingContent = {
                if (state.badge != null) {
                    ZashiBadge(
                        text = state.badge,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    )
                    Spacer(
                        modifier = Modifier.width(8.dp),
                    )
                }
                val iconAngle =
                    animateFloatAsState(
                        targetValue = if (state.isExpanded) 180f else 0f,
                        label = "iconAngle"
                    )
                Image(
                    modifier =
                        Modifier
                            .align(Alignment.CenterVertically)
                            .rotate(iconAngle.value),
                    painter = painterResource(id = R.drawable.ic_expand),
                    contentDescription = state.radioButtonState.text.getValue(),
                    colorFilter = ColorFilter.tint(ZashiColors.Text.textPrimary)
                )
            }
        )

        AnimatedVisibility(visible = state.isExpanded) {
            val focusManager = LocalFocusManager.current
            Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingSmall))
            ZashiEndpointTextField(
                state = state.newServerTextFieldState,
                placeholder = {
                    Text(text = stringResource(R.string.choose_server_textfield_hint))
                },
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(true) }),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 52.dp,
                            end = 20.dp,
                            bottom = 16.dp
                        ),
                colors =
                    ZashiTextFieldDefaults.defaultColors(
                        containerColor = ZashiColors.Surfaces.bgPrimary,
                        textColor = ZashiColors.Text.textPrimary,
                        borderColor = ZashiColors.Inputs.Default.stroke,
                    ) orDark
                        ZashiTextFieldDefaults.defaultColors(
                            containerColor = ZashiColors.Surfaces.bgSecondary,
                            hintColor = ZashiColors.Utility.Gray.utilityGray400,
                            textColor = ZashiColors.Text.textPrimary,
                            borderColor = ZashiColors.Utility.Gray.utilityGray600
                        )
            )
        }
    }
}

@Suppress("LongMethod", "MagicNumber")
@Composable
private fun ChooseServerPreview(
    showFastestServerLoading: Boolean = true,
    dialogState: ServerDialogState? = null
) {
    var selectionIndex by remember { mutableIntStateOf(5) }
    val fastestServers =
        ServerListState.Fastest(
            title = stringRes("Fastest Servers"),
            servers =
                if (showFastestServerLoading) {
                    (1..3).map {
                        ServerState.Default(
                            key = UUID.randomUUID().toString(),
                            RadioButtonState(
                                text = stringRes("Some Server"),
                                isChecked = selectionIndex == it,
                                onClick = {
                                    selectionIndex = it
                                },
                                subtitle = null,
                            ),
                            badge = null,
                        )
                    }
                } else {
                    listOf()
                },
            retryButton =
                ButtonState(
                    text = stringRes("Save Button"),
                    onClick = {},
                ),
            isLoading = true,
        )
    ChooseServerView(
        state =
            ChooseServerState(
                connectionMode =
                    ServerConnectionModeState(
                        automatic =
                            RadioButtonState(
                                text = stringRes("Automatic"),
                                isChecked = true,
                                onClick = {}
                            ),
                        manual =
                            RadioButtonState(
                                text = stringRes("Manual"),
                                isChecked = false,
                                onClick = {}
                            )
                    ),
                fastest = fastestServers,
                other =
                    ServerListState.Other(
                        title = stringRes("Other Servers"),
                        servers =
                            (4..<12).map {
                                if (it == 5) {
                                    ServerState.Custom(
                                        key = UUID.randomUUID().toString(),
                                        RadioButtonState(
                                            text = stringRes("Custom Server"),
                                            isChecked = selectionIndex == it,
                                            onClick = {
                                                selectionIndex = it
                                            }
                                        ),
                                        newServerTextFieldState =
                                            EndpointTextFieldState(
                                                onValueChange = { },
                                            ),
                                        badge = null,
                                        isExpanded = true
                                    )
                                } else {
                                    ServerState.Default(
                                        key = UUID.randomUUID().toString(),
                                        RadioButtonState(
                                            text = stringRes("Some Server"),
                                            isChecked = selectionIndex == it,
                                            onClick = {
                                                selectionIndex = it
                                            },
                                            subtitle = if (it == 6) stringRes("Default") else null,
                                        ),
                                        badge = if (it == 6) stringRes("Active") else null,
                                    )
                                }
                            }
                    ),
                saveButton =
                    ButtonState(
                        text = stringRes("Save Button"),
                        onClick = {},
                    ),
                dialogState = dialogState,
                onBack = {}
            ),
    )
}

@Suppress("UnusedPrivateMember")
@PreviewScreens
@Composable
private fun ChooseServerPreviewValidationDialog() =
    ZcashTheme {
        ChooseServerPreview(
            dialogState =
                ServerDialogState.Validation(
                    state =
                        AlertDialogState(
                            title = stringRes("title"),
                            text = stringRes("text"),
                        ),
                    reason = stringRes("reason")
                )
        )
    }

@Suppress("UnusedPrivateMember")
@PreviewScreens
@Composable
private fun ChooseServerPreviewLoading() =
    ZcashTheme {
        ChooseServerPreview(showFastestServerLoading = false)
    }

@Suppress("UnusedPrivateMember")
@PreviewScreens
@Composable
private fun ChooseServerPreviewData() =
    ZcashTheme {
        ChooseServerPreview()
    }

private const val CHOOSE_SERVER_TOP_APP_BAR = "choose_server_top_app_bar"
