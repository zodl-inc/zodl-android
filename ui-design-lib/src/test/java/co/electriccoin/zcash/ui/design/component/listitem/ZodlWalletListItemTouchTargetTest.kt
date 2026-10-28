package co.electriccoin.zcash.ui.design.component.listitem

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The trailing action of a wallet row is a real 48 dp touch target, its outer ring included, while
 * the row stays 60 dp tall; a tap anywhere on it reaches the action and never the row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ZodlWalletListItemTouchTargetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var rowClicks = 0

    private var actionClicks = 0

    @Test
    fun theRowIsSixtyDpTallAndTheActionFortyEightDpSquare() {
        setContent()

        val row = composeTestRule.onNodeWithTag(ROW_TAG).getUnclippedBoundsInRoot()
        val action = composeTestRule.onNodeWithContentDescription(OPTIONS).getUnclippedBoundsInRoot()
        assertEquals(60.dp, row.height)
        assertEquals(48.dp, action.width)
        assertEquals(48.dp, action.height)
    }

    @Test
    fun aTapOnTheOuterRingOfTheActionReachesTheAction() {
        setContent()

        composeTestRule.onNodeWithContentDescription(OPTIONS).performTouchInput {
            click(Offset(x = 1.dp.toPx(), y = height / 2f))
        }
        composeTestRule.onNodeWithContentDescription(OPTIONS).performTouchInput {
            click(Offset(x = width / 2f, y = 1.dp.toPx()))
        }

        assertEquals(2, actionClicks)
        assertEquals(0, rowClicks)
    }

    @Test
    fun aTapJustOutsideTheActionReachesTheRow() {
        setContent()

        composeTestRule.onNodeWithContentDescription(OPTIONS).performTouchInput {
            click(Offset(x = width + 4.dp.toPx(), y = height / 2f))
        }

        assertEquals(0, actionClicks)
        assertEquals(1, rowClicks)
    }

    private fun setContent() {
        composeTestRule.setContent {
            ZcashTheme {
                ZodlWalletListItem(
                    modifier = Modifier.width(360.dp).testTag(ROW_TAG),
                    state =
                        WalletListItemState(
                            icon = imageRes(R.drawable.ic_item_zashi),
                            title = stringRes("Zodl"),
                            subtitle = stringRes("1 ZEC"),
                            isSelected = false,
                            trailingAction =
                                IconButtonState(
                                    icon = R.drawable.ic_wallet_list_more,
                                    contentDescription = stringRes(OPTIONS),
                                    onClick = { actionClicks++ },
                                ),
                            onClick = { rowClicks++ },
                        )
                )
            }
        }
    }

    private companion object {
        const val OPTIONS = "Options for Zodl"
        const val ROW_TAG = "wallet_row"
    }
}
