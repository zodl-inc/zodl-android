package co.electriccoin.zcash.ui.design.component.listitem.checkbox

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ZashiCheckboxIndicator
import co.electriccoin.zcash.ui.design.component.listitem.BaseListItem
import co.electriccoin.zcash.ui.design.component.listitem.ZashiListItemDefaults
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.Compose
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import kotlinx.collections.immutable.persistentListOf

@Composable
fun ZashiCheckboxListItem(
    state: ZashiCheckboxListItemState,
    modifier: Modifier = Modifier
) {
    BaseListItem(
        modifier = modifier,
        contentPadding = ZashiListItemDefaults.contentPadding,
        leading = {
            Box(
                modifier = it,
                contentAlignment = Alignment.Center
            ) {
                when (state.icon) {
                    is ImageResource.ByDrawable -> {
                        Image(
                            modifier = Modifier.sizeIn(maxWidth = 48.dp, maxHeight = 48.dp),
                            painter = painterResource(state.icon.resource),
                            contentDescription = null,
                        )
                    }

                    is ImageResource.Layered -> {
                        state.icon.Compose(modifier = Modifier.size(40.dp))
                    }

                    is ImageResource.DisplayString -> {
                        Text(
                            modifier =
                                Modifier
                                    .background(ZashiColors.Surfaces.bgSecondary, CircleShape)
                                    .size(40.dp)
                                    .padding(top = 11.dp)
                                    .align(Alignment.Center),
                            text = state.icon.value,
                            style = ZashiTypography.textSm,
                            color = ZashiColors.Text.textTertiary,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    ImageResource.Loading -> {
                        // do nothing
                    }
                }
            }
        },
        content = {
            ZashiListItemDefaults.ContentItem(
                modifier = it,
                text = state.title.getValue(),
                subtitle = state.subtitle.getValue().let(::AnnotatedString),
                titleIcons = persistentListOf(),
                isEnabled = true
            )
        },
        trailing = {
            ZashiCheckboxIndicator(state.isSelected)
        },
        onClick = state.onClick,
    )
}

@Composable
@PreviewScreens
private fun PreviewChecked() =
    ZcashTheme {
        BlankSurface {
            ZashiCheckboxListItem(
                modifier = Modifier.fillMaxWidth(),
                state =
                    ZashiCheckboxListItemState(
                        title = stringRes("title"),
                        subtitle = stringRes("subtitle"),
                        icon = imageRes("1"),
                        isSelected = true,
                        onClick = {}
                    )
            )
        }
    }

@Composable
@PreviewScreens
private fun PreviewUnchecked() =
    ZcashTheme {
        BlankSurface {
            ZashiCheckboxListItem(
                modifier = Modifier.fillMaxWidth(),
                state =
                    ZashiCheckboxListItemState(
                        title = stringRes("title"),
                        subtitle = stringRes("subtitle"),
                        icon = imageRes("1"),
                        isSelected = false,
                        onClick = {}
                    )
            )
        }
    }

data class ZashiCheckboxListItemState(
    val title: StringResource,
    val subtitle: StringResource,
    val icon: ImageResource,
    val isSelected: Boolean,
    val onClick: () -> Unit
) : CheckboxListItemState
