package co.electriccoin.zcash.test

import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.runner.AndroidJUnitRunner

open class ZcashUiTestRunner : AndroidJUnitRunner() {
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)

        val powerManager =
            ApplicationProvider
                .getApplicationContext<Context>()
                .getSystemService(Context.POWER_SERVICE) as PowerManager

        // There is no alternative to this deprecated API.  The suggestion of a view to keep the screen
        // on won't work well for our tests.
        @Suppress("DEPRECATION")
        val flags = PowerManager.FULL_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE
        wakeLock = powerManager.newWakeLock(flags, "zcash:keep_screen_on_for_tests")
        wakeLock.acquire()
    }

    override fun onDestroy() {
        super.onDestroy()

        wakeLock.release()
    }
}
