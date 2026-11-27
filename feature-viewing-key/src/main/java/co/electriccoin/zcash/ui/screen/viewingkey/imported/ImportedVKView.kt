package co.electriccoin.zcash.ui.screen.viewingkey.imported

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.compose.AccountFormHeader
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.zashiVerticalGradient
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.scaffoldPadding

@Composable
internal fun ImportedVKView(state: ImportedVKState) {
    BlankBgScaffold { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(zashiVerticalGradient(ZashiColors.Utility.SuccessGreen.utilitySuccess100))
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .scaffoldPadding(paddingValues)
            ) {
                Spacer(Modifier.height(TopAppBarDefaults.TopAppBarExpandedHeight + HEADER_TOP_SPACING))
                AccountFormHeader(
                    state = state.header,
                    headingStyle = ZashiTypography.header6,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                ZashiButton(
                    state = state.doneButton,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(ImportedVKTag.DONE_BUTTON)
                )
            }
        }
    }
}

/**
 * Figma keeps an empty top app bar area above the success header and pads the header 132 below the
 * list's top, so the header starts that far under a top-bar-height gap although the screen shows no bar.
 */
private val HEADER_TOP_SPACING = 132.dp

@PreviewScreens
@Composable
private fun ImportedPreview() =
    ZcashTheme {
        ImportedVKView(ImportedVKState.preview)
    }
