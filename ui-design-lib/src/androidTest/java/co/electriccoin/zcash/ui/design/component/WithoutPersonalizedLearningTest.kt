package co.electriccoin.zcash.ui.design.component

import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.filters.MediumTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A field inside [WithoutPersonalizedLearning] asks the keyboard not to learn from what is typed,
 * through [EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING] on its input connection, as does every field
 * of a layout it wraps, such as a row of amount fields; a field outside it keeps the default options.
 */
class WithoutPersonalizedLearningTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @MediumTest
    fun aFocusedFieldAsksTheKeyboardNotToLearn() {
        val info = focusAndConnect { WithoutPersonalizedLearning { Field() } }

        assertEquals(
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
            info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        )
    }

    @Test
    @MediumTest
    fun everyFieldInAWrappedLayoutAsksTheKeyboardNotToLearn() {
        val info =
            focusAndConnect(fieldIndex = 1) {
                WithoutPersonalizedLearning {
                    Row {
                        Field()
                        Field()
                    }
                }
            }

        assertEquals(
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
            info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        )
    }

    @Test
    @MediumTest
    fun aFieldOutsideKeepsTheDefaultOptions() {
        val info = focusAndConnect { Field() }

        assertEquals(0, info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
    }

    /**
     * Shows [content], focuses its text field at [fieldIndex] and returns the editor info the focused
     * view fills in when the keyboard asks it for an input connection.
     */
    private fun focusAndConnect(
        fieldIndex: Int = 0,
        content: @Composable () -> Unit
    ): EditorInfo {
        var view: View? = null
        composeTestRule.setContent {
            view = LocalView.current
            content()
        }
        composeTestRule.onAllNodes(hasSetTextAction())[fieldIndex].performClick()
        composeTestRule.waitForIdle()

        val info = EditorInfo()
        composeTestRule.runOnUiThread {
            val focused = assertNotNull(assertNotNull(view).findFocus())
            assertNotNull(focused.onCreateInputConnection(info))
        }
        return info
    }

    @Composable
    private fun Field() {
        var text by remember { mutableStateOf("") }
        BasicTextField(value = text, onValueChange = { text = it })
    }
}
