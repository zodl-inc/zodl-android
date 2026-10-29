package co.electriccoin.zcash.ui.screen.viewingkey.name

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import co.electriccoin.zcash.ui.common.compose.AccountFormHeaderState
import co.electriccoin.zcash.ui.common.compose.AccountFormState
import co.electriccoin.zcash.ui.common.compose.AccountNameFieldState
import co.electriccoin.zcash.ui.common.model.AccountNameLimit
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.ButtonStyle
import co.electriccoin.zcash.ui.design.util.stringRes
import co.electriccoin.zcash.ui.screen.viewingkey.vkAvatar
import co.electriccoin.zcash.viewingkey.R

/**
 * Names the account being imported.
 *
 * @param form the form frame, whose avatar's pencil badge opens the colour picker. The picked colour
 *        is only shown, not yet stored with the account.
 * @param nameField the name field, with how many of the allowed characters it uses, counted in code
 *        points, shown under it.
 */
@Immutable
internal data class NameVKState(
    val form: AccountFormState,
    val nameField: AccountNameFieldState,
    val continueButton: ButtonState,
    val onBack: () -> Unit,
) {
    companion object {
        val preview: NameVKState = preview(name = "")

        val previewFilled: NameVKState = preview(name = "Zodl Vault")

        val previewColored: NameVKState = preview(name = "Zodl Vault", color = Color(PREVIEW_COLOR))

        val previewLimit: NameVKState = preview(name = "Zodl Savings")

        val previewDuplicate: NameVKState = preview(name = "Zodl", isDuplicate = true)

        val previewImporting: NameVKState = preview(name = "Zodl Vault", isImporting = true)

        /**
         * The screen for [name] with the avatar in [color], or the default avatar without one.
         * Continue is enabled while the name is not blank and not [isDuplicate], the name of another
         * account, which the field also says under it. While [isImporting], Continue shows its
         * progress and is disabled, and so is the name field.
         */
        fun create(
            name: String,
            color: Color?,
            isDuplicate: Boolean,
            isImporting: Boolean,
            onAvatarEditClick: () -> Unit,
            onValueChange: (String) -> Unit,
            onContinueClick: () -> Unit,
            onBack: () -> Unit,
        ): NameVKState {
            val continueButton =
                ButtonState(
                    text = stringRes(R.string.view_only_continue),
                    style = ButtonStyle.PRIMARY,
                    isEnabled = name.isNotBlank() && !isDuplicate && !isImporting,
                    isLoading = isImporting,
                    onClick = onContinueClick,
                )
            return NameVKState(
                form =
                    AccountFormState(
                        title = stringRes(R.string.view_only_import_title),
                        subtitle = stringRes(R.string.view_only_import_subtitle),
                        header =
                            AccountFormHeaderState.createAccountName(
                                avatar = vkAvatar(color = color, onEditClick = onAvatarEditClick)
                            ),
                    ),
                nameField =
                    AccountNameFieldState.create(
                        name = stringRes(name),
                        nameLength = AccountNameLimit.lengthOf(name),
                        isDuplicate = isDuplicate,
                        onValueChange = onValueChange,
                        submit = continueButton,
                        isEnabled = !isImporting,
                    ),
                continueButton = continueButton,
                onBack = onBack,
            )
        }

        private fun preview(
            name: String,
            color: Color? = null,
            isDuplicate: Boolean = false,
            isImporting: Boolean = false,
        ) = create(
            name = name,
            color = color,
            isDuplicate = isDuplicate,
            isImporting = isImporting,
            onAvatarEditClick = {},
            onValueChange = {},
            onContinueClick = {},
            onBack = {},
        )

        private const val PREVIEW_COLOR = 0xFFBE38F3
    }
}

internal object NameVKTag {
    const val CONTINUE_BUTTON = "view_only_name_continue_button"
}
