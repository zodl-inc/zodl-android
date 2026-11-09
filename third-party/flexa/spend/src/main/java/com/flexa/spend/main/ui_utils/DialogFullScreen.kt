package com.flexa.spend.main.ui_utils

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun FullScreenDialog(onClickOutside: () -> Unit, content: @Composable () -> Unit) {
    val animateTrigger = remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = {
                animateTrigger.value = false
                onClickOutside.invoke()
        },
        DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val localView = LocalView.current.parent
        SideEffect {
            if (localView is DialogWindowProvider) {
                localView.window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
            animateTrigger.value = true
        }
        AnimatedVisibility(
            visible = animateTrigger.value,
            enter = fadeIn() + scaleIn(initialScale = .95F) + slideInVertically(initialOffsetY = { it / 4 }),
            exit = scaleOut(targetScale = 1.3F) + fadeOut()
        ) {
            content()
        }
    }
}
