package co.electriccoin.zcash.ui.screen.migration.notesplit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarCloseNavigation
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.common.LceRenderer
import co.electriccoin.zcash.ui.screen.common.WalletHeaderBadgeChrome
import co.electriccoin.zcash.ui.screen.common.WalletHeaderIcons
import co.electriccoin.zcash.ui.screen.common.WalletHeaderIconsState
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Serializable
data object MigrationNoteSplitArgs

@Composable
fun MigrationNoteSplitScreen() {
    val vm = koinViewModel<MigrationNoteSplitVM>()
    val state by vm.state.collectAsStateWithLifecycle()
    LceRenderer(state) { s ->
        BackHandler { s.onBack() }
        MigrationNoteSplitView(s)
    }
}

@Composable
fun MigrationNoteSplitView(state: MigrationNoteSplitState) {
    when (state.phase) {
        NoteSplitPhase.EXPLAINER -> ExplainerView(state)
        NoteSplitPhase.IN_PROGRESS -> InProgressView(state)
        NoteSplitPhase.COMPLETE -> SuccessView(state)
    }
}

@Composable
private fun ExplainerView(state: MigrationNoteSplitState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                navigationAction = { ZashiTopAppBarBackNavigation(onBack = state.onBack) },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .scaffoldPadding(padding),
        ) {
            WalletHeaderIcons(
                state = WalletHeaderIconsState(
                    isKeystone = state.isKeystone,
                    badgeIcon = R.drawable.ic_migration_coins_swap,
                )
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Split Your Wallet Funds",
                style = ZashiTypography.header6,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "This sends a transaction to yourself, breaking your balance into smaller notes. " +
                    "Each Ironwood migration transfer then settles independently — no waiting for change.",
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
            Spacer(Modifier.height(24.dp))
            DetailsCard(
                transactionId = state.transactionId,
                onCopyTransactionId = state.onCopyTransactionId,
                amount = state.splitAmount,
                fee = state.fee,
            )
            Spacer(Modifier.weight(1f))
            ZashiButton(
                state = ButtonState(text = stringRes("Confirm"), onClick = state.onContinue),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun InProgressView(state: MigrationNoteSplitState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                navigationAction = { ZashiTopAppBarCloseNavigation(onBack = state.onBack) },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .scaffoldPadding(padding),
        ) {
            WalletHeaderIcons(
                state = WalletHeaderIconsState(
                    isKeystone = state.isKeystone,
                    badgeIcon = R.drawable.ic_migration_loading,
                )
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Splitting Funds...",
                style = ZashiTypography.header6,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Splitting your balance into transfer-sized notes. This is a send-to-self — " +
                    "your ZEC stays in Orchard.",
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
            Spacer(Modifier.height(24.dp))
            DetailsCard(
                transactionId = state.transactionId,
                onCopyTransactionId = state.onCopyTransactionId,
                amount = state.splitAmount,
                fee = state.fee,
            )
            Spacer(Modifier.weight(1f))
            DisclaimerCard(
                title = "Transaction in Progress",
                body = "Keep your phone on and the app open until this step completes.",
            )
            Spacer(Modifier.height(24.dp))
            ZashiButton(
                state = ButtonState(
                    text = stringRes("Splitting Funds..."),
                    icon = R.drawable.ic_migration_loading,
                    isEnabled = false,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SuccessView(state: MigrationNoteSplitState) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                navigationAction = { ZashiTopAppBarCloseNavigation(onBack = state.onBack) },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .scaffoldPadding(padding),
        ) {
            WalletHeaderIcons(
                state = WalletHeaderIconsState(
                    isKeystone = state.isKeystone,
                    badgeIcon = R.drawable.ic_vote_check_verified_solid,
                    badgeChrome = WalletHeaderBadgeChrome.Success,
                )
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Split Confirmed!",
                style = ZashiTypography.header6,
                fontWeight = FontWeight.SemiBold,
                color = ZashiColors.Text.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Splitting your balance into transfer-sized notes. This is a send-to-self — " +
                    "your ZEC stays in Orchard.",
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
            Spacer(Modifier.height(24.dp))
            DetailsCard(
                transactionId = state.transactionId,
                onCopyTransactionId = state.onCopyTransactionId,
                amount = state.splitAmount,
                fee = state.fee,
            )
            Spacer(Modifier.weight(1f))
            ZashiButton(
                state = ButtonState(text = stringRes("Continue"), onClick = state.onContinue),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DetailsCard(
    transactionId: StringResource?,
    onCopyTransactionId: () -> Unit,
    amount: StringResource,
    fee: StringResource,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ZashiColors.Surfaces.bgSecondary),
    ) {
        DetailsRow(label = "Transaction ID") {
            Text(
                text = transactionId?.getValue() ?: "Pending",
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textPrimary,
            )
            if (transactionId != null) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = null,
                    tint = ZashiColors.Text.textPrimary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onCopyTransactionId),
                )
            }
        }
        DetailsDivider()
        DetailsRow(label = "Amount") {
            Text(
                text = amount.getValue(),
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textPrimary,
            )
        }
        DetailsDivider()
        DetailsRow(label = "Fee") {
            Text(
                text = fee.getValue(),
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textPrimary,
            )
        }
    }
}

@Composable
private fun DetailsRow(label: String, value: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary,
        )
        Row(verticalAlignment = Alignment.CenterVertically, content = value)
    }
}

@Composable
private fun DetailsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ZashiColors.Surfaces.bgPrimary),
    )
}

@Composable
private fun DisclaimerCard(title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ZashiColors.Surfaces.bgSecondary)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.Medium,
                color = ZashiColors.Text.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            painter = painterResource(co.electriccoin.zcash.ui.design.R.drawable.ic_info),
            contentDescription = null,
            tint = ZashiColors.Text.textTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@PreviewScreens
@Composable
private fun PreviewExplainer() = ZcashTheme {
    MigrationNoteSplitView(
        MigrationNoteSplitState(
            phase = NoteSplitPhase.EXPLAINER,
            isKeystone = false,
            splitAmount = stringRes("12.458 ZEC"),
            fee = stringRes("0.001 ZEC"),
            transactionId = null,
            onCopyTransactionId = {},
            onContinue = {},
            onBack = {},
        )
    )
}

@PreviewScreens
@Composable
private fun PreviewInProgress() = ZcashTheme {
    MigrationNoteSplitView(
        MigrationNoteSplitState(
            phase = NoteSplitPhase.IN_PROGRESS,
            isKeystone = false,
            splitAmount = stringRes("12.458 ZEC"),
            fee = stringRes("0.001 ZEC"),
            transactionId = null,
            onCopyTransactionId = {},
            onContinue = {},
            onBack = {},
        )
    )
}

@PreviewScreens
@Composable
private fun PreviewSuccess() = ZcashTheme {
    MigrationNoteSplitView(
        MigrationNoteSplitState(
            phase = NoteSplitPhase.COMPLETE,
            isKeystone = false,
            splitAmount = stringRes("12.458 ZEC"),
            fee = stringRes("0.001 ZEC"),
            transactionId = stringRes("e87f1…6f28b"),
            onCopyTransactionId = {},
            onContinue = {},
            onBack = {},
        )
    )
}
