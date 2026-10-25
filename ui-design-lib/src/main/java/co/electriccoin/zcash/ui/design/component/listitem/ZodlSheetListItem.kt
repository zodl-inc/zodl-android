package co.electriccoin.zcash.ui.design.component.listitem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.StyledStringResource
import co.electriccoin.zcash.ui.design.util.getValue

/**
 * One bordered row of a bottom sheet as Figma draws it: a 40 dp logo 12 dp from a Text SM Medium
 * title, the subtitle in Text XS right under it, and the trailing chevron. The logo and text sit
 * together in the content slot because [BaseListItem]'s leading slot is spaced for a 48 dp item.
 * The "Add Account" and "Wallet Options" sheets build their rows with it; the Wallets & Hardware
 * screen's wallet rows are [ZodlWalletListItem].
 *
 * The logo and the chevron are decorative and left out of the accessibility tree, so an
 * accessibility service reads the row as one button named by its title and subtitle, not the title
 * three times.
 */
@Composable
fun ZodlSheetListItem(
    icon: ImageResource,
    title: StringResource,
    subtitle: StyledStringResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseListItem(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        leading = null,
        content = {
            Row(
                modifier = it,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZashiListItemDefaults.LeadingItem(
                    modifier = Modifier.size(40.dp).clearAndSetSemantics { },
                    icon = icon,
                    badge = null,
                    contentDescription = title.getValue()
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = title.getValue(),
                        style = ZashiTypography.textSm,
                        fontWeight = FontWeight.Medium,
                        color = ZashiColors.Text.textPrimary,
                    )
                    Text(
                        text = subtitle.getValue(),
                        style = ZashiTypography.textXs,
                        color = ZashiColors.Text.textTertiary,
                    )
                }
            }
        },
        trailing = {
            ZashiListItemDefaults.TrailingItem(
                modifier = it.clearAndSetSemantics { },
                contentDescription = title.getValue()
            )
        },
        color = ZashiColors.Surfaces.bgPrimary,
        border = BorderStroke(1.dp, ZashiColors.Surfaces.strokeSecondary),
        onClick = onClick
    )
}
