package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import org.junit.Rule
import org.junit.Test

/**
 * Guards the MOB-1376 fix: the plaintext recovery phrase must never reach the composable's
 * text/semantics nodes until [SeedTextState.isRevealed] is true (i.e. after the biometric reveal).
 * The blur is only a cosmetic effect, so we assert directly against the semantics tree.
 */
class ZashiSeedTextTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val hiddenSeedWordDescription =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .getString(R.string.general_hidden_seed_word)

    private val seedWords = List(SEED_WORD_COUNT) { "word$it" }

    private fun setContent(isRevealed: Boolean) {
        composeTestRule.setContent {
            ZcashTheme {
                ZashiSeedText(
                    modifier = Modifier.fillMaxWidth(),
                    state =
                        SeedTextState(
                            seed = seedWords.joinToString(separator = " "),
                            isRevealed = isRevealed,
                        )
                )
            }
        }
    }

    @Test
    @MediumTest
    fun plaintext_words_absent_from_semantics_while_hidden() {
        setContent(isRevealed = false)

        seedWords.forEach { word ->
            composeTestRule.onNodeWithText(word).assertDoesNotExist()
        }

        // While hidden, each word node exposes only a descriptive content description (the visible
        // "•••••" mask is cleared from the accessibility tree) so a screen reader does not spell
        // out the mask for every word.
        composeTestRule
            .onAllNodesWithContentDescription(hiddenSeedWordDescription)
            .assertCountEquals(SEED_WORD_COUNT)
    }

    @Test
    @MediumTest
    fun plaintext_words_present_in_semantics_once_revealed() {
        setContent(isRevealed = true)

        seedWords.forEach { word ->
            composeTestRule.onNodeWithText(word).assertExists()
        }

        composeTestRule.onAllNodesWithContentDescription(hiddenSeedWordDescription).assertCountEquals(0)
    }
}

private const val SEED_WORD_COUNT = 24
