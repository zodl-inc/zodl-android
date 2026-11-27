package co.electriccoin.zcash.ui.common.provider

import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface ApplicationStateProvider {
    val isInForeground: Flow<Boolean>

    fun onThirdPartyUiShown()

    fun onApplicationLifecycleChanged(event: Lifecycle.Event)

    fun observeOnForeground(): Flow<Unit>
}

class ApplicationStateProviderImpl : ApplicationStateProvider {
    // Default BACKGROUND, not foreground. The lifecycle observer (ApplicationStateRepository) replays
    // the real state on attach: a genuine foreground launch immediately gets ON_START → true. But a
    // COLD PROCESS START IN THE BACKGROUND (e.g. WorkManager waking the app for a migration lane) has
    // no Activity, so ProcessLifecycleOwner never reaches STARTED and ON_START never fires — with a
    // `true` default the app then believed it was foreground, called synchronizer.onForeground(), and
    // left the slipstream engine following the tip continuously in the background. That continuous
    // follow blew past Android's background-CPU cap → SIGKILL → restart → repeat, and starved the
    // migration broadcast lane of its privacy quiet-gap (observed live 2026-07-29). Defaulting to
    // background makes a headless start correctly `onBackground()` (engine stopped; only the bounded
    // Lane A sync bursts run). observeOnForeground()'s false→true edge now also fires on the first
    // real foreground, which is the correct behavior for its consumers.
    private val state = MutableStateFlow(ApplicationState(isAppInForeground = false, isThirdPartyUiShown = false))

    override val isInForeground: Flow<Boolean> = state.map { it.isActuallyInForeground }.distinctUntilChanged()

    override fun onApplicationLifecycleChanged(event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_START) {
            state.update {
                it.copy(
                    isAppInForeground = true,
                    isThirdPartyUiShown = false,
                )
            }
        } else if (event == Lifecycle.Event.ON_STOP) {
            state.update {
                it.copy(
                    isAppInForeground = it.isThirdPartyUiShown
                )
            }
        }
    }

    override fun observeOnForeground(): Flow<Unit> =
        channelFlow {
            launch {
                var previous = state.value.isActuallyInForeground
                isInForeground.collect { isForeground ->
                    if (isForeground && !previous) {
                        send(Unit)
                    }
                    previous = isForeground
                }
            }
            awaitClose {
                // do nothing
            }
        }

    override fun onThirdPartyUiShown() {
        state.update { it.copy(isThirdPartyUiShown = true) }
    }
}

private data class ApplicationState(
    val isAppInForeground: Boolean,
    val isThirdPartyUiShown: Boolean,
) {
    val isActuallyInForeground = isAppInForeground || isThirdPartyUiShown
}
