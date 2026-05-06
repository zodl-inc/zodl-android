package co.electriccoin.zcash.ui.screen.voting.signkeystone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.appbar.ZashiTopAppBarTags
import co.electriccoin.zcash.ui.design.component.BlankBgScaffold
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.CircularScreenProgressIndicator
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.ZashiSmallTopAppBar
import co.electriccoin.zcash.ui.design.component.ZashiTopAppBarBackNavigation
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiInScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.scaffoldPadding
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.SignKeystoneTransactionBottomSheet
import co.electriccoin.zcash.ui.screen.signkeystonetransaction.SignKeystoneTransactionView
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignKeystoneVotingScreen(args: SignKeystoneVotingArgs) {
    val vm = koinViewModel<SignKeystoneVotingVM> { parametersOf(args) }
    val state by vm.state.collectAsStateWithLifecycle()
    val bottomSheetState by vm.bottomSheetState.collectAsStateWithLifecycle()
    val skipBottomSheetState by vm.skipBottomSheetState.collectAsStateWithLifecycle()
    val isLoading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    BackHandler {
        if (state != null) {
            state?.onBack?.invoke()
        } else {
            vm.onScreenBack()
        }
    }

    when {
        state != null -> SignKeystoneTransactionView(requireNotNull(state))
        isLoading -> SignKeystoneVotingLoadingView(onBack = vm::onScreenBack)
        error != null -> SignKeystoneVotingErrorView(
            message = requireNotNull(error),
            onBack = vm::onScreenBack,
            onRetry = vm::onRetry
        )
    }

    SignKeystoneTransactionBottomSheet(state = bottomSheetState)
    SkipKeystoneBundlesBottomSheet(state = skipBottomSheetState)
}

@Serializable
data class SignKeystoneVotingArgs(
    val roundIdHex: String
)

data class SkipKeystoneBundlesBottomSheetState(
    override val onBack: () -> Unit,
    val message: StringResource,
    val skipButton: ButtonState,
    val cancelButton: ButtonState
) : ModalBottomSheetState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkipKeystoneBundlesBottomSheet(
    state: SkipKeystoneBundlesBottomSheetState?,
    modifier: Modifier = Modifier
) {
    ZashiInScreenModalBottomSheet(
        state = state,
        modifier = modifier
    ) { sheetState ->
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.sign_keystone_voting_skip_remaining_title),
                style = ZashiTypography.header6,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = sheetState.message.getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
            Spacer(Modifier.height(32.dp))
            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                state = sheetState.skipButton,
                defaultPrimaryColors = ZashiButtonDefaults.destructive2Colors()
            )
            Spacer(Modifier.height(8.dp))
            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                state = sheetState.cancelButton
            )
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
        }
    }
}

@Composable
private fun SignKeystoneVotingLoadingView(onBack: () -> Unit) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                title = stringResource(R.string.sign_keystone_voting_bar_title),
                navigationAction = {
                    ZashiTopAppBarBackNavigation(
                        onBack = onBack,
                        modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
                    )
                }
            )
        }
    ) { padding ->
        CircularScreenProgressIndicator(
            modifier = Modifier.scaffoldPadding(padding)
        )
    }
}

@Composable
private fun SignKeystoneVotingErrorView(
    message: StringResource,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    BlankBgScaffold(
        topBar = {
            ZashiSmallTopAppBar(
                title = stringResource(R.string.sign_keystone_voting_bar_title),
                navigationAction = {
                    ZashiTopAppBarBackNavigation(
                        onBack = onBack,
                        modifier = Modifier.testTag(ZashiTopAppBarTags.BACK)
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .scaffoldPadding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.sign_keystone_voting_error_title),
                style = ZashiTypography.header6,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = message.getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textSecondary
            )
            ZashiButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                state = ButtonState(
                    text = stringRes(R.string.vote_try_again),
                    onClick = onRetry
                )
            )
        }
    }
}
