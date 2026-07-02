package co.electriccoin.zcash.ui.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R as DesignR
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography

@Composable
fun PrivacyDisclaimerCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    val warningBg = ZashiColors.Utility.WarningYellow.utilityOrange50
    val warningText = ZashiColors.Utility.WarningYellow.utilityOrange700
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(warningBg, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = ZashiTypography.textSm,
                fontWeight = FontWeight.SemiBold,
                color = warningText,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = ZashiTypography.textXs,
                color = warningText,
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            painter = painterResource(DesignR.drawable.ic_info),
            contentDescription = null,
            tint = warningText,
            modifier = Modifier.size(20.dp),
        )
    }
}
