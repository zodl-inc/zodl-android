package co.electriccoin.zcash.ui.design.component.listitem

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.filters.MediumTest
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import org.junit.Rule
import org.junit.Test

/**
 * A wallet row tells accessibility services whether it is the selected wallet, which its fill and
 * check badge only show visually, on the clickable node itself, and a trailing chevron without a
 * description stays out of the accessibility tree while a described options button remains
 * reachable.
 */
class ZodlWalletListItemSemanticsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        isSelected: Boolean,
        trailingAction: IconButtonState,
    ) {
        composeTestRule.setContent {
            ZcashTheme {
                ZodlWalletListItem(
                    state =
                        WalletListItemState(
                            icon = imageRes(R.drawable.ic_item_zashi),
                            title = stringRes(TITLE),
                            subtitle = stringRes("1 ZEC"),
                            isSelected = isSelected,
                            trailingAction = trailingAction,
                            onClick = {},
                        )
                )
            }
        }
    }

    @Test
    @MediumTest
    fun theSelectedRowIsReportedAsSelected() {
        setContent(isSelected = true, trailingAction = chevron())

        composeTestRule.onNode(clickableRow(), useUnmergedTree = true).assertIsSelected()
        composeTestRule.onNodeWithText(TITLE).assertIsSelected()
    }

    @Test
    @MediumTest
    fun anotherRowIsReportedAsNotSelected() {
        setContent(isSelected = false, trailingAction = chevron())

        composeTestRule.onNode(clickableRow(), useUnmergedTree = true).assertIsNotSelected()
        composeTestRule.onNodeWithText(TITLE).assertIsNotSelected()
    }

    @Test
    @MediumTest
    fun aDescribedOptionsButtonStaysReachable() {
        setContent(
            isSelected = false,
            trailingAction =
                IconButtonState(
                    icon = R.drawable.ic_wallet_list_more,
                    contentDescription = stringRes(OPTIONS),
                    onClick = {},
                ),
        )

        composeTestRule.onNodeWithContentDescription(OPTIONS).assertExists()
        composeTestRule.onAllNodes(hasClickAction()).assertCountEquals(2)
    }

    @Test
    @MediumTest
    fun aChevronWithoutADescriptionIsLeftOutOfTheAccessibilityTree() {
        setContent(isSelected = true, trailingAction = chevron())

        composeTestRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        composeTestRule.onNodeWithText(TITLE).assertHasClickAction()
    }

    /**
     * The row's own clickable node in the unmerged tree, the node an accessibility service focuses;
     * the selected state must sit on it, not on a child.
     */
    private fun clickableRow(): SemanticsMatcher = hasClickAction() and hasAnyDescendant(hasText(TITLE))

    private fun chevron() = IconButtonState(icon = R.drawable.ic_wallet_list_chevron, onClick = {})

    private companion object {
        const val TITLE = "Zodl"
        const val OPTIONS = "Options for Zodl"
    }
}
