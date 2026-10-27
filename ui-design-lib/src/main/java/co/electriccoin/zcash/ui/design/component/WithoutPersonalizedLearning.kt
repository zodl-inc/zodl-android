package co.electriccoin.zcash.ui.design.component

import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/**
 * Asks the keyboard not to learn from or personalise on what is typed into [content], for fields
 * that take secrets, keys, addresses, amounts or memos. Compose's keyboard options cannot set
 * [EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING], so it is added to the editor info of every input
 * connection started below.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WithoutPersonalizedLearning(content: @Composable () -> Unit) {
    val interceptor =
        remember {
            PlatformTextInputInterceptor { request, nextHandler ->
                nextHandler.startInputMethod(
                    PlatformTextInputMethodRequest { outAttributes ->
                        request.createInputConnection(outAttributes).also {
                            outAttributes.imeOptions =
                                outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                        }
                    }
                )
            }
        }
    InterceptPlatformTextInput(interceptor = interceptor, content = content)
}
