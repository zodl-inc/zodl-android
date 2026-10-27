package co.electriccoin.zcash.ui.design.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.stringRes
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test

/**
 * A pasted viewing key is shown cut to its first and last eight characters around an ellipsis, as an
 * address is, until the field takes focus and shows all of it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ZodlViewingKeyTextFieldTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun anUnfocusedKeyIsCutToItsHeadAndTail() {
        setContent()

        composeTestRule.onNode(hasSetTextAction()).assertTextEquals("uview1tg...bcdefghi")
    }

    @Test
    fun aFocusedKeyIsShownWhole() {
        setContent()

        composeTestRule.onNode(hasSetTextAction()).performClick()

        composeTestRule.onNode(hasSetTextAction()).assertTextEquals(KEY)
    }

    private fun setContent() {
        composeTestRule.setContent {
            ZcashTheme {
                ZodlViewingKeyTextField(
                    state = TextFieldState(value = stringRes(KEY), onValueChange = {}),
                    modifier = Modifier.testTag(FIELD_TAG),
                )
            }
        }
    }

    private companion object {
        const val FIELD_TAG = "viewingKeyField"
        const val KEY = "uview1tg6rpjgju2s2j37gkgjq79qrh5lvzr6e0ed3n4sf4hu5qd35vmsh7avl80xbcdefghi"
    }
}
