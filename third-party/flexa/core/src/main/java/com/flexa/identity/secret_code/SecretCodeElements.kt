package com.flexa.identity.secret_code

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flexa.core.theme.FlexaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@ExperimentalComposeUiApi
@Composable
fun SecretCode(
    modifier: Modifier = Modifier,
    clickable: Boolean = true,
    length: Int = 6,
    value: String = "",
    onValueChanged: (String) -> Unit,
    onFulfilled: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val palette = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes

    TextField(
        value = TextFieldValue(
            text = value,
            selection = TextRange(value.length)
        ),
        textStyle = TextStyle(color = Color.Transparent),
        onValueChange = {
            if (it.text.length <= length) {
                if (it.text.all { c -> c in '0'..'9' }) {
                    onValueChanged(it.text)
                    if (it.text.length == length) onFulfilled.invoke(it.text)
                }
                if (it.text.length >= length) keyboard?.hide()
            }
        },
        modifier = Modifier
            .size(1.dp)
            .alpha(0F)
            .focusRequester(focusRequester),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword
        )
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(length) { times ->
            val strokeColor by animateColorAsState(
                targetValue = if (times == value.length) palette.tertiary else palette.outline,
                animationSpec = tween(durationMillis = 500), label = ""
            )
            val strokeSize by animateDpAsState(
                targetValue = if (times == value.length) 2.dp else 1.dp,
                animationSpec = tween(durationMillis = 500), label = ""
            )
            val shape = shapes.small
            InputItem(
                modifier = Modifier
                    .size(width = 45.dp, height = 60.dp)
                    .clip(shape)
                    .border(
                        border = BorderStroke(width = strokeSize, strokeColor),
                        shape = shape
                    )
                    .combinedClickable(
                        onClick = {
                            if (clickable) {
                                focusRequester.requestFocus()
                                keyboard?.show()
                            }
                        },
                        onLongClick = {
                            SecretCodeExtractor(
                                clipboardManager
                                    .getText()
                                    .toString(), 6
                            ).code?.let { code ->
                                onValueChanged(code)
                                keyboard?.hide()
                                onFulfilled.invoke(code)
                            }
                        },
                    ),
                value = value.getOrNull(times)?.toString() ?: "",
            )
            if (times != length - 1)
                Spacer(modifier = Modifier.weight(1F))
        }
    }
}

@Composable
fun InputItem(
    modifier: Modifier,
    value: String,
    isCursorVisible: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val (cursorSymbol, setCursorSymbol) = remember { mutableStateOf("") }

    LaunchedEffect(key1 = cursorSymbol, isCursorVisible) {
        if (isCursorVisible) {
            scope.launch {
                delay(350)
                setCursorSymbol(if (cursorSymbol.isEmpty()) "|" else "")
            }
        }
    }

    Box(
        modifier = modifier
    ) {
        AnimatedContent(
            modifier = Modifier.align(Alignment.Center),
            targetState = if (isCursorVisible) cursorSymbol else value,
            transitionSpec = {
                scaleIn(initialScale = .0F) togetherWith scaleOut(targetScale = .0F)
            }, label = ""
        ) { target ->
            Text(
                text = target,
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun SecretCodePreview() {
    FlexaTheme {
        Surface {
            SecretCode(
                modifier = Modifier.padding(16.dp),
                value = "123",
                onValueChanged = {},
                onFulfilled = {}
            )
        }
    }
}