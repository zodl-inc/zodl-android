package co.electriccoin.zcash.ui.common.usecase

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.screen.colorpicker.ColorPickerArgs
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.transformWhile

/**
 * Opens the colour picker sheet seeded with [invoke]'s initial colour each time the returned flow is
 * collected, under a request id of that collection's own, and streams the selection live: every
 * colour picked while the sheet is open. The flow ends whenever the sheet closes, however it closes,
 * so its last value is the last colour picked; nothing is restored. The pipeline is subscribed
 * before the sheet is forwarded to, so an event can never be emitted with nobody listening, and only
 * the events carrying this request's id count. Events are sent without suspending, so the sheet's
 * view model can still send one from `onCleared`; the buffer drops its oldest pick rather than the
 * closing event.
 */
class NavigateToColorPickerUseCase(
    private val navigationRouter: NavigationRouter
) {
    private val pipeline =
        MutableSharedFlow<ColorPickerEvent>(
            extraBufferCapacity = BUFFER,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    operator fun invoke(initial: Color?): Flow<Color> =
        flow {
            val args = ColorPickerArgs(initialArgb = initial?.toArgb())
            emitAll(
                pipeline
                    .onSubscription { navigationRouter.forward(args) }
                    .filter { it.args.requestId == args.requestId }
                    .transformWhile { event ->
                        if (event is ColorPickerEvent.Picked) emit(event.color)
                        event is ColorPickerEvent.Picked
                    }
            )
        }

    /**
     * Whether a caller is collecting a picker's selection. A sheet restored from a saved back stack
     * after process death has none, so its picks would reach nobody.
     */
    fun hasCollector(): Boolean = pipeline.subscriptionCount.value > 0

    /** [color] was picked on the open sheet. */
    fun onPicked(
        color: Color,
        args: ColorPickerArgs
    ) {
        pipeline.tryEmit(ColorPickerEvent.Picked(color = color, args = args))
    }

    /**
     * Pops the sheet of [args] when it is the current destination and nothing otherwise, so a close
     * that comes twice, late, or after the sheet already left never pops another screen.
     */
    fun dismiss(args: ColorPickerArgs) {
        navigationRouter.custom { current ->
            NavigationCommand.Back.takeIf { current?.isSheetOf(args) == true }
        }
    }

    /** The sheet already left the back stack: ends the caller's flow on the last picked colour. */
    fun onClosed(args: ColorPickerArgs) {
        pipeline.tryEmit(ColorPickerEvent.Closed(args))
    }

    private fun NavBackStackEntry.isSheetOf(args: ColorPickerArgs) =
        destination.hasRoute<ColorPickerArgs>() && toRoute<ColorPickerArgs>().requestId == args.requestId

    private companion object {
        const val BUFFER = 16
    }
}

private sealed interface ColorPickerEvent {
    val args: ColorPickerArgs

    data class Picked(
        val color: Color,
        override val args: ColorPickerArgs
    ) : ColorPickerEvent

    data class Closed(
        override val args: ColorPickerArgs
    ) : ColorPickerEvent
}
