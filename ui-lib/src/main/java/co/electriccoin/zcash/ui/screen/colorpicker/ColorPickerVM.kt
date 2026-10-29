package co.electriccoin.zcash.ui.screen.colorpicker

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import co.electriccoin.zcash.ui.common.model.stateIn
import co.electriccoin.zcash.ui.common.usecase.NavigateToColorPickerUseCase
import co.electriccoin.zcash.ui.design.component.colorpicker.ColorChannel
import co.electriccoin.zcash.ui.design.component.colorpicker.filterHexInput
import co.electriccoin.zcash.ui.design.component.colorpicker.parseChannelInput
import co.electriccoin.zcash.ui.design.component.colorpicker.parseHexColor
import co.electriccoin.zcash.ui.design.component.colorpicker.spectrumColor
import co.electriccoin.zcash.ui.design.component.colorpicker.spectrumPosition
import co.electriccoin.zcash.ui.design.component.colorpicker.withChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.updateAndGet
import kotlin.math.roundToInt

/**
 * Picks a colour for [ColorPickerArgs] and reports through [NavigateToColorPickerUseCase]: every
 * change of the picked opaque colour as it happens, then one close, which keeps the last pick. The
 * toolbar's trailing button, back, a drag down or a tap outside close the sheet, and a sheet cleared
 * without a close, popped or replaced by another navigation, closes it too, so the caller's flow
 * always ends. Only the first close is reported, because the sheet also reports its own hiding once it
 * has gone, but every close pops the sheet while it is still on top, so a pop that never arrived does
 * not leave it stuck. A sheet restored without a caller collecting its picks, after process death,
 * closes itself.
 */
internal class ColorPickerVM(
    private val args: ColorPickerArgs,
    private val navigateToColorPicker: NavigateToColorPickerUseCase,
) : ViewModel() {
    private val selection = MutableStateFlow(ColorPickerSelection.initial(args.initialArgb?.let { Color(it) }))

    private var isClosed = false

    val state: StateFlow<ColorPickerState> =
        selection.map(::createState).stateIn(viewModel = this, initialValue = createState(selection.value))

    private fun createState(selection: ColorPickerSelection) =
        ColorPickerState.create(
            selection = selection,
            onTabSelect = ::onTabSelect,
            onColorSelect = ::pick,
            onSpectrumPick = ::onSpectrumPick,
            onChannelChange = ::onChannelChange,
            onFieldChange = ::onFieldChange,
            onBack = ::onClose,
        )

    init {
        if (!navigateToColorPicker.hasCollector()) onClose()
    }

    override fun onCleared() {
        reportClosed()
        super.onCleared()
    }

    private fun onTabSelect(tab: ColorPickerTab) = select { it.copy(tab = tab) }

    private fun onSpectrumPick(position: Offset) = pick(spectrumColor(position), spectrumPosition = position)

    private fun onChannelChange(
        channel: ColorChannel,
        value: Float
    ) = pick(selection.value.color.withChannel(channel, (value * MAX_CHANNEL).roundToInt()))

    private fun onFieldChange(
        field: ColorPickerField,
        text: String
    ) = select { current ->
        val channel = field.channel
        val draft = if (channel == null) filterHexInput(text) else text.filter(Char::isDigit).take(CHANNEL_DIGITS)
        val parsed =
            if (channel == null) {
                parseHexColor(draft)
            } else {
                parseChannelInput(draft)?.let { current.color.withChannel(channel, it) }
            }
        current.copy(
            color = parsed ?: current.color,
            spectrumPosition = parsed?.spectrumPosition() ?: current.spectrumPosition,
            drafts = mapOf(field to draft)
        )
    }

    private fun onClose() {
        reportClosed()
        navigateToColorPicker.dismiss(args)
    }

    /** Picks the opaque part of [color], which clears every half-typed field. */
    private fun pick(
        color: Color,
        spectrumPosition: Offset = color.spectrumPosition(),
    ) = select {
        it.copy(
            color = color.copy(alpha = 1f),
            spectrumPosition = spectrumPosition,
            drafts = emptyMap()
        )
    }

    /** Applies [transform] to the selection and reports the picked colour when it changed, until closed. */
    private fun select(transform: (ColorPickerSelection) -> ColorPickerSelection) {
        val before = selection.value.color
        val after = selection.updateAndGet(transform).color
        if (after != before && !isClosed) navigateToColorPicker.onPicked(after, args)
    }

    /** Ends the caller's flow on the last pick, once. */
    private fun reportClosed() {
        if (isClosed) return
        isClosed = true
        navigateToColorPicker.onClosed(args)
    }

    private companion object {
        const val MAX_CHANNEL = 255
        const val CHANNEL_DIGITS = 3
    }
}
