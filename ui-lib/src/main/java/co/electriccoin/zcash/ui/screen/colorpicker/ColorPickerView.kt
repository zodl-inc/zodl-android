package co.electriccoin.zcash.ui.screen.colorpicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.electriccoin.zcash.ui.design.component.ZashiHorizontalDivider
import co.electriccoin.zcash.ui.design.component.ZashiIconButton
import co.electriccoin.zcash.ui.design.component.ZashiModalBottomSheetDefaults
import co.electriccoin.zcash.ui.design.component.ZashiScreenModalBottomSheet
import co.electriccoin.zcash.ui.design.component.ZodlTabLayout
import co.electriccoin.zcash.ui.design.component.colorpicker.ZodlColorGrid
import co.electriccoin.zcash.ui.design.component.colorpicker.ZodlColorSlider
import co.electriccoin.zcash.ui.design.component.colorpicker.ZodlColorSpectrum
import co.electriccoin.zcash.ui.design.component.colorpicker.ZodlColorSwatches
import co.electriccoin.zcash.ui.design.component.colorpicker.ZodlColorValueField
import co.electriccoin.zcash.ui.design.component.rememberZashiFrostState
import co.electriccoin.zcash.ui.design.component.zashiFrostSource
import co.electriccoin.zcash.ui.design.component.zashiFrostedHeader
import co.electriccoin.zcash.ui.design.newcomponent.PreviewScreens
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.ZashiColors
import co.electriccoin.zcash.ui.design.theme.typography.ZashiTypography
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.getValue

/**
 * The colour picker sheet, laid out as the iOS picker in Figma ("Color Picker - iPhone") on the
 * app's own sheet, colours and type, without the eyedropper or a drag handle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColorPickerView(state: ColorPickerState?) {
    ZashiScreenModalBottomSheet(state = state, dragHandle = null) { innerState, contentPadding ->
        val hazeState = rememberZashiFrostState()
        Box(modifier = Modifier.weight(1f, false)) {
            Column(
                modifier =
                    Modifier
                        .zashiFrostSource(hazeState)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(
                            top = TOOLBAR_TOP_PADDING + TOOLBAR_HEIGHT,
                            bottom = contentPadding.calculateBottomPadding()
                        )
            ) {
                Spacer(Modifier.height(5.dp))
                ZodlTabLayout(items = innerState.tabs, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Content(innerState.content)
                Spacer(Modifier.height(15.dp))
                ZashiHorizontalDivider(color = ZashiColors.Surfaces.strokePrimary)
                Spacer(Modifier.height(11.dp))
                ZodlColorSwatches(
                    current = innerState.picked,
                    swatches = innerState.swatches
                )
            }
            Toolbar(
                state = innerState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .zashiFrostedHeader(
                            hazeState = hazeState,
                            frostColor = ZashiModalBottomSheetDefaults.ContainerColor,
                            fallbackColor = ZashiModalBottomSheetDefaults.ContainerColor
                        ).padding(horizontal = 24.dp)
                        .padding(top = TOOLBAR_TOP_PADDING)
            )
        }
    }
}

@Composable
private fun Toolbar(
    state: ColorPickerState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(TOOLBAR_HEIGHT),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.size(48.dp))
        Text(
            text = state.title.getValue(),
            modifier = Modifier.weight(1f),
            style = ZashiTypography.textLg.copy(fontSize = 17.sp, lineHeight = 22.sp),
            fontWeight = FontWeight.SemiBold,
            color = ZashiColors.Text.textPrimary,
            textAlign = TextAlign.Center
        )
        ZashiIconButton(
            state = state.doneButton,
            modifier = Modifier.offset(x = CLOSE_BUTTON_SHIFT),
            iconSize = CLOSE_ICON_SIZE,
            iconTint = ZashiColors.Text.textTertiary
        )
    }
}

@Composable
private fun Content(state: ColorPickerContentState) {
    when (state) {
        is ColorPickerContentState.Grid -> {
            ZodlColorGrid(selected = state.selected, onSelect = state.onSelect)
        }

        is ColorPickerContentState.Spectrum -> {
            ZodlColorSpectrum(position = state.position, color = state.color, onPick = state.onPick)
        }

        is ColorPickerContentState.Sliders -> {
            Sliders(state)
        }
    }
}

/** The Sliders tab, in the same 296 dp footprint as the grid and the spectrum. */
@Composable
private fun Sliders(state: ColorPickerContentState.Sliders) {
    Column(modifier = Modifier.height(296.dp)) {
        state.channels.forEach { channel ->
            Label(channel.label)
            Row(
                modifier = Modifier.offset(y = -SLIDER_LIFT),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                ZodlColorSlider(
                    value = channel.value,
                    trackColors = channel.trackColors,
                    knobColor = channel.knobColor,
                    onValueChange = channel.onValueChange,
                    modifier = Modifier.weight(1f)
                )
                ZodlColorValueField(
                    value = channel.text,
                    onValueChange = channel.onTextChange,
                    modifier = Modifier.width(VALUE_FIELD_WIDTH),
                    label = channel.label.getValue()
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.hexLabel.getValue(),
                modifier = Modifier.weight(1f),
                style = ZashiTypography.textMd,
                color = ZashiColors.Text.textPrimary
            )
            ZodlColorValueField(
                value = state.hex,
                onValueChange = state.onHexChange,
                modifier = Modifier.width(HEX_FIELD_WIDTH),
                keyboardType = KeyboardType.Ascii,
                label = state.hexLabel.getValue()
            )
        }
    }
}

@Composable
private fun Label(text: StringResource) {
    Text(
        text = text.getValue().uppercase(),
        style = ZashiTypography.textSm.copy(fontSize = 13.sp),
        fontWeight = FontWeight.SemiBold,
        color = ZashiColors.Text.textPrimary
    )
}

private val VALUE_FIELD_WIDTH = 75.dp
private val HEX_FIELD_WIDTH = 104.dp

/** Pulls a slider row up so its track starts 4 dp below the label, while keeping the 48 dp touch area. */
private val SLIDER_LIFT = 3.dp

/**
 * The "Colors" toolbar stays pinned over the scrolling content, which frosts behind it, so the
 * content starts below its 4 dp top padding and 48 dp row.
 */
private val TOOLBAR_TOP_PADDING = 4.dp

private val TOOLBAR_HEIGHT = 48.dp

/** Draws the 12 dp-wide close glyph of the 24 dp vector at 15 dp, about Figma's 17 pt mark including caps. */
private val CLOSE_ICON_SIZE = 30.dp

/** Moves the close button right so its glyph sits about 4 dp inside the content edge, as in Figma. */
private val CLOSE_BUTTON_SHIFT = 12.dp

@PreviewScreens
@Composable
private fun GridPreview() =
    ZcashTheme {
        ColorPickerView(ColorPickerState.preview)
    }

@PreviewScreens
@Composable
private fun SpectrumPreview() =
    ZcashTheme {
        ColorPickerView(ColorPickerState.previewSpectrum)
    }

@PreviewScreens
@Composable
private fun SlidersPreview() =
    ZcashTheme {
        ColorPickerView(ColorPickerState.previewSliders)
    }
