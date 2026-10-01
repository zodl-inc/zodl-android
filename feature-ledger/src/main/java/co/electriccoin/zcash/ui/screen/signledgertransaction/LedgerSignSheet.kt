@file:Suppress("TooManyFunctions")

package co.electriccoin.zcash.ui.screen.signledgertransaction

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ledger.R
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.rememberScreenModalBottomSheetState
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerDeviceRow
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerErrorContent
import co.electriccoin.zcash.ui.screen.connectledger.common.LedgerWaitingIndicator

/**
 * Non-dismissable: neither a drag, a tap outside nor system back hides it; only Cancel Transaction
 * or the end of the session leaves it.
 *
 * Only the part that changes size animates: the slot between the sheet's top and Cancel Transaction
 * crossfades and resizes only when an issue replaces a waiting phase or the other way round. Between
 * waiting phases the header stays put and only the region under it resizes, which the slot follows
 * without a size animation of its own. A new status within the same kind is a text swap, so the
 * spinner keeps turning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LedgerSignSheet(state: LedgerSignSheetState?) {
    ZashiScreenModalBottomSheet(
        state = state,
        sheetGesturesEnabled = false,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        sheetState = rememberScreenModalBottomSheetState(confirmValueChange = { it != SheetValue.Hidden }),
        dragHandle = null,
    ) { sheetState, contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(LedgerSignTag.SHEET)
                    .padding(
                        top = 34.dp,
                        bottom = contentPadding.calculateBottomPadding()
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = sheetState.content,
                modifier = Modifier.weight(1f, false),
                contentKey = { it is LedgerSignContent.Issue },
                transitionSpec = {
                    if ((initialState is LedgerSignContent.Issue) == (targetState is LedgerSignContent.Issue)) {
                        ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
                    } else {
                        crossfade()
                    }
                },
                contentAlignment = Alignment.TopCenter,
                label = "LedgerSignContent",
            ) { content ->
                when (content) {
                    is LedgerSignContent.Issue -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            LedgerErrorContent(
                                state = content,
                                primaryModifier = Modifier.testTag(LedgerSignTag.PRIMARY_BTN),
                            )
                            if (content.primary != null) {
                                Spacer(STACKED_BUTTON_GAP.dp)
                            }
                        }
                    }

                    is LedgerSignContent.Waiting -> {
                        WaitingContent(content)
                    }
                }
            }
            ZashiButton(
                state = sheetState.cancelButton,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .testTag(LedgerSignTag.CANCEL_BTN),
                defaultPrimaryColors = ZashiButtonDefaults.destructive1Colors(),
            )
        }
    }
}

private fun crossfade() =
    ContentTransform(
        targetContentEnter = fadeIn(),
        initialContentExit = fadeOut(),
        sizeTransform = SizeTransform(clip = false),
    )

/**
 * The header, the same in every waiting phase apart from its body line, over the spinner and its
 * status or the device picker. Only the region under the header resizes when the phase changes
 * between the two.
 */
@Composable
private fun WaitingContent(content: LedgerSignContent.Waiting) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header(body = content.body)
        Spacer(24.dp)
        AnimatedContent(
            targetState = content,
            modifier = Modifier.weight(1f, false),
            contentKey = { it::class },
            transitionSpec = { crossfade() },
            contentAlignment = Alignment.TopCenter,
            label = "LedgerSignWaitingContent",
        ) { waiting ->
            when (waiting) {
                is LedgerSignContent.Progress -> {
                    ProgressContent(waiting)
                }

                is LedgerSignContent.Devices -> {
                    DevicesContent(waiting)
                }
            }
        }
    }
}

/**
 * Figma puts Cancel Transaction 44 dp under the status: its 32 dp gap plus the status frame's own
 * 12 dp bottom padding.
 */
@Composable
private fun ProgressContent(content: LedgerSignContent.Progress) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LedgerWaitingIndicator(title = content.status.getValue())
        Spacer(PROGRESS_CANCEL_GAP.dp)
    }
}

/**
 * The cards are buttons, not a radio group: a row tap connects straight away, so none is ever shown
 * picked and the cards keep their unselected look.
 */
@Composable
private fun DevicesContent(content: LedgerSignContent.Devices) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LedgerWaitingIndicator(
            title = content.title.getValue(),
            showSpinner = false,
        )
        Spacer(16.dp)
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            content.devices.forEachIndexed { index, device ->
                if (index != 0) {
                    Spacer(8.dp)
                }
                LedgerDeviceRow(
                    state = device,
                    testTag = LedgerSignTag.DEVICE_ROW_PREFIX + index,
                )
            }
        }
        Spacer(DEVICES_CANCEL_GAP.dp)
    }
}

@Composable
private fun ColumnScope.Header(body: StringResource) {
    Image(
        modifier = Modifier.size(44.dp),
        painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_item_ledger),
        contentDescription = null,
    )
    Spacer(12.dp)
    Text(
        text = stringResource(R.string.ledger_sign_title),
        style = ZashiTypography.textXl,
        fontWeight = FontWeight.SemiBold,
        color = ZashiColors.Text.textPrimary,
        textAlign = TextAlign.Center,
    )
    Spacer(4.dp)
    Text(
        modifier = Modifier.animateContentSize(),
        text = body.getValue(),
        style = ZashiTypography.textSm,
        color = ZashiColors.Text.textTertiary,
        textAlign = TextAlign.Center,
    )
}

private const val PROGRESS_CANCEL_GAP = 44

private const val DEVICES_CANCEL_GAP = 32

/**
 * An issue's button above Cancel Transaction keeps the 8 dp stacked-button gap.
 */
private const val STACKED_BUTTON_GAP = 8

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun ScanningPreview() =
    ZcashTheme {
        LedgerSignSheet(state = LedgerSignSheetState.previewScanning)
    }

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun OpeningZcashAppPreview() =
    ZcashTheme {
        LedgerSignSheet(state = LedgerSignSheetState.previewOpeningZcashApp)
    }

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun AwaitingReviewPreview() =
    ZcashTheme {
        LedgerSignSheet(state = LedgerSignSheetState.previewAwaitingReview)
    }

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun DevicesPreview() =
    ZcashTheme {
        LedgerSignSheet(state = LedgerSignSheetState.previewDevices)
    }

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreens
@Composable
private fun IssuePreview() =
    ZcashTheme {
        LedgerSignSheet(state = LedgerSignSheetState.previewIssue)
    }
