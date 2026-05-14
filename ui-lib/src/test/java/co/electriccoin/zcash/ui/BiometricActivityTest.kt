package co.electriccoin.zcash.ui

import androidx.biometric.BiometricPrompt
import co.electriccoin.zcash.ui.common.repository.BiometricResult
import kotlin.test.Test
import kotlin.test.assertIs

class BiometricActivityTest {
    @Test
    fun cancellationErrorsMapToCancelledResult() {
        val requestCode = "request-code"

        val cancellationCodes =
            listOf(
                BiometricPrompt.ERROR_CANCELED,
                BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                BiometricPrompt.ERROR_USER_CANCELED
            )

        cancellationCodes.forEach { errorCode ->
            assertIs<BiometricResult.Cancelled>(errorCode.toBiometricResult(requestCode))
        }
    }

    @Test
    fun nonCancellationErrorsMapToFailureResult() {
        assertIs<BiometricResult.Failure>(
            BiometricPrompt.ERROR_HW_UNAVAILABLE.toBiometricResult("request-code")
        )
    }
}
