package co.electriccoin.zcash.ui.screen.voting.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import co.electriccoin.zcash.ui.design.R

@Composable
fun VoteRadioIndicator(isChecked: Boolean) {
    Box {
        Image(
            painter = painterResource(R.drawable.ic_radio_button_unchecked),
            contentDescription = null,
        )
        AnimatedVisibility(
            visible = isChecked,
            enter = scaleIn(spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy)),
            exit = scaleOut(spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioMediumBouncy))
        ) {
            Image(
                painter = painterResource(R.drawable.ic_radio_button_checked),
                contentDescription = null,
            )
        }
    }
}
