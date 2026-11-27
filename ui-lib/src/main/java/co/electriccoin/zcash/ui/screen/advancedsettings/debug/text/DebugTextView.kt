package co.electriccoin.zcash.ui.screen.advancedsettings.debug.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiFrostedSheetHeader
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugTextView(
    state: DebugTextState?,
) {
    ZashiScreenModalBottomSheet(
        state = state,
        dragHandle = null,
        content = { state, contentPadding ->
            val hazeState = rememberZashiFrostState()
            var headerHeight by remember { mutableStateOf(0.dp) }
            Box(modifier = Modifier.weight(1f, false)) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .zashiFrostSource(hazeState)
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = 24.dp,
                                end = 24.dp,
                                top = headerHeight,
                                bottom = contentPadding.calculateBottomPadding()
                            )
                ) {
                    SelectionContainer {
                        Text(
                            text = state.text.getValue(),
                            style = ZashiTypography.textMd,
                            color = ZashiColors.Text.textPrimary
                        )
                    }
                    Spacer(24.dp)
                }

                ZashiFrostedSheetHeader(
                    hazeState = hazeState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    onHeightChanged = { headerHeight = it },
                    title = {
                        Text(
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                            text = state.title.getValue(),
                            color = ZashiColors.Text.textPrimary,
                            style = ZashiTypography.textXl,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        DebugTextView(
            DebugTextState(
                title = stringRes("Title"),
                text = stringRes("Text"),
                onBack = {},
            )
        )
    }
