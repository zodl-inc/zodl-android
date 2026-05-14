package co.electriccoin.zcash.crash

import co.electriccoin.zcash.spackle.io.canWriteSuspend
import co.electriccoin.zcash.spackle.io.existsSuspend
import co.electriccoin.zcash.spackle.io.isDirectorySuspend
import co.electriccoin.zcash.spackle.io.mkdirsSuspend
import java.io.File
import java.util.UUID

object ExceptionPath {
    const val LOG_DIRECTORY_NAME = "log" // $NON-NLS-1$
    const val EXCEPTION_DIRECTORY_NAME = "exception" // $NON-NLS-1$
    const val SEPARATOR = "|"
    const val TYPE = "txt"

    @Suppress("MaxLineLength")
    fun newExceptionFileName(
        exception: ReportableException,
        uuid: UUID = UUID.randomUUID()
    ) = "${exception.time.epochSeconds}$SEPARATOR$uuid$SEPARATOR${exception.exceptionClass}$SEPARATOR${exception.isUncaught}.$TYPE"

    // The exceptions are really just for debugging
    @Suppress("ThrowsCount")
    suspend fun validateDir(path: File) {
        if (!path.existsSuspend()) {
            if (!path.mkdirsSuspend()) {
                throw IllegalArgumentException("Directories couldn't be created")
            }
        } else {
            if (!path.isDirectorySuspend()) {
                throw IllegalArgumentException("Path is a file when a directory was expected")
            }
        }

        if (!path.canWriteSuspend()) {
            throw IllegalArgumentException("Path is not writeable")
        }
    }
}
