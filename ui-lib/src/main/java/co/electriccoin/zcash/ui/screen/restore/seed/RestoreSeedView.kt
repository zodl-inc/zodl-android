@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.screen.restore.seed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.LocalKeyboardManager
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ChipButtonState
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.SeedTextFieldHandle
import co.electriccoin.zcash.ui.design.component.SeedTextFieldState
import co.electriccoin.zcash.ui.design.component.SeedWordInnerTextFieldState
import co.electriccoin.zcash.ui.design.component.SeedWordTextFieldState
import co.electriccoin.zcash.ui.design.component.TextSelection
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiChipButton
import co.electriccoin.zcash.ui.design.component.ZashiIconButton
import co.electriccoin.zcash.ui.design.component.ZashiSeedTextField
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.rememberSeedTextFieldHandle
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.orDark
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import java.util.Locale

@Composable
fun RestoreSeedView(
    state: RestoreSeedState,
    suggestionsState: RestoreSeedSuggestionsState
) {
    val handle = rememberSeedTextFieldHandle(state.seed)

    val focusManager = LocalFocusManager.current
    var wasKeyboardOpen by remember { mutableStateOf(false) }
    val keyboardManager = LocalKeyboardManager.current
    val isKeyboardOpen = keyboardManager.isOpen
    LaunchedEffect(isKeyboardOpen) {
        if (wasKeyboardOpen && !isKeyboardOpen) {
            focusManager.clearFocus(true)
        }
        wasKeyboardOpen = isKeyboardOpen
    }

    BlankBgScaffold(
        topBar = { AppBar(state) },
        bottomBar = { BottomBar(state, suggestionsState, handle) },
        content = { padding ->
            Content(
                state = state,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .scaffoldPadding(padding),
                handle = handle
            )
        }
    )
}

@Composable
private fun Content(
    state: RestoreSeedState,
    handle: SeedTextFieldHandle,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.restore_subtitle),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.restore_message),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary
        )
        Spacer(Modifier.height(20.dp))
        ZashiSeedTextField(
            state = state.seed,
            handle = handle,
            wordModifier = { Modifier.testTag(RestoreSeedTag.SEED_WORD_TEXT_FIELD) }
        )
        Spacer(Modifier.weight(1f))
        state.nextButton?.let {
            Spacer(Modifier.height(24.dp))
            ZashiButton(
                state = state.nextButton,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AppBar(state: RestoreSeedState) {
    ZashiSmallTopAppBar(
        title = stringResource(R.string.restore_title),
        navigationAction = {
            ZashiTopAppBarBackNavigation(
                onBack = state.onBack,
                modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
            )
        },
        regularActions = {
            ZashiIconButton(state.dialogButton, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(20.dp))
        },
        colors =
            ZcashTheme.colors.topAppBarColors orDark
                ZcashTheme.colors.topAppBarColors.copyColors(
                    containerColor = Color.Transparent
                ),
    )
}

@Suppress("ComplexCondition", "CyclomaticComplexMethod")
@Composable
private fun BottomBar(
    state: RestoreSeedState,
    suggestionsState: RestoreSeedSuggestionsState,
    handle: SeedTextFieldHandle,
    modifier: Modifier = Modifier,
) {
    val suggestions by remember(suggestionsState, handle) {
        derivedStateOf { getFilteredSuggestions(suggestionsState, handle) }
    }
    var previousIndex by remember { mutableIntStateOf(handle.selectedIndex) }

    LaunchedEffect(handle.selectedIndex, handle.selectedText) {
        if (
            suggestions.size == 1 &&
            previousIndex == handle.selectedIndex &&
            suggestions.contains(handle.selectedText)
        ) {
            val nextIndex =
                state.seed.values
                    .withIndex()
                    .indexOfFirst { (index, field) ->
                        index >= handle.selectedIndex && (field.innerState.value.isBlank() || field.isError)
                    }

            handle.setSelectedIndex(nextIndex)
        }
        previousIndex = handle.selectedIndex
    }

    if (suggestionsState.isVisible &&
        handle.selectedIndex >= 0 &&
        !handle.selectedText.isNullOrEmpty() &&
        !(suggestions.size == 1 && suggestions.contains(handle.selectedText))
    ) {
        if (suggestions.isEmpty()) {
            Warn(
                modifier = modifier
            )
        } else {
            Surface(
                Modifier.padding(top = 8.dp),
                color = ZashiColors.Surfaces.bgPrimary
            ) {
                LazyRow(
                    modifier =
                        Modifier
                            .testTag(RestoreSeedTag.AUTOCOMPLETE_LAYOUT)
                            .fillMaxWidth(),
                    contentPadding = PaddingValues(ZcashTheme.dimens.spacingSmall),
                    horizontalArrangement = spacedBy(6.dp)
                ) {
                    items(suggestions) {
                        ZashiChipButton(
                            state =
                                ChipButtonState(
                                    text = stringRes(it),
                                    onClick = {
                                        val selectedIndex = handle.selectedIndex
                                        if (selectedIndex >= 0) {
                                            state.seed.values[selectedIndex].onValueChange(
                                                SeedWordInnerTextFieldState(value = it, selection = TextSelection.End)
                                            )

                                            val nextIndex =
                                                state.seed.values
                                                    .withIndex()
                                                    .indexOfFirst { (index, field) ->
                                                        index >= selectedIndex &&
                                                            (field.innerState.value.isBlank() || field.isError)
                                                    }

                                            if (nextIndex != -1) {
                                                handle.setSelectedIndex(nextIndex)
                                            }
                                        }
                                    }
                                ),
                            modifier = Modifier.testTag(RestoreSeedTag.AUTOCOMPLETE_ITEM)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Warn(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(size = ZcashTheme.dimens.tinyRippleEffectCorner),
            color = ZcashTheme.colors.primaryColor,
            border =
                BorderStroke(
                    width = ZcashTheme.dimens.chipStroke,
                    color = ZcashTheme.colors.layoutStrokeSecondary
                ),
            shadowElevation = ZcashTheme.dimens.chipShadowElevation
        ) {
            Text(
                color = ZcashTheme.colors.textPrimary,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(ZcashTheme.dimens.spacingSmall),
                textAlign = TextAlign.Center,
                text = stringResource(R.string.restore_seed_warning_suggestions)
            )
        }
    }
}

private fun getFilteredSuggestions(
    suggestionsState: RestoreSeedSuggestionsState,
    handle: SeedTextFieldHandle,
): List<String> {
    val trimmed =
        handle.selectedText
            ?.lowercase(Locale.US)
            ?.trim()
            .orEmpty()
    val autocomplete = suggestionsState.suggestions.filter { it.startsWith(trimmed) }
    return when {
        trimmed.isBlank() -> suggestionsState.suggestions
        suggestionsState.suggestions.contains(trimmed) && autocomplete.size == 1 -> autocomplete
        else -> autocomplete
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        RestoreSeedView(
            state =
                RestoreSeedState(
                    seed =
                        SeedTextFieldState(
                            values =
                                (1..24).map {
                                    SeedWordTextFieldState(
                                        innerState =
                                            SeedWordInnerTextFieldState(
                                                value = "asd",
                                                selection = TextSelection.Start
                                            ),
                                        onValueChange = { },
                                        isError = false
                                    )
                                }
                        ),
                    onBack = {},
                    dialogButton = IconButtonState(R.drawable.ic_help) {},
                    nextButton =
                        ButtonState(
                            text = stringRes("Next"),
                            onClick = {}
                        )
                ),
            suggestionsState =
                RestoreSeedSuggestionsState(
                    isVisible = true,
                    suggestions = listOf("Word 1", "Word 2"),
                )
        )
    }
