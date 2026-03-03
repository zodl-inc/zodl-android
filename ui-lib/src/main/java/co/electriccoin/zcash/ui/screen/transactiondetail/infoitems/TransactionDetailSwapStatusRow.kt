package co.electriccoin.zcash.ui.screen.transactiondetail.infoitems

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.model.SwapStatus
import co.electriccoin.zcash.ui.common.model.SwapStatus.EXPIRED
import co.electriccoin.zcash.ui.common.model.SwapStatus.FAILED
import co.electriccoin.zcash.ui.common.model.SwapStatus.INCOMPLETE_DEPOSIT
import co.electriccoin.zcash.ui.common.model.SwapStatus.PENDING
import co.electriccoin.zcash.ui.common.model.SwapStatus.PROCESSING
import co.electriccoin.zcash.ui.common.model.SwapStatus.REFUNDED
import co.electriccoin.zcash.ui.common.model.SwapStatus.SUCCESS
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ShimmerRectangle
import co.electriccoin.zcash.ui.design.component.ZashiBadge
import co.electriccoin.zcash.ui.design.component.ZashiBadgeDefaults
import co.electriccoin.zcash.ui.design.component.heightDp
import co.electriccoin.zcash.ui.design.component.measureTextStyle
import co.electriccoin.zcash.ui.design.component.rememberZashiShimmer
import co.electriccoin.zcash.ui.design.component.widthDp
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailSwapStatusRowState.Mode.SWAP_FROM_ZEC
import co.electriccoin.zcash.ui.screen.transactiondetail.infoitems.TransactionDetailSwapStatusRowState.Mode.SWAP_INTO_ZEC
import com.valentinilk.shimmer.shimmer

@Composable
fun TransactionDetailSwapStatusRow(
    state: TransactionDetailSwapStatusRowState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
) {
    TransactionDetailRowSurface(
        onClick = null,
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        Text(
            modifier = Modifier.weight(1f),
            maxLines = 1,
            text = state.title.getValue(),
            style = ZashiTypography.textSm,
            color = ZashiColors.Text.textTertiary
        )
        if (state.status != null) {
            Spacer(Modifier.width(16.dp))
            StatusChip(state)
        } else {
            Spacer(Modifier.width(16.dp))
            Box(
                modifier = Modifier.shimmer(rememberZashiShimmer())
            ) {
                val textSize =
                    measureTextStyle(
                        style = ZcashTheme.extendedTypography.transactionItemStyles.contentMedium,
                        text = stringResource(R.string.swap_detail_completed),
                    )

                ShimmerRectangle(
                    width = textSize.size.widthDp + 4.dp + 4.dp + 1.dp,
                    height = textSize.size.heightDp + 4.dp + 4.dp + 1.dp,
                    color = ZashiColors.Surfaces.bgTertiary,
                    shape = CircleShape
                )
            }
        }
    }
}

@Suppress("CyclomaticComplexMethod")
@Composable
private fun StatusChip(state: TransactionDetailSwapStatusRowState) {
    if (state.status == null) return

    ZashiBadge(
        text =
            when (state.status) {
                EXPIRED -> stringResource(R.string.swap_detail_expired)
                INCOMPLETE_DEPOSIT -> stringResource(R.string.swap_detail_incomplete_deposit)
                PENDING ->
                    if (state.mode == SWAP_INTO_ZEC) {
                        stringResource(R.string.swap_detail_pending_deposit)
                    } else {
                        stringResource(R.string.swap_detail_pending)
                    }

                SUCCESS -> stringResource(R.string.swap_detail_completed)
                REFUNDED -> stringResource(R.string.swap_detail_refunded)
                FAILED -> stringResource(R.string.swap_detail_failed)
                PROCESSING ->
                    if (state.mode == SWAP_INTO_ZEC) {
                        stringResource(R.string.swap_detail_processing)
                    } else {
                        stringResource(R.string.swap_detail_pending)
                    }
            },
        colors =
            when (state.status) {
                PROCESSING -> ZashiBadgeDefaults.hyperBlueColors()
                INCOMPLETE_DEPOSIT,
                PENDING ->
                    if (state.mode == SWAP_INTO_ZEC) {
                        ZashiBadgeDefaults.warningColors()
                    } else {
                        ZashiBadgeDefaults.hyperBlueColors()
                    }

                SUCCESS -> ZashiBadgeDefaults.successColors()

                EXPIRED,
                REFUNDED,
                FAILED -> ZashiBadgeDefaults.errorColors()
            },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Immutable
data class TransactionDetailSwapStatusRowState(
    val title: StringResource,
    val status: SwapStatus?,
    val mode: Mode = SWAP_FROM_ZEC,
) {
    enum class Mode {
        SWAP_FROM_ZEC,
        SWAP_INTO_ZEC
    }
}

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            TransactionDetailSwapStatusRow(
                state =
                    TransactionDetailSwapStatusRowState(
                        title = stringRes("Title"),
                        status = SUCCESS,
                    )
            )
        }
    }

@PreviewScreens
@Composable
private fun LoadingPreview() =
    ZcashTheme {
        BlankSurface {
            TransactionDetailSwapStatusRow(
                state =
                    TransactionDetailSwapStatusRowState(
                        title = stringRes("Title"),
                        status = null,
                    )
            )
        }
    }
