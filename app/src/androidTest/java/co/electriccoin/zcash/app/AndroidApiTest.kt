package co.electriccoin.zcash.app

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidApiTest {
    @Test
    @SmallTest
    fun checkTargetApi() {
        // This test case prevents accidental release of the app targeting a newer API level than
        // we currently support.  Don't change this unless you're absolutely sure we're ready to
        // target the new API level.
        assertThat(
            ApplicationProvider.getApplicationContext<Application>().applicationInfo.targetSdkVersion,
            lessThanOrEqualTo(Build.VERSION_CODES.BAKLAVA)
        )
    }

    @Test
    @SmallTest
    fun checkMinApi() {
        // This test case prevents accidental release of the app with a different API level than we
        // have currently set in gradle.properties. It could impact the app's functionality. Don't
        // change this unless you're absolutely sure we're ready to set a new API level.
        assertEquals(
            ApplicationProvider.getApplicationContext<Application>().applicationInfo.minSdkVersion,
            Build.VERSION_CODES.O_MR1
        )
    }
}
