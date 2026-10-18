package com.flexa.spend.main.settings_popup

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.ShoppingCartCheckout
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.flexa.core.theme.FlexaTheme
import com.flexa.spend.R

@Composable
fun SettingsMenu(
    modifier: Modifier = Modifier,
    toPlaces: () -> Unit,
    toFlexaId: () -> Unit,
    toHowTo: () -> Unit,
    toReport: () -> Unit,
) {
    val rowHeight by remember { mutableStateOf(52.dp) }
    val palette = MaterialTheme.colorScheme
    val containerColor = palette.surface
    val onSurfaceVariant = palette.onSurfaceVariant
    val onSurface = palette.onSurface
    Column(modifier = modifier) {
        val paddingStart by remember { mutableStateOf(34.dp) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .background(containerColor)
                .clickable { toPlaces.invoke() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(paddingStart))
            Text(
                text = stringResource(id = R.string.find_places_to_pay),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W600,
                    color = onSurface
                )
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                modifier = Modifier.size(26.dp),
                imageVector = Icons.Rounded.Map,
                tint = onSurfaceVariant,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(14.dp))
        }
        HorizontalDivider(thickness = 1.dp, color = palette.onSurfaceVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .background(color = containerColor)
                .clickable { toFlexaId.invoke() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(paddingStart))
            Text(
                text = stringResource(id = R.string.manage_id),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W600,
                    color = onSurface
                )
            )
            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .background(color = Color.Gray)
            )
            Icon(
                modifier = Modifier.size(26.dp),
                imageVector = Icons.Rounded.Tune,
                tint = onSurfaceVariant,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(14.dp))
        }
        HorizontalDivider(thickness = 1.dp, color = palette.onSurfaceVariant)
        val previewMode = LocalInspectionMode.current
        var openHelp by rememberSaveable { mutableStateOf(previewMode) }
        val transition = updateTransition(openHelp, label = "open")
        val helpAngle by transition.animateFloat(label = "help icon angle") { if (it) 90F else 0F }
        Row(
            modifier = Modifier
                .zIndex(.1F)
                .fillMaxWidth()
                .height(rowHeight)
                .background(color = containerColor)
                .clickable { openHelp = !openHelp },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier
                    .size(paddingStart)
                    .padding(5.dp)
                    .rotate(helpAngle),
                imageVector = Icons.Rounded.ChevronRight,
                tint = onSurfaceVariant,
                contentDescription = null
            )
            Text(
                text = stringResource(id = R.string.help),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W600,
                    color = onSurface
                )
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                modifier = Modifier.size(26.dp),
                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                tint = onSurfaceVariant,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(14.dp))
        }
        AnimatedVisibility(
            visible = openHelp,
            enter = slideInVertically(animationSpec = tween(100)) + fadeIn(tween(100)),
            exit = slideOutVertically(animationSpec = tween(100)) + fadeOut(tween(100))
        ) {
            Column(
                modifier = Modifier
                    .background(containerColor)
            ) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = palette.onSurfaceVariant.copy(alpha = .5F)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .background(containerColor)
                        .clickable { toHowTo() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(paddingStart))
                    Text(
                        text = stringResource(id = R.string.how_to_pay),
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W600,
                            color = onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        modifier = Modifier.size(26.dp),
                        imageVector = Icons.Outlined.ShoppingCartCheckout,
                        tint = onSurfaceVariant,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = paddingStart),
                    thickness = 1.dp,
                    color = palette.onSurfaceVariant.copy(alpha = .5F)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .background(containerColor)
                        .clickable { toReport() }
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(paddingStart))
                    Text(
                        text = stringResource(id = R.string.report_an_issue),
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W600,
                            color = onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        modifier = Modifier.size(26.dp),
                        imageVector = Icons.Outlined.Feedback,
                        tint = onSurfaceVariant,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFB9B9B9)
@Composable
private fun SettingsMenuPreview() {
    FlexaTheme {
        SettingsMenu(
            modifier = Modifier
                .width(230.dp)
                .clip(RoundedCornerShape(16.dp)),
            {}, {}, {}, {}
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsMenuPreviewDark() {
    SettingsMenuPreview()
}
