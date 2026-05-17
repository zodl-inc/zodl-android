package co.electriccoin.zcash.ui.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import co.electriccoin.zcash.ui.design.theme.ZcashTheme

@Preview(showBackground = true)
@Composable
private fun LabeledCheckboxPreview() {
    ZcashTheme(forceDarkMode = false) {
        BlankSurface {
            Row {
                LabeledCheckBox(
                    onCheckedChange = {},
                    text = "Checkbox",
                    checked = false
                )
                LabeledCheckBox(
                    onCheckedChange = {},
                    text = "Checkbox",
                    checked = true
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LabeledCheckboxDarkPreview() {
    ZcashTheme(forceDarkMode = true) {
        BlankSurface {
            Row {
                LabeledCheckBox(
                    onCheckedChange = {},
                    text = "Checkbox",
                    checked = false
                )
                LabeledCheckBox(
                    onCheckedChange = {},
                    text = "Checkbox",
                    checked = true
                )
            }
        }
    }
}

@Composable
fun LabeledCheckBox(
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    checked: Boolean = false,
    checkBoxTestTag: String? = null,
    color: Color = ZcashTheme.colors.textPrimary,
    style: TextStyle = ZcashTheme.extendedTypography.checkboxText
) {
    val (checkedState, setCheckedState) = rememberSaveable { mutableStateOf(checked) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier.then(
                Modifier
                    .clip(RoundedCornerShape(ZcashTheme.dimens.regularRippleEffectCorner))
                    .clickable {
                        setCheckedState(!checkedState)
                        onCheckedChange(!checkedState)
                    }
                    // Setting just the end padding, the start one is taken from the checkbox
                    .padding(end = ZcashTheme.dimens.spacingMid)
            )
    ) {
        Checkbox(
            checked = checkedState,
            colors =
                CheckboxDefaults.colors(
                    checkedColor = ZcashTheme.colors.secondaryColor,
                    uncheckedColor = ZcashTheme.colors.secondaryColor,
                    checkmarkColor = ZcashTheme.colors.primaryColor,
                ),
            onCheckedChange = {
                setCheckedState(it)
                onCheckedChange(it)
            },
            enabled = true,
            modifier =
                Modifier
                    .padding(
                        top = ZcashTheme.dimens.spacingTiny,
                        bottom = ZcashTheme.dimens.spacingTiny,
                        end = ZcashTheme.dimens.spacingTiny
                    ).then(
                        if (checkBoxTestTag != null) {
                            Modifier.testTag(checkBoxTestTag)
                        } else {
                            Modifier
                        }
                    )
        )
        Text(
            text = AnnotatedString(text),
            color = color,
            style = style
        )
    }
}
