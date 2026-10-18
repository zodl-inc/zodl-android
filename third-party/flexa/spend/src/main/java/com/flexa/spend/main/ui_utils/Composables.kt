package com.flexa.spend.main.ui_utils

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.BitmapFactoryDecoder
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.flexa.core.shared.SelectedAsset
import com.flexa.spend.MockFactory
import com.flexa.spend.R
import com.flexa.spend.Spend
import com.flexa.spend.main.assets.AssetsViewModel

@Composable
internal fun SpendAsyncImage(
    modifier: Modifier,
    imageUrl: String?,
    crossfade: Boolean = true,
    crossfadeDuration: Int = 300,
    placeholder: Painter? = null,
) {
    AsyncImage(
        modifier = modifier,
        model = ImageRequest.Builder(LocalContext.current)
            .decoderFactory(BitmapFactoryDecoder.Factory())
            .decoderFactory(SvgDecoder.Factory())
            .data(imageUrl)
            .crossfade(crossfade)
            .crossfade(crossfadeDuration)
            .build(),
        contentDescription = null,
        placeholder = placeholder,
        error = painterResource(id = R.drawable.ic_flexa)
    )
}

@Composable
fun rememberSelectedAsset(): State<SelectedAsset?> {
    val previewMode = LocalInspectionMode.current
    return if (!previewMode) Spend.selectedAsset.collectAsStateWithLifecycle()
    else remember { mutableStateOf(MockFactory.getMockSelectedAsset()) }
}

@Composable
fun KeepScreenOn() {
    val currentView = LocalView.current
    DisposableEffect(Unit) {
        currentView.keepScreenOn = true
        onDispose {
            currentView.keepScreenOn = false
        }
    }
}

@Composable
fun BalanceRestrictionsDialog(
    modifier: Modifier = Modifier,
    viewModel: AssetsViewModel,
    onDismiss: () -> Unit
) {
    val selectedAsset by viewModel.selectedAssetBundle.collectAsStateWithLifecycle()
    AlertDialog(
        modifier = modifier,
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        shape = RoundedCornerShape(25.dp),
        icon = {
            Box {
                Icon(
                    imageVector = Icons.Rounded.HourglassTop,
                    contentDescription = null
                )
                Icon(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(AlertDialogDefaults.containerColor)
                        .align(Alignment.BottomEnd),
                    imageVector = Icons.Rounded.WatchLater,
                    contentDescription = null
                )
            }
        },
        title = {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "${stringResource(R.string.balance)} ${stringResource(R.string.updating)}...",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
            )
        },
        text = {
            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontSize = MaterialTheme.typography.bodyMedium.fontSize)) {
                        append(stringResource(R.string.balance_restrictions_copy1))
                        append(" ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(selectedAsset?.asset?.balanceBundle?.availableLabel ?: "")
                        }
                        append(" ")
                        append(stringResource(R.string.balance_restrictions_copy2))
                    }
                },
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) {
                Text(text = stringResource(android.R.string.ok))
            }
        }
    )
}
