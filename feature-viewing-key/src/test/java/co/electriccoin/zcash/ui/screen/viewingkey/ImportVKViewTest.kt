package co.electriccoin.zcash.ui.screen.viewingkey

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.screen.viewingkey.importvk.ImportVKState
import co.electriccoin.zcash.ui.screen.viewingkey.importvk.ImportVKTag
import co.electriccoin.zcash.ui.screen.viewingkey.importvk.ImportVKView
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The paste screen: Continue stays disabled until the field has text, and the QR button in the
 * field reports its click.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImportVKViewTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var text by mutableStateOf("")

    private var scans = 0

    @Test
    fun continueIsDisabledUntilTheFieldHasText() {
        show()

        composeTestRule.onNodeWithTag(ImportVKTag.CONTINUE_BUTTON).assertIsNotEnabled()

        composeTestRule.onNode(isTextField).performTextInput(KEY)

        assertEquals(KEY, text)
        composeTestRule.onNodeWithTag(ImportVKTag.CONTINUE_BUTTON).assertIsEnabled()
    }

    @Test
    fun theScanButtonReportsItsClick() {
        show()

        composeTestRule.onNodeWithTag(ImportVKTag.SCAN_BUTTON).performClick()

        assertEquals(1, scans)
    }

    private fun show() {
        composeTestRule.setContent {
            ZcashTheme {
                ImportVKView(
                    ImportVKState.create(
                        text = text,
                        isSubmitting = false,
                        onValueChange = { text = it },
                        onScanClick = { scans++ },
                        onContinueClick = {},
                        onBack = {},
                    )
                )
            }
        }
    }

    /** The key field, found by its editable text. */
    private val isTextField = SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)

    private companion object {
        const val KEY = "uview1fixture"
    }
}
