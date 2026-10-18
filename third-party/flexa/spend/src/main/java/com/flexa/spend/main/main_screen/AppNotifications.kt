package com.flexa.spend.main.main_screen

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flexa.core.entity.Notification
import com.flexa.core.theme.FlexaTheme
import com.flexa.spend.MockFactory
import com.flexa.spend.main.ui_utils.SpendAsyncImage

@Composable
fun AppNotification(
    modifier: Modifier = Modifier,
    appNotification: Notification,
    toUrl: (@ParameterName("url") String) -> Unit,
    onClose: (@ParameterName("notification") Notification) -> Unit
) {
    val previewMode = LocalInspectionMode.current
    ListItem(
        modifier = modifier,
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.onPrimary
        ),
        leadingContent = {
            if (!previewMode) {
                SpendAsyncImage(
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(34.dp)
                        .offset(y = 4.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    imageUrl = appNotification.iconUrl,
                    crossfadeDuration = 1000,
                )
            } else {
                Icon(
                    modifier = Modifier.size(40.dp).offset(y = 4.dp),
                    imageVector = Icons.AutoMirrored.Default.PlaylistPlay,
                    contentDescription = null
                )
            }
        },
        overlineContent = {
            Text(appNotification.title ?: "", style = MaterialTheme.typography.titleMedium)
        },
        headlineContent = {
            Text(appNotification.body ?: "", style = MaterialTheme.typography.bodyMedium)
        },
        supportingContent = {
            Column {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 14.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .5F)
                )
                TextButton(
                    modifier = Modifier.offset(x = (-12).dp),
                    content = {
                        Text(appNotification.action?.label ?: "")
                    },
                    onClick = { toUrl(appNotification.action?.url ?: "") }
                )
            }
        },
        trailingContent = {
            IconButton(
                modifier = Modifier.offset(x = 12.dp, y = (-12).dp),
                content = {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        contentDescription = null
                    )
                },
                onClick = { onClose(appNotification) }
            )
        }
    )
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun AppNotificationPreview() {
    FlexaTheme {
        AppNotification(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)),
            MockFactory.getAppNotification(),
            toUrl = {},
            onClose = {}
        )
    }
}