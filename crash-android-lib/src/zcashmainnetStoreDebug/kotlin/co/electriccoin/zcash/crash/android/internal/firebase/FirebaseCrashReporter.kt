@file:JvmName("FirebaseCrashReporterKt")

package co.electriccoin.zcash.crash.android.internal.firebase

import android.content.Context
import androidx.annotation.AnyThread
import co.electriccoin.zcash.crash.android.R
import co.electriccoin.zcash.crash.android.internal.CrashReporter
import co.electriccoin.zcash.spackle.FirebaseTestLabUtil
import co.electriccoin.zcash.spackle.SuspendingLazy
import co.electriccoin.zcash.spackle.Twig
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.installations.FirebaseInstallations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * Registers an exception handler with Firebase Crashlytics.
 */
internal class FirebaseCrashReporter(
    context: Context
) : CrashReporter {
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private val analyticsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val initFirebaseJob: Deferred<CrashReporter?> =
        analyticsScope.async {
            FirebaseCrashReporterImpl.getInstance(context)
        }

    @AnyThread
    override fun reportCaughtException(exception: Throwable) {
        initFirebaseJob.invokeOnCompletionWithResult {
            it?.reportCaughtException(exception)
        }
    }

    override fun enable() {
        initFirebaseJob.invokeOnCompletionWithResult {
            it?.enable()
        }
    }

    override fun disableAndDelete() {
        initFirebaseJob.invokeOnCompletionWithResult {
            it?.disableAndDelete()
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> Deferred<T>.invokeOnCompletionWithResult(handler: (T) -> Unit) {
    invokeOnCompletion {
        handler(this.getCompleted())
    }
}

/**
 * Registers an exception handler with Firebase Crashlytics.
 */
private class FirebaseCrashReporterImpl(
    private val firebaseCrashlytics: FirebaseCrashlytics,
    private val firebaseInstallations: FirebaseInstallations
) : CrashReporter {
    @AnyThread
    override fun reportCaughtException(exception: Throwable) {
        error(
            "Although most of the sensitive model objects implement custom [toString] methods to redact information" +
                " if they were to be logged (which includes exceptions), we're encouraged to disable caught exception" +
                " reporting to the remote Crashlytics service due to its security risk. Use the the local variant of" +
                " the reporter to report caught exception - [LocalCrashReporter]."
        )
    }

    override fun enable() {
        firebaseCrashlytics.isCrashlyticsCollectionEnabled = true
    }

    override fun disableAndDelete() {
        firebaseCrashlytics.isCrashlyticsCollectionEnabled = false
        firebaseCrashlytics.deleteUnsentReports()
        firebaseInstallations.delete()
    }

    companion object {
        /*
         * Note there is a tradeoff with the suspending implementation.  In order to avoid disk IO
         * on the main thread, there is a brief timing gap during application startup where very
         * early crashes may be missed.  This is a tradeoff we are willing to make in order to avoid
         * ANRs.
         */
        private val lazyWithArgument =
            SuspendingLazy<Context, CrashReporter?> {
                if (it.resources.getBoolean(R.bool.co_electriccoin_zcash_crash_is_firebase_enabled)) {

                    // Workaround for disk IO on main thread in Firebase initialization
                    val firebaseApp = FirebaseAppCache.getFirebaseApp(it)
                    if (firebaseApp == null) {
                        Twig.warn { "Unable to initialize Crashlytics. FirebaseApp is null" }
                        return@SuspendingLazy null
                    }

                    val firebaseInstallations = FirebaseInstallations.getInstance(firebaseApp)
                    val firebaseCrashlytics =
                        FirebaseCrashlytics.getInstance().apply {
                            setCustomKey(
                                CrashlyticsUserProperties.IS_TEST,
                                FirebaseTestLabUtil.isFirebaseTestLab(it)
                            )
                        }

                    FirebaseCrashReporterImpl(firebaseCrashlytics, firebaseInstallations)
                } else {
                    Twig.warn { "Unable to initialize Crashlytics. Configure API keys in the app module" }
                    null
                }
            }

        suspend fun getInstance(context: Context): CrashReporter? = lazyWithArgument.getInstance(context)
    }
}

internal object CrashlyticsUserProperties {
    /**
     * Flags a crash as occurring in a test environment.  Set automatically to detect Firebase Test Lab and emulator.wtf
     */
    const val IS_TEST = "is_test" // $NON-NLS
}
