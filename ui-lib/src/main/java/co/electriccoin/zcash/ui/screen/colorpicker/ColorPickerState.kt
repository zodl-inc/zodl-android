package co.electriccoin.zcash.ui.screen.colorpicker

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.component.IconButtonState
import co.electriccoin.zcash.ui.design.component.ModalBottomSheetState
import co.electriccoin.zcash.ui.design.component.SegmentedControlItem
import co.electriccoin.zcash.ui.design.component.colorpicker.ColorChannel
import co.electriccoin.zcash.ui.design.component.colorpicker.ColorPickerPalette
import co.electriccoin.zcash.ui.design.component.colorpicker.ColorSwatchState
import co.electriccoin.zcash.ui.design.component.colorpicker.channel
import co.electriccoin.zcash.ui.design.component.colorpicker.spectrumPosition
import co.electriccoin.zcash.ui.design.component.colorpicker.toHex
import co.electriccoin.zcash.ui.design.component.colorpicker.withChannel
import co.electriccoin.zcash.ui.design.util.StringResource
import co.electriccoin.zcash.ui.design.util.stringRes

internal enum class ColorPickerTab { GRID, SPECTRUM, SLIDERS }

/** A text field of the Sliders tab, with the channel it edits; the hex field edits all three. */
internal enum class ColorPickerField(
    val channel: ColorChannel?
) {
    RED(ColorChannel.RED),
    GREEN(ColorChannel.GREEN),
    BLUE(ColorChannel.BLUE),
    HEX(null)
}

/**
 * What has been picked so far.
 *
 * @param color the picked colour, always opaque.
 * @param spectrumPosition where the Spectrum tab rings the colour, as fractions of the spectrum.
 * @param drafts the text of a Sliders field while it is being typed, which may not be a value yet.
 */
@Immutable
internal data class ColorPickerSelection(
    val color: Color,
    val tab: ColorPickerTab,
    val spectrumPosition: Offset,
    val drafts: Map<ColorPickerField, String>,
) {
    companion object {
        /** A selection starting on [initial], or on opaque white without one, on the Grid tab. */
        fun initial(initial: Color?): ColorPickerSelection {
            val color = (initial ?: Color.White).copy(alpha = 1f)
            return ColorPickerSelection(
                color = color,
                tab = ColorPickerTab.GRID,
                spectrumPosition = color.spectrumPosition(),
                drafts = emptyMap()
            )
        }
    }
}

/** One Red, Green or Blue row of the Sliders tab: its label, slider, and 0-255 field. */
@Immutable
internal data class ColorChannelState(
    val label: StringResource,
    val value: Float,
    val trackColors: List<Color>,
    val knobColor: Color,
    val text: String,
    val onValueChange: (Float) -> Unit,
    val onTextChange: (String) -> Unit,
)

/** The area under the tabs, which the selected tab decides. */
@Immutable
internal sealed interface ColorPickerContentState {
    data class Grid(
        val selected: Color,
        val onSelect: (Color) -> Unit,
    ) : ColorPickerContentState

    data class Spectrum(
        val position: Offset,
        val color: Color,
        val onPick: (Offset) -> Unit,
    ) : ColorPickerContentState

    data class Sliders(
        val channels: List<ColorChannelState>,
        val hexLabel: StringResource,
        val hex: String,
        val onHexChange: (String) -> Unit,
    ) : ColorPickerContentState
}

/**
 * The colour picker sheet: the "Colors" toolbar whose trailing button closes it, the Grid,
 * Spectrum and Sliders tabs, and the swatches row. Back, a drag down or a tap outside close it as
 * well, and every way of closing it keeps the last picked colour.
 */
@Immutable
internal data class ColorPickerState(
    val title: StringResource,
    val doneButton: IconButtonState,
    val tabs: List<SegmentedControlItem>,
    val content: ColorPickerContentState,
    val picked: Color,
    val swatches: List<ColorSwatchState>,
    override val onBack: () -> Unit,
) : ModalBottomSheetState {
    companion object {
        private val TABS =
            listOf(
                ColorPickerTab.GRID to R.string.colorPicker_grid,
                ColorPickerTab.SPECTRUM to R.string.colorPicker_spectrum,
                ColorPickerTab.SLIDERS to R.string.colorPicker_sliders,
            )

        private val CHANNELS =
            listOf(
                Triple(ColorChannel.RED, ColorPickerField.RED, R.string.colorPicker_red),
                Triple(ColorChannel.GREEN, ColorPickerField.GREEN, R.string.colorPicker_green),
                Triple(ColorChannel.BLUE, ColorPickerField.BLUE, R.string.colorPicker_blue),
            )

        val preview: ColorPickerState = preview(ColorPickerTab.GRID)

        val previewSpectrum: ColorPickerState = preview(ColorPickerTab.SPECTRUM)

        val previewSliders: ColorPickerState = preview(ColorPickerTab.SLIDERS)

        /** The sheet for [selection]; the callbacks are the view model's. */
        fun create(
            selection: ColorPickerSelection,
            onTabSelect: (ColorPickerTab) -> Unit,
            onColorSelect: (Color) -> Unit,
            onSpectrumPick: (Offset) -> Unit,
            onChannelChange: (ColorChannel, Float) -> Unit,
            onFieldChange: (ColorPickerField, String) -> Unit,
            onBack: () -> Unit,
        ): ColorPickerState {
            val color = selection.color
            return ColorPickerState(
                title = stringRes(R.string.colorPicker_title),
                doneButton =
                    IconButtonState(
                        icon = R.drawable.ic_close,
                        contentDescription = stringRes(R.string.colorPicker_done),
                        onClick = onBack
                    ),
                tabs =
                    TABS.map { (tab, label) ->
                        SegmentedControlItem(
                            text = stringRes(label),
                            isSelected = tab == selection.tab,
                            onClick = { onTabSelect(tab) }
                        )
                    },
                content =
                    when (selection.tab) {
                        ColorPickerTab.GRID -> {
                            ColorPickerContentState.Grid(selected = color, onSelect = onColorSelect)
                        }

                        ColorPickerTab.SPECTRUM -> {
                            ColorPickerContentState.Spectrum(
                                position = selection.spectrumPosition,
                                color = color,
                                onPick = onSpectrumPick
                            )
                        }

                        ColorPickerTab.SLIDERS -> {
                            ColorPickerContentState.Sliders(
                                channels =
                                    CHANNELS.map { (channel, field, label) ->
                                        ColorChannelState(
                                            label = stringRes(label),
                                            value = color.channel(channel) / MAX_CHANNEL,
                                            trackColors =
                                                listOf(
                                                    color.withChannel(channel, 0),
                                                    color.withChannel(channel, MAX_CHANNEL.toInt())
                                                ),
                                            knobColor = color,
                                            text = selection.drafts[field] ?: color.channel(channel).toString(),
                                            onValueChange = { onChannelChange(channel, it) },
                                            onTextChange = { onFieldChange(field, it) }
                                        )
                                    },
                                hexLabel = stringRes(R.string.colorPicker_hex),
                                hex = selection.drafts[ColorPickerField.HEX] ?: color.toHex(),
                                onHexChange = { onFieldChange(ColorPickerField.HEX, it) }
                            )
                        }
                    },
                picked = color,
                swatches =
                    ColorPickerPalette.presets.map { swatch ->
                        ColorSwatchState(
                            color = swatch,
                            isSelected = swatch.toArgb() == color.toArgb(),
                            onClick = onColorSelect
                        )
                    },
                onBack = onBack
            )
        }

        private fun preview(tab: ColorPickerTab) =
            create(
                selection =
                    ColorPickerSelection
                        .initial(Color(PREVIEW_COLOR))
                        .copy(tab = tab),
                onTabSelect = {},
                onColorSelect = {},
                onSpectrumPick = {},
                onChannelChange = { _, _ -> },
                onFieldChange = { _, _ -> },
                onBack = {}
            )

        private const val MAX_CHANNEL = 255f
        private const val PREVIEW_COLOR = 0xFFBE38F3
    }
}
