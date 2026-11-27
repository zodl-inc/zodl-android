package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.util.PressMorphDefaults
import co.electriccoin.zcash.ui.design.util.pressMorph

@Composable
fun SwitchWithLabel(
    label: String,
    state: Boolean,
    onStateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    ConstraintLayout(
        modifier =
            modifier
                .pressMorph(interactionSource, PressMorphDefaults.PRESSED_SCALE_SUBTLE)
                .clickable(
                    interactionSource = interactionSource,
                    // disable ripple
                    indication = null,
                    role = Role.Switch,
                    onClick = { onStateChange(!state) }
                ).fillMaxWidth()
    ) {
        val (text, spacer, switchButton) = createRefs()
        Body(
            text = label,
            modifier =
                Modifier.constrainAs(text) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(spacer.start)
                    width = Dimension.fillToConstraints
                }
        )
        Spacer(
            modifier =
                Modifier
                    .width(ZcashTheme.dimens.spacingDefault)
                    .constrainAs(spacer) {
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.top)
                        start.linkTo(text.end)
                        end.linkTo(switchButton.start)
                    }
        )
        Switch(
            checked = state,
            onCheckedChange = {
                onStateChange(it)
            },
            modifier =
                Modifier.constrainAs(switchButton) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.top)
                    start.linkTo(spacer.end)
                    end.linkTo(parent.end)
                    width = Dimension.wrapContent
                }
        )
    }
}
