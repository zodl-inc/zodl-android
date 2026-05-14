package co.electriccoin.zcash.ui.common.usecase

import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.provider.KeystoneSDKException
import co.electriccoin.zcash.ui.common.provider.KeystoneSDKProvider
import co.electriccoin.zcash.ui.common.repository.KeystoneProposalRepository
import com.keystone.module.DecodeResult
import com.sparrowwallet.hummingbird.UR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ParseKeystonePCZTUseCase(
    private val submitKSProposal: SubmitKSProposalUseCase,
    private val keystoneProposalRepository: KeystoneProposalRepository,
    keystoneSDKProvider: KeystoneSDKProvider,
) : BaseKeystoneScanner(keystoneSDKProvider) {
    override suspend fun onSuccess(ur: UR) {
        keystoneProposalRepository.parsePCZT(ur)
        submitKSProposal()
    }
}

abstract class BaseKeystoneScanner(
    protected val keystoneSDKProvider: KeystoneSDKProvider,
) {
    private val mutex = Mutex()

    private var activeScanSessionId: String? = null
    private var latestResult: ParseKeystoneQrResult? = null

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend operator fun invoke(
        result: String,
        scanSessionId: String? = null
    ): ParseKeystoneQrResult =
        withSemaphore {
            resetDecoderForNewSession(scanSessionId)
            val latest = latestResult
            if (latest != null && latest.isFinished) {
                latest
            } else {
                val decodedResult = decodeResult(result)
                val ur = decodedResult.ur

                Twig.info { "=========> progress ur: ${decodedResult.progress}" }

                val new =
                    if (ur != null) {
                        try {
                            onSuccess(ur)
                            ParseKeystoneQrResult(
                                progress = decodedResult.progress,
                                isFinished = true
                            )
                        } catch (e: Exception) {
                            @Suppress("TooGenericExceptionCaught", "SwallowedException")
                            try {
                                keystoneSDKProvider.resetQRDecoder()
                            } catch (resetException: KeystoneSDKException) {
                                Twig.warn(resetException) { "Failed to reset QR decoder" }
                            }
                            latestResult =
                                ParseKeystoneQrResult(
                                    progress = 0,
                                    isFinished = false
                                )
                            throw e
                        }
                    } else {
                        ParseKeystoneQrResult(
                            progress = decodedResult.progress,
                            isFinished = false
                        )
                    }
                latestResult = new
                new
            }
        }

    abstract suspend fun onSuccess(ur: UR)

    private fun resetDecoderForNewSession(scanSessionId: String?) {
        val requestedSessionId = scanSessionId ?: DEFAULT_SCAN_SESSION_ID
        val previousSessionId = activeScanSessionId
        if (previousSessionId == requestedSessionId) {
            return
        }

        activeScanSessionId = requestedSessionId
        latestResult = null
        if (previousSessionId == null) {
            return
        }
        try {
            keystoneSDKProvider.resetQRDecoder()
        } catch (resetException: KeystoneSDKException) {
            Twig.warn(resetException) { "Failed to reset QR decoder for new scan session" }
        }
    }

    private fun decodeResult(result: String): DecodeResult =
        try {
            keystoneSDKProvider.decodeQR(result)
        } catch (_: KeystoneSDKException) {
            throw InvalidKeystonePCZTQRException()
        }

    private suspend fun <T> withSemaphore(block: suspend () -> T) =
        mutex.withLock {
            withContext(Dispatchers.Default) {
                block()
            }
        }

    private companion object {
        const val DEFAULT_SCAN_SESSION_ID = "default"
    }
}

class InvalidKeystonePCZTQRException : Exception()

data class ParseKeystoneQrResult(
    val progress: Int,
    val isFinished: Boolean
)
