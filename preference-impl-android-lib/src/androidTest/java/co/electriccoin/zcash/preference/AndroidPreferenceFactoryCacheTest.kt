package co.electriccoin.zcash.preference

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class AndroidPreferenceFactoryCacheTest {
    /*
     * Note: This test relies on Test Orchestrator to avoid issues with multiple runs. Specifically,
     * it purges the preference file and avoids corruption due to multiple instances of the
     * EncryptedPreferenceProvider.
     */

    private var isRun = false

    @Before
    fun checkUsingOrchestrator() {
        check(!isRun) {
            "State appears to be retained between test method invocations; verify that Test Orchestrator " +
                "is enabled and then re-run the tests"
        }

        isRun = true
    }

    @Test
    @SmallTest
    fun newStandard_caches_instance_per_filename() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()

            val first = AndroidPreferenceProvider.newStandard(context, STANDARD_FILENAME)
            val second = AndroidPreferenceProvider.newStandard(context, STANDARD_FILENAME)

            assertSame(first, second)
        }

    @Test
    @SmallTest
    fun newEncrypted_caches_instance_per_filename() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()

            val first = AndroidPreferenceProvider.newEncrypted(context, ENCRYPTED_FILENAME)
            val second = AndroidPreferenceProvider.newEncrypted(context, ENCRYPTED_FILENAME)

            assertSame(first, second)
        }

    @Test
    @SmallTest
    fun newStandard_and_newEncrypted_do_not_share_an_instance_for_the_same_filename() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()

            val standard = AndroidPreferenceProvider.newStandard(context, SHARED_FILENAME)
            val encrypted = AndroidPreferenceProvider.newEncrypted(context, SHARED_FILENAME)

            assertNotSame(standard, encrypted)
        }

    companion object {
        private const val STANDARD_FILENAME = "preference_provider_cache_standard_test"
        private const val ENCRYPTED_FILENAME = "preference_provider_cache_encrypted_test"
        private const val SHARED_FILENAME = "preference_provider_cache_shared_test"
    }
}
