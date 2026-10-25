package co.electriccoin.zcash.ui.design.component.listitem

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.design.util.withStyle
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test

/**
 * A sheet row is one button for accessibility services that reads its title and subtitle once: the
 * logo and the chevron are decorative and add no description of their own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ZodlSheetListItemSemanticsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun theRowIsOneButtonNamedOnceByItsTitle() {
        composeTestRule.setContent {
            ZcashTheme {
                ZodlSheetListItem(
                    icon = imageRes(R.drawable.ic_item_keystone),
                    title = stringRes(TITLE),
                    subtitle = stringRes(SUBTITLE).withStyle(),
                    onClick = {},
                )
            }
        }

        composeTestRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        composeTestRule
            .onNode(hasClickAction())
            .assert(hasText(TITLE) and hasText(SUBTITLE))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        composeTestRule.onAllNodesWithText(TITLE).assertCountEquals(1)
        composeTestRule.onAllNodesWithContentDescription(TITLE).assertCountEquals(0)
    }

    private companion object {
        const val TITLE = "Keystone"
        const val SUBTITLE = "Connect a hardware wallet"
    }
}
