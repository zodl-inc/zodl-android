package co.electriccoin.zcash.ui.design.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test

/**
 * With `showDust = false` (Home) the balance is cut to three decimals, except that a balance which
 * would then read as 0.000 keeps its remaining digits, down to a single zatoshi, without a ticker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StyledBalanceTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun assertRenders(
        zatoshi: Long,
        showDust: Boolean,
        expected: String
    ) {
        composeTestRule.setContent {
            ZcashTheme {
                StyledBalance(
                    balance = Zatoshi(zatoshi),
                    showDust = showDust,
                    modifier = Modifier.testTag(TAG)
                )
            }
        }
        composeTestRule.onNodeWithTag(TAG, useUnmergedTree = true).assertTextEquals(expected)
    }

    @Test
    fun aTypicalTestAmountKeepsItsPositionalDigits() =
        assertRenders(zatoshi = 4_000, showDust = false, expected = "0.00004")

    @Test
    fun oneZatoshiIsNeverTruncated() =
        assertRenders(zatoshi = 1, showDust = false, expected = "0.00000001")

    @Test
    fun zeroStaysThreeDecimals() =
        assertRenders(zatoshi = 0, showDust = false, expected = "0.000")

    @Test
    fun exactlyOneThousandthStaysThreeDecimals() =
        assertRenders(zatoshi = 100_000, showDust = false, expected = "0.001")

    @Test
    fun aBalanceAboveTheThresholdIsUnchanged() =
        assertRenders(zatoshi = 123_456_789, showDust = false, expected = "1.234")

    @Test
    fun showDustStillRendersTheTickerForEveryBalance() =
        assertRenders(zatoshi = 4_000, showDust = true, expected = "0.00004 ZEC")

    private companion object {
        const val TAG = "styled_balance"
    }
}
