package co.electriccoin.zcash.ui.screen.connectledger.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiBadge
import co.electriccoin.zcash.ui.design.component.ZashiBadgeDefaults
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiCard
import co.electriccoin.zcash.ui.design.component.ZashiMessage
import co.electriccoin.zcash.ui.design.component.ZashiMessageState
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.component.zashiFrostedHeader
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.withStyle

/**
 * The shell shared by the Ledger intro and the connect steps that are not the device picker:
 * frosted top bar, wordmark, optional step badge, title and description, then [content], with
 * [bottomButton] pushed to the bottom of the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LedgerStepLayout(
    navigationAction: @Composable () -> Unit,
    step: Int?,
    title: String,
    description: String,
    bottomButton: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hazeState = rememberZashiFrostState()
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostedHeader(hazeState),
                navigationAction = navigationAction,
                colors =
                    ZcashTheme.colors.topAppBarColors.copyColors(
                        containerColor = Color.Transparent
                    ),
            )
        }
    ) { padding ->
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
                        .scaffoldPadding(padding)
            ) {
                Image(
                    modifier = Modifier.height(32.dp),
                    painter = painterResource(R.drawable.ic_ledger_wordmark),
                    contentDescription = null,
                )
                Spacer(24.dp)
                if (step != null) {
                    LedgerStepBadge(step = step)
                    Spacer(12.dp)
                }
                Text(
                    text = title,
                    style = ZashiTypography.header6,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(8.dp)
                Text(
                    text = description,
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                )
                Spacer(24.dp)
                content()
                Spacer(24.dp)
                Spacer(1f)
                bottomButton()
            }
        }
    }
}

/**
 * The "Step N of [LEDGER_STEP_COUNT]" badge above a connect step's title.
 */
@Composable
internal fun LedgerStepBadge(step: Int) {
    ZashiBadge(
        text = stringResource(R.string.ledger_step_badge, step, LEDGER_STEP_COUNT),
        colors = ZashiBadgeDefaults.hyperBlueColors(),
    )
}

/**
 * A card listing what should be true before the user moves on, one [LedgerCheckRow] per item.
 */
@Composable
internal fun LedgerChecklistCard(content: @Composable ColumnScope.() -> Unit) {
    ZashiCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            content()
        }
    }
}

@Composable
internal fun LedgerCheckRow(
    title: String,
    subtitle: String? = null,
) {
    LedgerListRow(
        leading = {
            Icon(
                modifier = Modifier.size(14.dp),
                painter = painterResource(R.drawable.ic_ledger_check),
                contentDescription = null,
                tint = ZashiColors.Text.textPrimary,
            )
        },
        leadingBackground = ZashiColors.Surfaces.bgPrimary,
        title = title,
        subtitle = subtitle,
    )
}

@Composable
internal fun LedgerNumberedRow(
    number: Int,
    title: String,
    subtitle: String? = null,
) {
    LedgerListRow(
        leading = {
            Text(
                text = number.toString(),
                style = ZashiTypography.textXs,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        },
        leadingBackground = ZashiColors.Surfaces.bgSecondary,
        title = title,
        subtitle = subtitle,
    )
}

@Composable
private fun LedgerListRow(
    leading: @Composable () -> Unit,
    leadingBackground: Color,
    title: String,
    subtitle: String?,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .background(leadingBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            leading()
        }
        Spacer(12.dp)
        Column(modifier = Modifier.weight(1f)) {
            Spacer(2.dp)
            Text(
                text = title,
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Spacer(4.dp)
                Text(
                    text = subtitle,
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary,
                )
            }
        }
    }
}

/**
 * The one thing on a step that matters most, shown as an info message.
 */
@Composable
internal fun LedgerCallout(
    title: String,
    text: String,
) {
    ZashiMessage(
        ZashiMessageState(
            title = stringRes(title),
            text = stringRes(text).withStyle(),
            type = ZashiMessageState.Type.INFO,
        )
    )
}

internal const val LEDGER_STEP_COUNT = 4

@PreviewScreens
@Composable
private fun StepPreview() =
    ZcashTheme {
        LedgerStepLayout(
            navigationAction = { ZashiTopAppBarBackNavigation(onBack = {}) },
            step = 1,
            title = "Turn On and Unlock Your Ledger",
            description = "Turn your Ledger on and enter your PIN.",
            bottomButton = {
                ZashiButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Continue",
                    onClick = {},
                )
            },
        ) {
            LedgerChecklistCard {
                LedgerCheckRow(title = "Your Ledger is unlocked and on its home screen")
                LedgerCheckRow(title = "Bluetooth is on on your phone")
            }
            Spacer(16.dp)
            LedgerCallout(
                title = "Don't open the Zcash app yet",
                text = "Pairing works best from the home screen.",
            )
        }
    }

@PreviewScreens
@Composable
private fun NumberedListPreview() =
    ZcashTheme {
        LedgerStepLayout(
            navigationAction = { ZashiTopAppBarBackNavigation(onBack = {}) },
            step = null,
            title = "Connect Your Ledger",
            description = "Here's what we'll ask you to do:",
            bottomButton = {
                ZashiButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Get Started",
                    onClick = {},
                )
            },
        ) {
            LedgerNumberedRow(
                number = 1,
                title = "Turn on and unlock your Ledger.",
                subtitle = "Keep it on its home screen.",
            )
            Spacer(16.dp)
            LedgerNumberedRow(number = 2, title = "Pair it with your phone.")
        }
    }
