package co.electriccoin.zcash.ui.common.provider

import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.WalletCoordinator
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.model.SynchronizerError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

interface SynchronizerProvider {
    val error: StateFlow<SynchronizerError?>

    val synchronizer: StateFlow<Synchronizer?>

    /**
     * Get synchronizer and wait for it to be ready.
     */
    suspend fun getSynchronizer(): Synchronizer

    suspend fun getVotingWalletDbPath(): String

    fun resetSynchronizer()
}

class SynchronizerProviderImpl(
    private val walletCoordinator: WalletCoordinator
) : SynchronizerProvider {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override val error = MutableStateFlow<SynchronizerError?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val synchronizer: StateFlow<Synchronizer?> =
        walletCoordinator
            .synchronizer
            .flatMapLatest { synchronizer ->
                channelFlow {
                    if (synchronizer != null) {
                        val pipeline = initializeErrorHandling(synchronizer)

                        launch {
                            pipeline.collect { new ->
                                error.update { new }
                            }
                        }
                    }

                    send(synchronizer)
                    awaitClose {
                        synchronizer?.onProcessorErrorHandler = null
                        synchronizer?.onProcessorErrorResolved = null
                        synchronizer?.onSetupErrorHandler = null
                        synchronizer?.onChainErrorHandler = null
                    }
                }
            }.stateIn(
                scope = scope,
                started = SharingStarted.Lazily,
                initialValue = walletCoordinator.synchronizer.value
            )

    override suspend fun getSynchronizer(): Synchronizer =
        withContext(Dispatchers.IO) {
            synchronizer
                .filterNotNull()
                .first()
        }

    override suspend fun getVotingWalletDbPath(): String =
        getSynchronizer().getVotingWalletDbPathByReflection()

    override fun resetSynchronizer() {
        walletCoordinator.resetSynchronizer()
    }

    private fun initializeErrorHandling(synchronizer: Synchronizer): Flow<SynchronizerError?> {
        val pipeline = MutableStateFlow<SynchronizerError?>(null)

        // synchronizer.onCriticalErrorHandler = { error ->
        //     Twig.error { "WALLET - Error Critical: $error" }
        //     pipeline.update { SynchronizerError.Critical(error)}
        //     false
        // }
        synchronizer.onProcessorErrorHandler = { error ->
            Twig.error { "WALLET - Error Processor: $error" }
            pipeline.update { SynchronizerError.Processor(error) }
            true
        }
        synchronizer.onProcessorErrorResolved = {
            Twig.error { "WALLET - Processor error resolved" }
            pipeline.update { null }
        }
        synchronizer.onSetupErrorHandler = { error ->
            Twig.error { "WALLET - Error Setup: $error" }
            pipeline.update { SynchronizerError.Setup(error) }
            false
        }
        synchronizer.onChainErrorHandler = { x, y ->
            Twig.error { "WALLET - Error Chain: $x, $y" }
            pipeline.update { SynchronizerError.Chain(x, y) }
        }

        return pipeline
    }
}

@Suppress("TooGenericExceptionCaught")
private suspend fun Synchronizer.getVotingWalletDbPathByReflection(): String =
    suspendCoroutine { continuation ->
        try {
            val method = javaClass.methods.firstOrNull { method ->
                method.name.startsWith("getWalletDbPathForVoting") &&
                    method.parameterTypes.size == 1
            } ?: error("SDK synchronizer does not expose voting wallet DB path")
            val result = method.invoke(this, continuation)
            if (result !== COROUTINE_SUSPENDED) {
                continuation.resume(result as String)
            }
        } catch (exception: InvocationTargetException) {
            continuation.resumeWithException(exception.targetException ?: exception)
        } catch (throwable: Throwable) {
            continuation.resumeWithException(throwable)
        }
    }
