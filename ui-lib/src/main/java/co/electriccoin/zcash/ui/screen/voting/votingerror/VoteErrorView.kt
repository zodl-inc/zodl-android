package co.electriccoin.zcash.ui.screen.voting.votingerror

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R as UiR
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.orDark
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes

object VotingErrorMapper {
    fun toUserFriendlyMessage(rawMessage: String): StringResource {
        val lower = rawMessage.lowercase()
        return when {
            lower.contains("nullifier") && lower.contains("spent") ->
                stringRes(UiR.string.vote_error_mapper_nullifier_spent)

            lower.contains("round") && (lower.contains("not active") || lower.contains("inactive") || lower.contains("closed")) ->
                stringRes(UiR.string.vote_error_mapper_round_closed)

            lower.contains("pir") || lower.contains("private information retrieval") ->
                stringRes(UiR.string.vote_error_mapper_pir_connection)

            lower.contains("insufficient") && lower.contains("fund") ->
                stringRes(UiR.string.vote_error_mapper_insufficient_funds)

            lower.contains("wallet") && lower.contains("sync") ->
                stringRes(UiR.string.vote_error_mapper_wallet_sync)

            lower.contains("network") || lower.contains("timeout") || lower.contains("connect") ->
                stringRes(UiR.string.vote_error_mapper_network)

            lower.contains("proof") || lower.contains("zkp") ->
                stringRes(UiR.string.vote_error_mapper_proof)

            lower.contains("config") || lower.contains("version") ->
                stringRes(UiR.string.vote_error_mapper_version)

            else -> rawMessage
                .takeIf { it.isNotBlank() }
                ?.let(::stringRes)
                ?: stringRes(UiR.string.vote_error_mapper_unknown)
        }
    }
}

@Composable
fun VoteErrorView(state: VoteErrorState) {
    BlankBgScaffold(
        topBar = { ErrorAppBar(onBack = state.onBack) },
        content = { padding ->
            VoteErrorContent(
                title = state.title,
                message = state.message,
                actionButton = state.actionButton,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .scaffoldPadding(padding)
            )
        }
    )
}

@Composable
fun VoteConfigErrorView(state: VoteConfigErrorState) {
    BlankBgScaffold(
        topBar = { ErrorAppBar(onBack = state.onBack) },
        content = { padding ->
            VoteErrorContent(
                title = stringRes(UiR.string.vote_error_config_title),
                message = state.message,
                actionButton = state.dismissButton,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .scaffoldPadding(padding)
            )
        }
    )
}

@Composable
internal fun VoteErrorContent(
    title: StringResource,
    message: StringResource,
    actionButton: ButtonState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title.getValue(),
            style = ZashiTypography.header6,
            color = ZashiColors.Text.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message.getValue(),
            style = ZashiTypography.textMd,
            color = ZashiColors.Text.textTertiary
        )
        Spacer(modifier = Modifier.weight(1f))
        ZashiButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = ZashiDimensions.Spacing.spacingMd),
            state = actionButton
        )
    }
}

@Composable
private fun ErrorAppBar(onBack: () -> Unit) {
    ZashiSmallTopAppBar(
        title = stringRes(UiR.string.vote_error_top_bar_title).getValue(),
        navigationAction = {
            ZashiTopAppBarBackNavigation(
                onBack = onBack,
                modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
            )
        },
        colors = ZcashTheme.colors.topAppBarColors orDark
            ZcashTheme.colors.topAppBarColors.copyColors(containerColor = Color.Transparent)
    )
}
