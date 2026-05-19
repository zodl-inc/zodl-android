package co.electriccoin.zcash.ui.screen.restore.height

import co.electriccoin.zcash.ui.design.component.NumberTextFieldInnerState
import co.electriccoin.zcash.ui.design.util.StringResource

internal data class RestoreBDHeightValidation(
    val blockHeight: Long? = null,
    val error: RestoreBDHeightValidationError? = null,
) {
    val isValid = blockHeight != null && error == null
}

internal enum class RestoreBDHeightValidationError {
    INVALID_INTEGER,
    BELOW_SAPLING_ACTIVATION
}

internal object RestoreBDHeightValidator {
    fun validate(
        blockHeight: NumberTextFieldInnerState,
        saplingActivationHeight: Long
    ): RestoreBDHeightValidation {
        val inputText = blockHeight.inputText()
        if (inputText.isEmpty()) {
            return RestoreBDHeightValidation()
        }

        val amount =
            blockHeight.amount
                ?: return RestoreBDHeightValidation(error = RestoreBDHeightValidationError.INVALID_INTEGER)
        if (inputText.contains('.') || inputText.contains(',')) {
            return RestoreBDHeightValidation(error = RestoreBDHeightValidationError.INVALID_INTEGER)
        }

        val exactHeight =
            try {
                amount.longValueExact()
            } catch (_: ArithmeticException) {
                return RestoreBDHeightValidation(error = RestoreBDHeightValidationError.INVALID_INTEGER)
            }

        return if (exactHeight < saplingActivationHeight) {
            RestoreBDHeightValidation(
                error = RestoreBDHeightValidationError.BELOW_SAPLING_ACTIVATION
            )
        } else {
            RestoreBDHeightValidation(blockHeight = exactHeight)
        }
    }
}

private fun NumberTextFieldInnerState.inputText(): String =
    (innerTextFieldState.value as? StringResource.ByString)?.value.orEmpty()
