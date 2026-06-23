package co.electriccoin.zcash.ui.screen.transactionhistory

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.Itemizable
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.StyledStringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.orHiddenString
import co.electriccoin.zcash.ui.design.util.stringRes

@Composable
fun Activity(
    state: ActivityState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 12.dp)
) {
    LaunchedEffect(state.key) {
        state.onDisplayed()
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .clickable(
                        indication = ripple(),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = state.onClick,
                        role = Role.Button,
                    ).padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Image(
                    painter = painterResource(state.bigIcon),
                    contentDescription = null
                )
                if (state.smallIcon != null) {
                    Image(
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .offset(8.dp, 8.dp),
                        painter = painterResource(state.smallIcon),
                        contentDescription = null
                    )
                }
                if (state.isUnread) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(12.dp)
                                .border(2.dp, ZashiColors.Avatars.avatarProfileBorder, CircleShape)
                                .padding(2.dp)
                                .background(ZashiColors.Avatars.avatarBadgeBg, CircleShape)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row {
                    Text(
                        text = state.title.getValue(),
                        color = ZashiColors.Text.textPrimary,
                        style = ZashiTypography.textSm,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (state.isShielded) {
                        Spacer(Modifier.width(8.dp))
                        Image(
                            painter = painterResource(id = R.drawable.ic_transaction_private),
                            contentDescription = null
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))
                state.subtitle?.let {
                    Text(
                        text = it.getValue(),
                        color = ZashiColors.Text.textTertiary,
                        style = ZashiTypography.textSm,
                    )
                }
            }
            state.value?.let {
                Spacer(Modifier.width(16.dp))
                SelectionContainer {
                    Text(
                        text =
                            it orHiddenString
                                stringRes(co.electriccoin.zcash.ui.design.R.string.general_hideBalancesMost),
                        color = ZashiColors.Text.textPrimary,
                        style = ZashiTypography.textSm
                    )
                }
            }
        }
    }
}

data class ActivityState(
    override val key: Any,
    @field:DrawableRes val bigIcon: Int,
    @field:DrawableRes val smallIcon: Int?,
    val title: StringResource,
    val subtitle: StringResource?,
    val isShielded: Boolean,
    val value: StyledStringResource?,
    val isUnread: Boolean,
    val onClick: () -> Unit,
    val onDisplayed: () -> Unit
) : Itemizable {
    override val contentType: Any = "activity"
}

@PreviewScreens
@Composable
private fun TransactionPreview() =
    ZcashTheme {
        BlankSurface {
            Activity(
                state = ActivityStateFixture.new(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
