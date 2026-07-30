package co.electriccoin.zcash.ui.screen.error

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.filters.MediumTest
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.listitem.SimpleListItemState
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.test.getStringResource
import org.junit.Rule
import kotlin.test.Test

/**
 * The Sync Error sheet renders the incompatible-server diagnostics when they are present, and is
 * left untouched for the generic sync errors that carry none.
 */
class SyncErrorViewTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @MediumTest
    fun generic_error_shows_the_default_message_and_no_detail_test() {
        setContent(diagnostics = null)

        composeTestRule.onNodeWithText(getStringResource(R.string.sync_error_message)).assertExists()
        composeTestRule.onNodeWithText(getStringResource(R.string.sync_error_detail_server)).assertDoesNotExist()
        composeTestRule.onNodeWithText(getStringResource(R.string.sync_error_detail_error_type)).assertDoesNotExist()
    }

    @Test
    @MediumTest
    fun incompatible_server_replaces_the_default_message_with_an_explanation_test() {
        setContent(diagnostics = diagnostics())

        composeTestRule
            .onNodeWithText(getStringResource(R.string.sync_error_incompatible_consensus_message))
            .assertExists()
        composeTestRule.onNodeWithText(getStringResource(R.string.sync_error_message)).assertDoesNotExist()
    }

    @Test
    @MediumTest
    fun incompatible_server_lists_the_server_and_both_branch_ids_test() {
        setContent(diagnostics = diagnostics())

        composeTestRule.onNodeWithText(getStringResource(R.string.sync_error_detail_server)).assertExists()
        composeTestRule.onNodeWithText(SERVER).assertExists()
        composeTestRule.onNodeWithText(getStringResource(R.string.sync_error_detail_expected_branch)).assertExists()
        composeTestRule.onNodeWithText(CLIENT_BRANCH_ID).assertExists()
        composeTestRule.onNodeWithText(getStringResource(R.string.sync_error_detail_server_branch)).assertExists()
        composeTestRule.onNodeWithText(SERVER_BRANCH_ID).assertExists()
        composeTestRule.onNodeWithText(ERROR_TYPE).assertExists()
    }

    @Test
    @MediumTest
    fun switch_server_is_offered_and_no_extra_button_is_added_test() {
        setContent(diagnostics = diagnostics())

        // Try again, Switch server and Contact Support, and nothing new alongside them.
        composeTestRule.onNodeWithText(TRY_AGAIN).assertExists()
        composeTestRule.onNodeWithText(getStringResource(R.string.sync_error_switch_server)).assertExists()
        composeTestRule.onNodeWithText(getStringResource(R.string.sync_error_contact_support)).assertExists()
        composeTestRule.onNodeWithText(getStringResource(R.string.sync_error_disable_tor)).assertDoesNotExist()
    }

    private fun setContent(diagnostics: SyncErrorDiagnosticsState?) {
        composeTestRule.setContent {
            ZcashTheme {
                SyncErrorContent(
                    state =
                        SyncErrorState(
                            tryAgain = ButtonState(text = stringRes(TRY_AGAIN), onClick = {}),
                            switchServer =
                                ButtonState(
                                    text = stringRes(R.string.sync_error_switch_server),
                                    onClick = {}
                                ),
                            disableTor = null,
                            support =
                                ButtonState(
                                    text = stringRes(R.string.sync_error_contact_support),
                                    onClick = {}
                                ),
                            onBack = {},
                            diagnostics = diagnostics
                        ),
                    contentPadding = PaddingValues()
                )
            }
        }
    }
}

private fun diagnostics() =
    SyncErrorDiagnosticsState(
        explanation = stringRes(R.string.sync_error_incompatible_consensus_message),
        facts =
            listOf(
                SimpleListItemState(stringRes(R.string.sync_error_detail_server), stringRes(SERVER)),
                SimpleListItemState(
                    stringRes(R.string.sync_error_detail_expected_branch),
                    stringRes(CLIENT_BRANCH_ID)
                ),
                SimpleListItemState(
                    stringRes(R.string.sync_error_detail_server_branch),
                    stringRes(SERVER_BRANCH_ID)
                ),
                SimpleListItemState(stringRes(R.string.sync_error_detail_error_type), stringRes(ERROR_TYPE))
            )
    )

private const val TRY_AGAIN = "Try again"
private const val SERVER = "zec.rocks:443"
private const val CLIENT_BRANCH_ID = "0x5437f330"
private const val SERVER_BRANCH_ID = "0x37a5165b"
private const val ERROR_TYPE = "MismatchedConsensusBranch"
