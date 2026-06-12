package co.electriccoin.zcash.ui.design.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.spackle.AndroidApiVersion
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.dimensions.ZashiDimensions
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography

@Suppress("MagicNumber")
@Composable
fun ZashiSeedText(
    state: SeedTextState,
    modifier: Modifier = Modifier
) {
    val maskedSeedWord = stringResource(R.string.general_masked_seed_word)
    val hiddenSeedWordDescription = stringResource(R.string.general_hidden_seed_word)
    val blur by animateDpAsState(if (state.isRevealed) 0.dp else 14.dp, label = "")
    val color by animateColorAsState(
        when {
            AndroidApiVersion.isAtLeastS -> Color.Unspecified
            state.isRevealed -> ZashiColors.Surfaces.bgPrimary
            else -> ZashiColors.Surfaces.bgSecondary
        },
        label = ""
    )
    Box(
        modifier = modifier.background(color, RoundedCornerShape(10.dp)),
    ) {
        // Until the user passes the biometric reveal, the plaintext mnemonic must not enter the
        // composable's Text/semantics nodes - the blur is only a cosmetic effect (a no-op for
        // accessibility services and view-tree inspection). We therefore render masked placeholders
        // and only substitute the real words once the state is revealed. See security ticket MOB-1376.
        val rowItems =
            remember(state, maskedSeedWord) {
                state.seed
                    .split(" ")
                    .mapIndexed { index, word ->
                        IndexedValue(index, if (state.isRevealed) word else maskedSeedWord)
                    }.chunked(3)
            }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = spacedBy(4.dp)
        ) {
            rowItems.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = spacedBy(4.dp)
                ) {
                    row.forEach { (index, string) ->
                        ZashiSeedWordText(
                            modifier = Modifier.weight(1f),
                            prefix = (index + 1).toString(),
                            state =
                                SeedWordTextState(
                                    text = string,
                                ),
                            content = { mod, text ->
                                ZashiSeedWordTextContent(
                                    text = text,
                                    contentDescription = if (state.isRevealed) null else hiddenSeedWordDescription,
                                    modifier = mod.blurCompat(blur, 14.dp)
                                )
                            },
                            prefixContent = { mod, text ->
                                ZashiSeedWordPrefixContent(
                                    text = text,
                                    modifier =
                                        mod then
                                            if (!AndroidApiVersion.isAtLeastS) {
                                                Modifier.blurCompat(blur, 14.dp)
                                            } else {
                                                Modifier
                                            }
                                )
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
            visible = !AndroidApiVersion.isAtLeastS && state.isRevealed.not(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_reveal),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(ZashiColors.Text.textPrimary)
                )

                Spacer(Modifier.height(ZashiDimensions.Spacing.spacingMd))

                Text(
                    text = stringResource(R.string.seed_recovery_reveal),
                    style = ZashiTypography.textLg,
                    fontWeight = FontWeight.SemiBold,
                    color = ZashiColors.Text.textPrimary
                )
            }
        }
    }
}

data class SeedTextState(
    val seed: String,
    val isRevealed: Boolean
)

@PreviewScreens
@Composable
private fun Preview() =
    ZcashTheme {
        BlankSurface {
            ZashiSeedText(
                modifier = Modifier.fillMaxWidth(),
                state =
                    SeedTextState(
                        seed = (1..24).joinToString(separator = " ") { "word" },
                        isRevealed = true,
                    )
            )
        }
    }

@PreviewScreens
@Composable
private fun HiddenPreview() =
    ZcashTheme {
        BlankSurface {
            ZashiSeedText(
                modifier = Modifier.fillMaxWidth(),
                state =
                    SeedTextState(
                        seed = (1..24).joinToString(separator = " ") { "word" },
                        isRevealed = false,
                    )
            )
        }
    }
