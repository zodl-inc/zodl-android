package co.electriccoin.zcash.ui.screen.connectledger.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiBadge
import co.electriccoin.zcash.ui.design.component.ZashiBadgeDefaults
import co.electriccoin.zcash.ui.design.component.ZashiButton
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

/**
 * The shell shared by the Ledger intro and the connect steps: frosted top bar that names the step
 * when there is one, wordmark, title and description, then [content], with the optional [callout]
 * and the [bottomButton] pushed to the bottom of the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LedgerStepLayout(
    navigationAction: @Composable () -> Unit,
    step: Int?,
    title: String,
    description: String,
    bottomButton: @Composable ColumnScope.() -> Unit,
    descriptionColor: Color = ZashiColors.Text.textTertiary,
    callout: (@Composable () -> Unit)? = null,
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
                content = step?.let { { LedgerStepTitle(step = it) } },
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
                    modifier = Modifier.height(40.dp),
                    painter = painterResource(R.drawable.ic_ledger_wordmark),
                    contentDescription = null,
                )
                Spacer(24.dp)
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
                    color = descriptionColor,
                )
                Spacer(24.dp)
                content()
                Spacer(24.dp)
                Spacer(1f)
                if (callout != null) {
                    callout()
                    Spacer(20.dp)
                }
                bottomButton()
            }
        }
    }
}

/**
 * The two-line title of a connect step's top bar: the flow's name over "Step N of
 * [LEDGER_STEP_COUNT]".
 */
@Composable
private fun LedgerStepTitle(step: Int) {
    Text(
        text = stringResource(R.string.ledger_flow_title),
        style = ZashiTypography.textSm,
        color = ZashiColors.Text.textTertiary,
    )
    Text(
        text = stringResource(R.string.ledger_flow_step, step, LEDGER_STEP_COUNT),
        style = ZashiTypography.textSm,
        color = ZashiColors.Text.textPrimary,
        fontWeight = FontWeight.Medium,
    )
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
 * A card listing what should be true before the user moves on, one [LedgerChecklistItem] per item.
 * Figma fills it in Gray 50, which [ZashiColors.Utility.Gray.utilityGray50] is by day.
 */
@Composable
internal fun LedgerChecklistCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ZashiColors.Utility.Gray.utilityGray50)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
    }
}

/**
 * One numbered line of a [LedgerChecklistCard].
 */
@Composable
internal fun LedgerChecklistItem(
    number: Int,
    text: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        LedgerNumberBadge(number = number)
        Spacer(12.dp)
        Text(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(top = 2.dp),
            text = text,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun LedgerCheckRow(
    title: String,
    subtitle: String? = null,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .background(ZashiColors.Surfaces.bgPrimary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(14.dp),
                painter = painterResource(R.drawable.ic_ledger_check),
                contentDescription = null,
                tint = ZashiColors.Text.textPrimary,
            )
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
 * One step of the intro's overview: its number, a title and what the step asks for.
 */
@Composable
internal fun LedgerNumberedRow(
    number: Int,
    title: String,
    subtitle: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        LedgerNumberBadge(number = number)
        Spacer(16.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
        }
    }
}

@Composable
private fun LedgerNumberBadge(number: Int) {
    Box(
        modifier =
            Modifier
                .size(24.dp)
                .background(ZashiColors.Surfaces.bgTertiary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            style = ZashiTypography.textXs.copy(fontSize = 10.sp, lineHeight = 16.sp),
            color = ZashiColors.Text.textTertiary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * The one thing on a step that matters most, drawn as the Figma info callout in the Hyper Blue
 * utility palette.
 */
@Composable
internal fun LedgerCallout(
    title: String,
    text: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ZashiColors.Utility.HyperBlue.utilityBlueDark50)
                .padding(16.dp),
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            painter = painterResource(R.drawable.ic_ledger_callout_alert),
            contentDescription = null,
            tint = ZashiColors.Utility.HyperBlue.utilityBlueDark600,
        )
        Spacer(12.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = ZashiTypography.textSm,
                color = ZashiColors.Utility.HyperBlue.utilityBlueDark600,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = text,
                style = ZashiTypography.textXs,
                color = ZashiColors.Utility.HyperBlue.utilityBlueDark600,
            )
        }
    }
}

internal const val LEDGER_STEP_COUNT = 4

@PreviewScreens
@Composable
private fun StepPreview() =
    ZcashTheme {
        LedgerStepLayout(
            navigationAction = { ZashiTopAppBarBackNavigation(onBack = {}) },
            step = 1,
            title = "Turn On and Unlock",
            description = "Turn on your Ledger and enter your PIN.",
            bottomButton = {
                ZashiButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Continue",
                    onClick = {},
                )
            },
            callout = {
                LedgerCallout(
                    title = "Don’t Open the Zcash App Yet",
                    text = "Pairing works best from the home screen.",
                )
            },
        ) {
            LedgerChecklistCard {
                LedgerChecklistItem(number = 1, text = "Your Ledger is unlocked and on its home screen")
                LedgerChecklistItem(number = 2, text = "Bluetooth is on for your phone")
            }
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
            description = "Pair your Ledger with Zodl over Bluetooth in 4 quick steps.",
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
                title = "Turn on and unlock",
                subtitle = "Enter your PIN and stay on the home screen.",
            )
        }
    }
