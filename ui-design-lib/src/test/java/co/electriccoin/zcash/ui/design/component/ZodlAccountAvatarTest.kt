package co.electriccoin.zcash.ui.design.component

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.imageRes
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The avatar's pencil badge is one 48 dp button named after what it does, so a screen reader reads
 * it once, and a tap on it reaches the edit callback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ZodlAccountAvatarTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun theEditBadgeIsOneButtonNamedEditColor() {
        var edits = 0
        composeTestRule.setContent {
            ZcashTheme {
                ZodlAccountAvatar(
                    state =
                        AccountAvatarState.create(
                            image = imageRes(R.drawable.ic_item_zashi),
                            onEditClick = { edits++ }
                        )
                )
            }
        }
        val name = RuntimeEnvironment.getApplication().getString(R.string.colorPicker_edit)

        composeTestRule.onAllNodesWithContentDescription(name).assertCountEquals(1)
        composeTestRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        composeTestRule
            .onNodeWithContentDescription(name)
            .assert(hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertContentDescriptionEquals(name)
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
            .performClick()

        assertEquals(1, edits)
    }
}
