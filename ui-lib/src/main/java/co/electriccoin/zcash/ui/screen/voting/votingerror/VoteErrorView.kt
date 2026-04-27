package co.electriccoin.zcash.ui.screen.voting.votingerror

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.orDark
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

// ─── VotingErrorMapper ────────────────────────────────────────────────────────

/**
 * Maps raw server/SDK error messages to user-friendly descriptions.
 * Mirrors iOS VotingErrorMapper.userFriendlyMessage().
 */
object VotingErrorMapper {
    fun toUserFriendlyMessage(rawMessage: String): String {
        val lower = rawMessage.lowercase()
        return when {
            lower.contains("nullifier") && lower.contains("spent") -> {
                "Your vote authorization has already been used. Each wallet can only vote once per round."
            }

            lower.contains("round") && (lower.contains("not active") || lower.contains("inactive") || lower.contains("closed")) -> {
                "This voting round is no longer active. The poll may have closed while you were voting."
            }

            lower.contains("pir") || lower.contains("private information retrieval") -> {
                "Could not connect to the privacy-preserving vote server. Please check your connection and try again."
            }

            lower.contains("insufficient") && lower.contains("fund") -> {
                "Insufficient funds to authorize your vote. A small fee is required."
            }

            lower.contains("wallet") && lower.contains("sync") -> {
                "Your wallet is not fully synced. Please wait for syncing to complete before voting."
            }

            lower.contains("network") || lower.contains("timeout") || lower.contains("connect") -> {
                "Network error. Please check your connection and try again."
            }

            lower.contains("proof") || lower.contains("zkp") -> {
                "Vote proof generation failed. Please try again."
            }

            lower.contains("config") || lower.contains("version") -> {
                "Your app may need an update to participate in this voting round."
            }

            else -> {
                rawMessage.ifBlank { "An unexpected error occurred. Please try again." }
            }
        }
    }
}

@Composable
fun VoteErrorView(state: VoteErrorState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                title = "Governance",
                navigationAction = {
                    ZashiTopAppBarBackNavigation(
                        onBack = state.onBack,
                        modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
                    )
                },
                colors =
                    ZcashTheme.colors.topAppBarColors orDark
                        ZcashTheme.colors.topAppBarColors.copyColors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent
                        )
            )
        },
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .scaffoldPadding(padding)
            ) {
                Text(
                    text = state.title.getValue(),
                    style = ZashiTypography.header6,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(12.dp)
                Text(
                    text = state.message.getValue(),
                    style = ZashiTypography.textMd,
                    color = ZashiColors.Text.textTertiary
                )
                Spacer(1f)
                ZashiButton(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = ZashiDimensions.Spacing.spacingMd),
                    state = state.actionButton
                )
            }
        }
    )
}

@Composable
fun VoteConfigErrorView(state: VoteConfigErrorState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                title = "Governance",
                navigationAction = {
                    ZashiTopAppBarBackNavigation(
                        onBack = state.onBack,
                        modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
                    )
                },
                colors =
                    ZcashTheme.colors.topAppBarColors orDark
                        ZcashTheme.colors.topAppBarColors.copyColors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent
                        )
            )
        },
        content = { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .scaffoldPadding(padding)
            ) {
                Text(
                    text = stringRes("Wallet Update Required").getValue(),
                    style = ZashiTypography.header6,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(12.dp)
                Text(
                    text = state.message.getValue(),
                    style = ZashiTypography.textMd,
                    color = ZashiColors.Text.textTertiary
                )
                Spacer(1f)
                ZashiButton(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = ZashiDimensions.Spacing.spacingMd),
                    state = state.dismissButton
                )
            }
        }
    )
}
