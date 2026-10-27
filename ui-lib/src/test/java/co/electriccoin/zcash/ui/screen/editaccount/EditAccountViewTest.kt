package co.electriccoin.zcash.ui.screen.editaccount

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import co.electriccoin.zcash.ui.common.compose.AccountNameFieldTag
import co.electriccoin.zcash.ui.common.model.AccountNameLimit
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.imageRes
import co.electriccoin.zcash.ui.design.util.stringRes
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Edit Account form: Save follows the state's enabled flag, the Zodl form has a disabled name
 * field and no Remove Account button, and the counter under the field shows the name's length.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditAccountViewTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun saveIsEnabledOnlyWhenTheStateAllowsIt() {
        var saves = 0
        var shown by mutableStateOf(viewOnly(name = "Vault", canSave = false, onSave = { saves++ }))
        composeTestRule.setContent { ZcashTheme { EditAccountView(shown) } }

        composeTestRule.onNodeWithTag(EditAccountTag.SAVE_BUTTON).assertIsNotEnabled()

        shown = viewOnly(name = "Vault", canSave = true, onSave = { saves++ })
        composeTestRule
            .onNodeWithTag(EditAccountTag.SAVE_BUTTON)
            .assertIsEnabled()
            .performScrollTo()
            .performClick()
        assertEquals(1, saves)

        shown = viewOnly(name = "Vault", canSave = true, isSaving = true, onSave = { saves++ })
        composeTestRule.onNodeWithTag(EditAccountTag.SAVE_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun theZodlFormShowsNoRemoveButtonAndADisabledField() {
        composeTestRule.setContent { ZcashTheme { EditAccountView(EditAccountState.previewZodl) } }

        composeTestRule.onNodeWithTag(EditAccountTag.REMOVE_BUTTON).assertDoesNotExist()
        composeTestRule.onNodeWithTag(AccountNameFieldTag.COUNTER).assertDoesNotExist()
        composeTestRule.onNode(isTextField).assertIsNotEnabled()
    }

    @Test
    fun theCounterShowsTheNameLength() {
        composeTestRule.setContent { ZcashTheme { EditAccountView(viewOnly(name = "Zodl Vault 🔐", canSave = false)) } }

        val expected =
            RuntimeEnvironment.getApplication().getString(R.string.accountName_counter, 12, AccountNameLimit.MAX_LENGTH)
        composeTestRule.onNodeWithTag(AccountNameFieldTag.COUNTER).assertTextEquals(expected)
        composeTestRule.onNodeWithTag(EditAccountTag.REMOVE_BUTTON).assertIsEnabled()
        composeTestRule.onNode(isTextField).assertIsEnabled()
    }

    /** The name field, found by its editable text, which it keeps while disabled. */
    private val isTextField = SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)

    private fun viewOnly(
        name: String,
        canSave: Boolean,
        isSaving: Boolean = false,
        onSave: () -> Unit = {},
    ) = EditAccountState.create(
        image = imageRes(R.drawable.ic_item_zashi),
        name = stringRes(name),
        nameLength = AccountNameLimit.lengthOf(name),
        isDuplicate = false,
        canSave = canSave,
        isSaving = isSaving,
        remove =
            EditAccountRemoveState.create(
                icon = R.drawable.ic_info,
                info = stringRes("Removing asks first"),
                buttonText = stringRes("Remove"),
                isSaving = isSaving,
                onClick = {},
            ),
        onAvatarEditClick = {},
        onValueChange = {},
        onSaveClick = onSave,
        onBack = {},
    )
}
