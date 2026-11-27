package com.flexa.identity

import android.content.Context
import android.content.ContextWrapper
import android.util.LayoutDirection
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.annotation.IntRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.viewinterop.AndroidView
import java.security.MessageDigest

@Composable
internal fun AppImage(
    modifier: Modifier = Modifier,
    @DrawableRes resource: Int,
    colorFilter: ColorFilter? = null
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                setImageResource(resource)
                setColorFilter(colorFilter?.asAndroidColorFilter())
            }
        },
        update = {
            it.setImageResource(resource)
            it.colorFilter = colorFilter?.asAndroidColorFilter()
        }
    )
}

fun Context.getActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

@Stable
internal fun Modifier.mirror(
    @IntRange(
        LayoutDirection.LTR.toLong(),
        LayoutDirection.RTL.toLong()
    ) direction: Int = LayoutDirection.RTL
): Modifier {
    return if (direction == LayoutDirection.LTR)
        this.scale(scaleX = -1f, scaleY = 1f)
    else
        this
}

@Stable
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.autofill(
    autofillTypes: List<AutofillType>,
    onFill: ((String) -> Unit),
    onFocusChanged: ((FocusState) -> Unit)? = null
) = composed {
    val autofill = LocalAutofill.current
    val autofillNode =
        AutofillNode(onFill = onFill, autofillTypes = autofillTypes)

    LocalAutofillTree.current += autofillNode
    this
        .onGloballyPositioned {
            autofillNode.boundingBox = it.boundsInWindow()
        }
        .onFocusChanged { focusState ->
            onFocusChanged?.invoke(focusState)
            autofill?.runCatching {
                if (focusState.isFocused) {
                    requestAutofillForNode(autofillNode)
                } else {
                    cancelAutofillForNode(autofillNode)
                }
            }
        }
}

fun String.toSha256() =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .fold("") { str, it -> str + "%02x".format(it) }
