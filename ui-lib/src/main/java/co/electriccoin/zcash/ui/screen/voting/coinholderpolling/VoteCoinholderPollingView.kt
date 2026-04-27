package co.electriccoin.zcash.ui.screen.voting.coinholderpolling

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
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
fun VoteCoinholderPollingView(state: VoteCoinholderPollingState) {
    BackHandler { state.onBack() }
    BlankBgScaffold(
        topBar = { AppBar(state) },
        content = { padding ->
            if (state.activeRounds.isEmpty() && state.pastRounds.isEmpty()) {
                NoRoundsContent(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .scaffoldPadding(padding)
                )
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .scaffoldPadding(padding)
                ) {
                    if (state.activeRounds.isNotEmpty()) {
                        SectionHeader("Active")
                        state.activeRounds.forEach { round ->
                            PollCard(round)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                    if (state.pastRounds.isNotEmpty()) {
                        if (state.activeRounds.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        SectionHeader("Past Rounds")
                        state.pastRounds.forEach { round ->
                            PollCard(round)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun NoRoundsContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringRes("No Voting Rounds").getValue(),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringRes("There are no voting rounds available right now.").getValue(),
            style = ZashiTypography.textMd,
            color = ZashiColors.Text.textTertiary
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = ZashiTypography.textSm,
        color = ZashiColors.Text.textTertiary,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun PollCard(state: VotePollCardState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ZashiDimensions.Radius.radius2xl),
        color = ZashiColors.Surfaces.bgPrimary,
        shadowElevation = 1.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(ZashiDimensions.Spacing.spacingXl)
        ) {
            // Top row: status pill + date
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusPill(state.status)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = state.dateLabel.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title
            Text(
                text = state.title.getValue(),
                style = ZashiTypography.textMd,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold
            )

            // Voted indicator
            state.votedLabel?.let { label ->
                Spacer(modifier = Modifier.height(8.dp))
                VotedIndicator(label = label.getValue(), votedCount = state.votedCount, total = state.proposalCount)
            }

            // Description
            if (state.description.getValue().isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringRes("Poll Description").getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textTertiary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.description.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action button
            val (label, style) =
                when (state.status) {
                    VotePollCardStatus.ACTIVE -> "Enter Poll" to ButtonStyle.PRIMARY
                    VotePollCardStatus.VOTED -> "View My Votes" to ButtonStyle.PRIMARY
                    VotePollCardStatus.CLOSED -> "View Results" to ButtonStyle.TERTIARY
                }
            ZashiButton(
                state =
                    ButtonState(
                        text = stringRes(label),
                        style = style,
                        onClick = state.onAction
                    )
            )
        }
    }
}

@Composable
private fun StatusPill(status: VotePollCardStatus) {
    val (label, fg, bg) =
        when (status) {
            VotePollCardStatus.ACTIVE -> Triple("● Active", Color(0xFF15803D), Color(0xFFF0FDF4))
            VotePollCardStatus.VOTED -> Triple("✓ Voted", Color(0xFF15803D), Color(0xFFF0FDF4))
            VotePollCardStatus.CLOSED -> Triple("○ Closed", Color(0xFFB91C1C), Color(0xFFFEF2F2))
        }
    Surface(shape = CircleShape, color = bg) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun VotedIndicator(label: String, votedCount: Int, total: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textPrimary
        )
        Spacer(modifier = Modifier.weight(1f))
        Row {
            repeat(total) { index ->
                Surface(
                    shape = CircleShape,
                    color = if (index < votedCount) Color(0xFF22C55E) else ZashiColors.Surfaces.bgTertiary,
                    modifier =
                        Modifier
                            .padding(horizontal = 2.dp)
                            .size(8.dp)
                ) {}
            }
        }
    }
}

@Composable
private fun AppBar(state: VoteCoinholderPollingState) {
    ZashiSmallTopAppBar(
        title = "Coinholder Polling",
        navigationAction = {
            ZashiTopAppBarBackNavigation(
                onBack = state.onBack,
                modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
            )
        },
        colors =
            ZcashTheme.colors.topAppBarColors orDark
                ZcashTheme.colors.topAppBarColors.copyColors(
                    containerColor = Color.Transparent
                )
    )
}

// ─── Previews ────────────────────────────────────────────────────────────────

@PreviewScreens
@Composable
private fun CoinholderPollingPreview_WithRounds() =
    ZcashTheme {
        VoteCoinholderPollingView(
            state =
                VoteCoinholderPollingState(
                    activeRounds =
                        listOf(
                            VotePollCardState(
                                roundId = "abc123",
                                title = stringRes("ZF Grant Funding — Q3 2026"),
                                description = stringRes("Shielded vote on the allocation of Zcash Foundation grant funds for Q3 2026."),
                                status = VotePollCardStatus.ACTIVE,
                                dateLabel = stringRes("Closes May 15"),
                                votedLabel = null,
                                proposalCount = 2,
                                votedCount = 0,
                                onAction = {}
                            ),
                        ),
                    pastRounds =
                        listOf(
                            VotePollCardState(
                                roundId = "def456",
                                title = stringRes("ZF Grant Funding — Q2 2026"),
                                description = stringRes("Completed vote on Q2 2026 grant allocation."),
                                status = VotePollCardStatus.VOTED,
                                dateLabel = stringRes("Closed Apr 10"),
                                votedLabel = stringRes("2 of 2 voted"),
                                proposalCount = 2,
                                votedCount = 2,
                                onAction = {}
                            ),
                            VotePollCardState(
                                roundId = "ghi789",
                                title = stringRes("ZF Grant Funding — Q1 2026"),
                                description = stringRes(""),
                                status = VotePollCardStatus.CLOSED,
                                dateLabel = stringRes("Closed Jan 20"),
                                votedLabel = null,
                                proposalCount = 1,
                                votedCount = 0,
                                onAction = {}
                            ),
                        ),
                    onBack = {}
                )
        )
    }

@PreviewScreens
@Composable
private fun CoinholderPollingPreview_Empty() =
    ZcashTheme {
        VoteCoinholderPollingView(
            state =
                VoteCoinholderPollingState(
                    activeRounds = emptyList(),
                    pastRounds = emptyList(),
                    onBack = {}
                )
        )
    }
