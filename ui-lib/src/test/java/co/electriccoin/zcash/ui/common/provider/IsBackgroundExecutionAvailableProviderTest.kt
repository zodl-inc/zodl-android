package co.electriccoin.zcash.ui.common.provider

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [isBackgroundExecutionAvailable] must combine both OS-level signals — the battery-optimization
 * exemption AND the (API 28+) background-restriction flag — rather than relying on either alone,
 * since a device can be Doze-exempt yet still be background-restricted by the OEM or the user
 * (App Info > Battery > "Restricted").
 */
class IsBackgroundExecutionAvailableProviderTest {
    @Test
    fun exemptAndNotRestrictedIsAvailable() {
        val result =
            isBackgroundExecutionAvailable(
                isIgnoringBatteryOptimizations = true,
                isBackgroundRestricted = false,
                sdkInt = 28,
            )
        assertTrue(result)
    }

    @Test
    fun notExemptIsUnavailable() {
        val result =
            isBackgroundExecutionAvailable(
                isIgnoringBatteryOptimizations = false,
                isBackgroundRestricted = false,
                sdkInt = 28,
            )
        assertFalse(result)
    }

    @Test
    fun exemptButBackgroundRestrictedIsUnavailable() {
        // A device can be Doze-exempt yet still be OS/OEM background-restricted — that stronger
        // restriction must still make this unavailable.
        val result =
            isBackgroundExecutionAvailable(
                isIgnoringBatteryOptimizations = true,
                isBackgroundRestricted = true,
                sdkInt = 28,
            )
        assertFalse(result)
    }

    @Test
    fun neitherIsUnavailable() {
        val result =
            isBackgroundExecutionAvailable(
                isIgnoringBatteryOptimizations = false,
                isBackgroundRestricted = true,
                sdkInt = 28,
            )
        assertFalse(result)
    }

    @Test
    fun belowApi28IgnoresBackgroundRestrictedFlag() {
        // Below API 28, isBackgroundRestricted() doesn't exist on the platform — the signal must be
        // ignored entirely rather than (incorrectly) treated as restricted.
        val result =
            isBackgroundExecutionAvailable(
                isIgnoringBatteryOptimizations = true,
                isBackgroundRestricted = true,
                sdkInt = 26,
            )
        assertTrue(result)
    }

    @Test
    fun providerDelegatesToPowerManagerAndActivityManager() {
        val powerManager =
            mockk<PowerManager> {
                every { isIgnoringBatteryOptimizations(any()) } returns true
            }
        val activityManager =
            mockk<ActivityManager> {
                every { isBackgroundRestricted } returns false
            }
        // Relaxed so the unrelated getSharedPreferences() call made by the DEBUG-only
        // DebugForceBackgroundExecutionUnavailable override check doesn't need its own stub here.
        val context =
            mockk<Context>(relaxed = true) {
                every { packageName } returns "co.electriccoin.zcash.test"
                every { getSystemService(Context.POWER_SERVICE) } returns powerManager
                every { getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
            }

        val result = IsBackgroundExecutionAvailableProvider(context).isAvailable()

        // Build.VERSION.SDK_INT is 0 in this plain-JVM unit test, so isBackgroundRestricted is
        // ignored by isBackgroundExecutionAvailable() regardless of the mocked value above — this
        // only pins down that the provider wires the two system services through correctly.
        assertTrue(result)
    }
}
