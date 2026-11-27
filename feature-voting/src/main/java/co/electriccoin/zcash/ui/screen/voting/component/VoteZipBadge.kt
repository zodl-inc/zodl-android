package co.electriccoin.zcash.ui.screen.voting.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography

@Composable
fun ZipBadge(label: String) {
    Surface(
        color = ZashiColors.Utility.Gray.utilityGray100,
        shape = RoundedCornerShape(ZashiDimensions.Radius.radius2xl),
        border = BorderStroke(1.dp, ZashiColors.Utility.Gray.utilityGray200),
    ) {
        Text(
            text = label,
            style = ZashiTypography.textXs,
            color = ZashiColors.Utility.Gray.utilityGray700,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
