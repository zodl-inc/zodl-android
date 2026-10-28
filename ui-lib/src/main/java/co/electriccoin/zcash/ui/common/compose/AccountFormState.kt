package co.electriccoin.zcash.ui.common.compose

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import co.electriccoin.zcash.ui.common.model.AccountNameLimit
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.AccountAvatarState
import co.electriccoin.zcash.ui.design.component.ButtonState
import co.electriccoin.zcash.ui.design.component.TextFieldState
import co.electriccoin.zcash.ui.design.util.ImageResource
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

/**
 * The frame of an account form, drawn by [AccountFormView]: the top app bar with [title] and an
 * optional [subtitle], over [header]. Back is the screen state's own `onBack`, which the view takes
 * alongside this state, so the toolbar arrow and system back run the same callback.
 */
@Immutable
data class AccountFormState(
    val title: StringResource,
    val subtitle: StringResource?,
    val header: AccountFormHeaderState,
) {
    companion object {
        internal val preview: AccountFormState =
            AccountFormState(
                title = stringRes(R.string.editAccount_title),
                subtitle = null,
                header = AccountFormHeaderState.preview,
            )
    }
}

/**
 * The account avatar over a heading and a message, drawn by [AccountFormHeader]. The avatar carries
 * the pencil badge when [avatar] has an edit button.
 */
@Immutable
data class AccountFormHeaderState(
    val avatar: AccountAvatarState,
    val heading: StringResource,
    val message: StringResource,
) {
    companion object {
        internal val preview: AccountFormHeaderState =
            createAccountName(
                avatar =
                    AccountAvatarState.create(
                        image =
                            ImageResource.Layered(
                                background = Color.Unspecified,
                                foreground = ImageResource.ByDrawable(R.drawable.ic_item_zodl_glyph)
                            ),
                        onEditClick = {}
                    ),
            )

        /** The header of a form that names an account: [avatar] over the account-name heading and message. */
        fun createAccountName(avatar: AccountAvatarState) =
            AccountFormHeaderState(
                avatar = avatar,
                heading = stringRes(R.string.accountName_heading),
                message = stringRes(R.string.accountName_message),
            )
    }
}

/**
 * The account name field, drawn by [AccountNameField]: its [label], the [field] with [placeholder],
 * and the live character [counter] under it when there is one. The keyboard's Done key runs [onDone].
 */
@Immutable
data class AccountNameFieldState(
    val label: StringResource,
    val field: TextFieldState,
    val placeholder: StringResource,
    val counter: StringResource?,
    val onDone: () -> Unit,
) {
    companion object {
        internal val preview: AccountNameFieldState =
            create(
                name = stringRes(PREVIEW_NAME),
                nameLength = AccountNameLimit.lengthOf(PREVIEW_NAME),
                isDuplicate = false,
                onValueChange = {},
                submit = ButtonState(text = stringRes(PREVIEW_NAME)),
            )

        /**
         * The field showing [name]. [nameLength] is the code-point length of an editable [name],
         * shown in the counter; null for a name that cannot be changed, which disables the field and
         * hides the counter. [isDuplicate] says under the field that another account has the name.
         * The keyboard's Done key runs [submit] while it is enabled. [isEnabled] false disables the
         * field while keeping the counter, for a name that must not change for now, such as while the
         * account is being imported.
         */
        fun create(
            name: StringResource,
            nameLength: Int?,
            isDuplicate: Boolean,
            onValueChange: (String) -> Unit,
            submit: ButtonState,
            isEnabled: Boolean = true,
        ) = AccountNameFieldState(
            label = stringRes(R.string.accountName_label),
            field =
                TextFieldState(
                    value = name,
                    error = if (isDuplicate) stringRes(R.string.accountName_duplicate) else null,
                    isEnabled = isEnabled && nameLength != null,
                    onValueChange = onValueChange
                ),
            placeholder = stringRes(R.string.accountName_placeholder),
            counter = nameLength?.let { stringRes(R.string.accountName_counter, it, AccountNameLimit.MAX_LENGTH) },
            onDone = { if (submit.isEnabled) submit.onClick() },
        )

        private const val PREVIEW_NAME = "Zodl Vault"
    }
}
