package co.electriccoin.zcash.ui.screen.signkeystonetransaction

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.ZashiButton
import co.electriccoin.zcash.ui.design.component.ZashiButtonDefaults
import co.electriccoin.zcash.ui.design.component.ZashiInScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignKeystoneTransactionBottomSheet(
    state: SignKeystoneTransactionBottomSheetState?,
    modifier: Modifier = Modifier,
) {
    ZashiInScreenModalBottomSheet(
        state = state,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_keystone_sign_reject),
                contentDescription = null
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.keystoneTransactionReject_title),
                style = ZashiTypography.header6,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.sign_keystone_transaction_bottom_sheet_subtitle),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textTertiary,
            )
            Spacer(Modifier.height(32.dp))
            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                state = it.positiveButton
            )
            Spacer(Modifier.height(8.dp))
            ZashiButton(
                modifier = Modifier.fillMaxWidth(),
                state = it.negativeButton,
                defaultPrimaryColors = ZashiButtonDefaults.destructive2Colors()
            )
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
        }
    }
}

data class SignKeystoneTransactionBottomSheetState(
    override val onBack: () -> Unit,
    val positiveButton: ButtonState,
    val negativeButton: ButtonState,
) : ModalBottomSheetState

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        SignKeystoneTransactionBottomSheet(
            state =
                SignKeystoneTransactionBottomSheetState(
                    onBack = {},
                    positiveButton = ButtonState(stringRes("Get Signature")),
                    negativeButton = ButtonState(stringRes("Reject")),
                )
        )
    }
