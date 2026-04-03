package co.electriccoin.zcash.ui.common.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.sdk.ANDROID_STATE_FLOW_TIMEOUT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

fun <T> ViewModel.mutableLce(initial: Lce<T>? = null) = MutableLce<T>(viewModelScope, initial)

fun <T> Flow<T>.stateIn(
    viewModel: ViewModel,
    initialValue: T,
    started: SharingStarted = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
): StateFlow<T> = stateIn(viewModel.viewModelScope, started, initialValue)

fun <T> Flow<T>.stateIn(
    viewModel: ViewModel,
    started: SharingStarted = SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
): StateFlow<T?> = stateIn(viewModel.viewModelScope, started, null)

class MutableLce<T>(
    private val scope: CoroutineScope,
    initial: Lce<T>? = null,
) {
    private val _state = MutableStateFlow(initial ?: Lce())
    val state: StateFlow<Lce<T>> = _state.asStateFlow()

    private var job: Job? = null

    @Suppress("TooGenericExceptionCaught")
    fun execute(block: suspend () -> T) {
        job?.cancel()
        _state.update { it.copy(loading = true, content = it.content.takeUnless { c -> c is LceContent.Error }) }
        job =
            scope.launch {
                try {
                    val result = block()
                    _state.value = Lce(loading = false, content = LceContent.Success(result))
                } catch (e: CancellationException) {
                    _state.update { it.copy(loading = false) }
                    throw e
                } catch (e: Throwable) {
                    _state.update {
                        it.copy(
                            loading = false,
                            content =
                                LceContent.Error(
                                    cause = e,
                                    restart = { execute(block) },
                                    dismiss = { _state.update { it.copy(content = null) } }
                                )
                        )
                    }
                }
            }
    }
}
