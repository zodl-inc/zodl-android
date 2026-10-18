package com.flexa.spend.main.settings_popup

import android.util.Log
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsPopup(
    modifier: Modifier = Modifier,
    viewModel: PopupViewModel,
    toPlaces: () -> Unit,
    toFlexaId: () -> Unit,
    toHowTo: () -> Unit,
    toReport: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier,
    ) {
        var visibility by remember { mutableStateOf(viewModel.opened) }
        val animDuration by remember { mutableStateOf(300) }
        val density = LocalDensity.current
        val transition = updateTransition(viewModel.opened, label = "open")
        val alpha by transition.animateFloat(label = "alpha") { if (it) 1F else 0F }
        val scale by transition.animateFloat(label = "scale") { if (it) 1F else .9F }
        val offset by transition.animateDp(label = "offset") { if (it) 0.dp else (-100).dp }
        LaunchedEffect(viewModel.opened) {
            if (!viewModel.opened) delay(animDuration.toLong())
            visibility = viewModel.opened
        }

        if (visibility)
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(
                    x = -(density.run { 10.dp.toPx() }.toInt()),
                    y = density.run { 100.dp.toPx() }.toInt()
                ),
                onDismissRequest = {
                    Log.d("TAG", "PopupExample: onDismissRequest")
                    scope.launch {
                        delay(100)
                        if (!viewModel.blockGesture) {
                            viewModel.opened = false
                            viewModel.blockGesture = false
                        }
                    }
                }
            ) {
                SettingsMenu(
                    modifier = Modifier
                        .alpha(alpha)
                        .scale(scale)
                        .offset(y = offset)
                        .padding(2.dp)
                        .width(230.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    toPlaces = {
                        viewModel.opened = false
                        toPlaces.invoke()
                    },
                    toFlexaId = {
                        viewModel.opened = false
                        toFlexaId.invoke()
                    },
                    toHowTo = {
                        viewModel.opened = false
                        toHowTo()
                    },
                    toReport = {
                        viewModel.opened = false
                        toReport()
                    }
                )
            }
    }
}
