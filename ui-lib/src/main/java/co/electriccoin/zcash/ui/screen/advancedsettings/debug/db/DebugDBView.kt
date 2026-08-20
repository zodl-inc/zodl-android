package co.electriccoin.zcash.ui.screen.advancedsettings.debug.db

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTextField
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.component.zashiFrostedHeader
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun DebugDBView(state: DebugDBState) {
    val hazeState = rememberZashiFrostState()
    BlankBgScaffold(
        topBar = {
            Toolbar(
                onBack = state.onBack,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostedHeader(hazeState)
            )
        }
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .zashiFrostSource(hazeState)
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .scaffoldPadding(paddingValues)
            ) {
                ZashiButton(
                    modifier = Modifier.align(Alignment.End),
                    state = state.execute,
                )

                Spacer(8.dp)
                Text(
                    "Query:",
                    color = ZashiColors.Text.textTertiary,
                    style = ZashiTypography.textXs,
                )
                Spacer(4.dp)
                ZashiTextField(
                    state = state.query,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "SELECT * FROM accounts",
                            style = ZashiTypography.textMd,
                            color = ZashiColors.Inputs.Default.text
                        )
                    },
                    singleLine = false,
                    minLines = 10
                )

                Spacer(8.dp)
                Text(
                    "Output:",
                    color = ZashiColors.Text.textTertiary,
                    style = ZashiTypography.textXs,
                )
                Spacer(4.dp)

                Surface(
                    Modifier.fillMaxWidth(),
                    color = ZashiColors.Surfaces.bgTertiary,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(16.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = state.output.getValue(),
                                style = ZashiTypography.textSm,
                                color = ZashiColors.Text.textPrimary,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }

                Spacer(24.dp)
            }
        }
    }
}

@Composable
private fun Toolbar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZashiSmallTopAppBar(
        title = "Query Database",
        modifier = modifier.testTag(DebugDBTag.DEBUG_DB_TOP_APP_BAR),
        showTitleLogo = true,
        navigationAction = {
            ZashiTopAppBarBackNavigation(onBack = onBack)
        },
        colors =
            ZcashTheme.colors.topAppBarColors.copyColors(
                containerColor = Color.Transparent
            ),
    )
}

@PreviewScreens
@Composable
private fun DebugDBPreview() =
    ZcashTheme {
        DebugDBView(
            state =
                DebugDBState(
                    query =
                        co.electriccoin.zcash.ui.design.component.TextFieldState(
                            value = stringRes("SELECT * FROM accounts"),
                            onValueChange = {}
                        ),
                    output = stringRes("id | name\n---\n1 | Account 1\n2 | Account 2"),
                    execute =
                        co.electriccoin.zcash.ui.design.component.ButtonState(
                            text = stringRes("Execute"),
                            onClick = {}
                        ),
                    onBack = {}
                ),
        )
    }

object DebugDBTag {
    const val DEBUG_DB_TOP_APP_BAR = "debug_db_top_app_bar"
}
