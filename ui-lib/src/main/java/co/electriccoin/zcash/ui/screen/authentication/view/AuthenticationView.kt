package co.electriccoin.zcash.ui.screen.authentication.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.viewmodel.AuthenticationResult
import co.electriccoin.zcash.ui.design.component.AppAlertDialog
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.screen.authentication.view.AnimationConstants.WELCOME_ANIM_TEST_TAG

@Preview("App Access Authentication")
@Composable
private fun PreviewAppAccessAuthentication() {
    ZcashTheme(forceDarkMode = false) {
        BlankSurface {
            AppAccessAuthentication(
                onRetry = {},
                showAuthLogo = false,
                welcomeAnimVisibility = true,
            )
        }
    }
}

@Preview
@Composable
private fun ErrorAuthenticationPreview() {
    ZcashTheme(forceDarkMode = false) {
        BlankSurface {
            AuthenticationErrorDialog(
                onDismiss = {},
                onRetry = {},
                onSupport = {},
                reason = AuthenticationResult.Error(errorCode = -1, errorMessage = "Test Error Message")
            )
        }
    }
}

@Preview
@Composable
private fun ErrorAuthenticationDarkPreview() {
    ZcashTheme(forceDarkMode = true) {
        BlankSurface {
            AuthenticationErrorDialog(
                onDismiss = {},
                onRetry = {},
                onSupport = {},
                reason = AuthenticationResult.Error(errorCode = -1, errorMessage = "Test Error Message")
            )
        }
    }
}

@Composable
fun AppAccessAuthentication(
    onRetry: (() -> Unit),
    showAuthLogo: Boolean,
    welcomeAnimVisibility: Boolean,
    modifier: Modifier = Modifier,
) {
    WelcomeScreenView(
        animationState = welcomeAnimVisibility,
        onRetry = onRetry,
        showAuthLogo = showAuthLogo,
        modifier = modifier.testTag(WELCOME_ANIM_TEST_TAG),
    )
}

@Composable
fun AuthenticationErrorDialog(
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSupport: () -> Unit,
    reason: AuthenticationResult.Error
) {
    AppAlertDialog(
        title = stringResource(id = R.string.authentication_error_title),
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(id = R.string.authentication_error_text),
                    color = ZcashTheme.colors.textPrimary,
                )

                Spacer(modifier = Modifier.height(ZcashTheme.dimens.spacingDefault))

                Text(
                    text =
                        stringResource(
                            id = R.string.authentication_error_details,
                            reason.errorCode,
                            reason.errorMessage,
                        ),
                    fontStyle = FontStyle.Italic,
                    color = ZcashTheme.colors.textPrimary,
                )
            }
        },
        confirmButtonText = stringResource(id = R.string.coinVote_common_retry),
        onConfirmButtonClick = onRetry,
        dismissButtonText = stringResource(id = R.string.errorPage_action_contactSupport),
        onDismissButtonClick = onSupport,
        onDismissRequest = onDismiss,
    )
}

// Currently unused, we keep it for further iterations
@Composable
fun AuthenticationFailedDialog(
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSupport: () -> Unit
) {
    AppAlertDialog(
        title = stringResource(id = R.string.authentication_failed_title),
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(id = R.string.authentication_failed_text),
                    color = ZcashTheme.colors.textPrimary,
                )
            }
        },
        confirmButtonText = stringResource(id = R.string.coinVote_common_retry),
        onConfirmButtonClick = onRetry,
        dismissButtonText = stringResource(id = R.string.errorPage_action_contactSupport),
        onDismissButtonClick = onSupport,
        onDismissRequest = onDismiss,
    )
}
