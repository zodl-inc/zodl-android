package com.flexa.identity.create_id

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalFocusManager
import com.flexa.identity.Keyboard
import com.flexa.identity.keyboardAsState

@Composable
fun KeyboardHandler() {
    val keyboardState by keyboardAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(keyboardState) {
        if (keyboardState == Keyboard.CLOSED)
            focusManager.clearFocus()
    }
}
