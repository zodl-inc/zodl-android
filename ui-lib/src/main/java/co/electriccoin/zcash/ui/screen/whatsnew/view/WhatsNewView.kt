package co.electriccoin.zcash.ui.screen.whatsnew.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.SmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiBulletText
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.ZashiVersion
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.component.zashiFrostedHeader
import co.electriccoin.zcash.ui.design.theme.AppearanceMode
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.fixture.ChangelogFixture
import co.electriccoin.zcash.ui.fixture.VersionInfoFixture
import co.electriccoin.zcash.ui.screen.whatsnew.model.WhatsNewSectionState
import co.electriccoin.zcash.ui.screen.whatsnew.model.WhatsNewState
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter

@Composable
fun WhatsNewView(
    state: WhatsNewState,
    onBack: () -> Unit
) {
    val hazeState = rememberZashiFrostState()
    BlankBgScaffold(
        topBar = {
            AppBar(
                onBack = onBack,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostedHeader(hazeState)
            )
        },
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
                Row {
                    Text(
                        text = state.titleVersion.getValue(),
                        style = ZashiTypography.textXl,
                        color = ZashiColors.Text.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        modifier =
                            Modifier
                                .weight(1f)
                                .align(CenterVertically),
                        text = DateTimeFormatter.ISO_LOCAL_DATE.format(state.date.toJavaLocalDate()),
                        textAlign = TextAlign.End,
                        style = ZashiTypography.textSm,
                        fontWeight = FontWeight.SemiBold,
                        color = ZashiColors.Text.textPrimary,
                    )
                }

                Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacingXl))

                state.sections.forEach { section ->
                    SelectionContainer {
                        WhatsNewSection(section)
                    }
                    Spacer(modifier = Modifier.height(ZashiDimensions.Spacing.spacingXl))
                }

                Spacer(Modifier.weight(1f))

                ZashiVersion(
                    modifier = Modifier.fillMaxWidth(),
                    version = state.bottomVersion,
                    onLongClick = state.onVersionLongClick
                )
            }
        }
    }
}

@Composable
private fun WhatsNewSection(state: WhatsNewSectionState) {
    val bulletStyle =
        state.content
            .getValue()
            .split("\n-")
            .filter { it.isNotBlank() }
            .map {
                it.replace("\n-", "").trim()
            }

    Column {
        Text(
            text = state.title.getValue(),
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold,
            style = ZashiTypography.textMd,
        )

        Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingMin))

        ZashiBulletText(
            bulletStyle,
            modifier = Modifier.padding(start = ZashiDimensions.Spacing.spacingMd),
        )
    }
}

@Composable
private fun AppBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    SmallTopAppBar(
        modifier = modifier,
        titleText = stringResource(id = R.string.whats_new_title).uppercase(),
        navigationAction = {
            ZashiTopAppBarBackNavigation(onBack = onBack)
        },
        colors =
            ZcashTheme.colors.topAppBarColors.copyColors(
                containerColor = Color.Transparent
            ),
    )
}

@Composable
private fun WhatsNewViewPreview() {
    BlankSurface {
        WhatsNewView(
            state =
                WhatsNewState.new(
                    changelog = ChangelogFixture.new(),
                    version = VersionInfoFixture.new().versionName,
                    onVersionLongClick = {}
                ),
            onBack = {}
        )
    }
}

@Preview
@Composable
private fun WhatsNewViewPreviewLight() =
    ZcashTheme {
        WhatsNewViewPreview()
    }

@Preview
@Composable
private fun WhatsNewViewPreviewDark() =
    ZcashTheme(appearanceMode = AppearanceMode.DARK) {
        WhatsNewViewPreview()
    }
