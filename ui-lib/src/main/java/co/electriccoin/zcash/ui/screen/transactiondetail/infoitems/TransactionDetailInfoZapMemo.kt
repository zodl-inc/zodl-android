package co.electriccoin.zcash.ui.screen.transactiondetail.infoitems

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.ZapAttestationMemo
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.Spacer
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.PressMorphDefaults
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.pressMorph
import co.electriccoin.zcash.ui.design.util.stringRes

/**
 * Renders a ZAP1 attestation memo as a typed event card rather than as the raw
 * `ZAP1:{type_hex}:{payload_hash}` marker.
 *
 * The card shows only what the memo already contains. No network call is made and no attempt is
 * made to resolve or verify the commitment. The card uses only the memo already decrypted
 * by the wallet; rendering it does not disclose that memo to an external service.
 */
@Composable
fun TransactionDetailZapMemo(
    state: TransactionDetailZapMemoState,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier =
            modifier
                .pressMorph(interactionSource, PressMorphDefaults.PRESSED_SCALE_SUBTLE)
                .clickable(
                    indication = ripple(),
                    interactionSource = interactionSource,
                    onClick = state.onClick,
                    role = Role.Button,
                ),
        shape = RoundedCornerShape(12.dp),
        color = ZashiColors.Surfaces.bgSecondary,
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = state.eventLabel.getValue(),
                style = ZashiTypography.textSm,
                color = ZashiColors.Text.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(2.dp)
            Text(
                text = state.protocolLabel.getValue(),
                style = ZashiTypography.textXs,
                color = ZashiColors.Text.textTertiary
            )
            Spacer(12.dp)
            ZashiHorizontalDivider()
            Spacer(12.dp)
            Text(
                text = stringRes(R.string.transactionDetail_zapAttestationHash).getValue(),
                style = ZashiTypography.textXs,
                color = ZashiColors.Text.textTertiary
            )
            Spacer(2.dp)
            SelectionContainer {
                Text(
                    text = state.payloadHash.getValue(),
                    style = ZashiTypography.textSm,
                    color = ZashiColors.Text.textPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

data class TransactionDetailZapMemoState(
    val eventLabel: StringResource,
    val protocolLabel: StringResource,
    val payloadHash: StringResource,
    val onClick: () -> Unit,
)

/**
 * Abbreviates the payload hash for display. The supplied copy action retains the original memo
 * in full, including its prefix and type, rather than copying the abbreviated display value.
 */
fun ZapAttestationMemo.toTransactionDetailZapMemoState(onClick: () -> Unit) =
    TransactionDetailZapMemoState(
        eventLabel = stringRes(eventLabel),
        protocolLabel =
            when (protocol) {
                ZapAttestationMemo.Protocol.ZAP1 -> {
                    stringRes(R.string.transactionDetail_zapAttestation)
                }

                ZapAttestationMemo.Protocol.NSM1 -> {
                    stringRes(R.string.transactionDetail_zapAttestationLegacy)
                }
            },
        payloadHash = stringRes(payloadHash.abbreviateMiddle()),
        onClick = onClick
    )

private fun String.abbreviateMiddle(): String =
    if (length <= ABBREVIATED_HASH_THRESHOLD) {
        this
    } else {
        "${take(ABBREVIATED_HASH_EDGE)}…${takeLast(ABBREVIATED_HASH_EDGE)}"
    }

private const val ABBREVIATED_HASH_THRESHOLD = 24
private const val ABBREVIATED_HASH_EDGE = 10

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            TransactionDetailZapMemo(
                modifier = Modifier.fillMaxWidth(),
                state =
                    TransactionDetailZapMemoState(
                        eventLabel = stringRes("Agent action"),
                        protocolLabel = stringRes(R.string.transactionDetail_zapAttestation),
                        payloadHash = stringRes("4f3a1c9d8b…c04f3a1c9d"),
                        onClick = {}
                    )
            )
        }
    }

@PreviewScreens
@Composable
private fun LegacyPreview() =
    ZcashTheme {
        BlankSurface {
            TransactionDetailZapMemo(
                modifier = Modifier.fillMaxWidth(),
                state =
                    TransactionDetailZapMemoState(
                        eventLabel = stringRes("Governance proposal"),
                        protocolLabel = stringRes(R.string.transactionDetail_zapAttestationLegacy),
                        payloadHash = stringRes("8b2e5a7c04…9d8b2e5a7c"),
                        onClick = {}
                    )
            )
        }
    }
