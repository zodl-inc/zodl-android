@file:Suppress("DEPRECATION")

package co.electriccoin.zcash.preference

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import co.electriccoin.zcash.preference.test.fixture.StringDefaultPreferenceFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.KeyStore

// Areas that are not covered yet:
// 1. Test observer behavior
class EncryptedPreferenceProviderTest {
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
    fun put_and_get_string() =
        runBlocking {
            val expectedValue = StringDefaultPreferenceFixture.DEFAULT_VALUE + "extra"

            val preferenceProvider =
                new().apply {
                    putString(StringDefaultPreferenceFixture.KEY, expectedValue)
                }

            assertEquals(expectedValue, StringDefaultPreferenceFixture.new().getValue(preferenceProvider))
        }

    @Test
    @SmallTest
    fun hasKey_false() =
        runBlocking {
            val preferenceProvider = new()

            assertFalse(preferenceProvider.hasKey(StringDefaultPreferenceFixture.new().key))
        }

    @Test
    @SmallTest
    fun put_and_check_key() =
        runBlocking {
            val expectedValue = StringDefaultPreferenceFixture.DEFAULT_VALUE + "extra"

            val preferenceProvider =
                new().apply {
                    putString(StringDefaultPreferenceFixture.KEY, expectedValue)
                }

            assertTrue(preferenceProvider.hasKey(StringDefaultPreferenceFixture.new().key))
        }

    // Note: this test case relies on undocumented implementation details of SharedPreferences
    // e.g. the directory path and the fact the preferences are stored as XML
    @Test
    @SmallTest
    fun verify_no_plaintext() =
        runBlocking {
            val expectedValue = StringDefaultPreferenceFixture.DEFAULT_VALUE + "extra"

            new().apply {
                putString(StringDefaultPreferenceFixture.KEY, expectedValue)
            }

            val text =
                File(
                    File(ApplicationProvider.getApplicationContext<Context>().dataDir, "shared_prefs"),
                    "$FILENAME.xml"
                ).readText()

            assertFalse(text.contains(expectedValue))
            assertFalse(text.contains(StringDefaultPreferenceFixture.KEY.key))
        }

    @Test
    @SmallTest
    fun graceful_recovery_when_encrypted_prefs_are_corrupted() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()

            // Simulate D2D transfer state: prefs file contains a keyset that was encrypted
            // with a Keystore key from the source device (which doesn't exist on this device).
            // We reproduce this by writing garbage to the known keyset keys in raw SharedPreferences.
            // Note: emulator Keystore is software-backed and doesn't faithfully reproduce
            // hardware key invalidation, so we corrupt the raw data directly instead.
            context
                .getSharedPreferences(RECOVERY_FILENAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_KEYSET, "corrupted_keyset_data")
                .putString(VALUE_KEYSET, "corrupted_keyset_data")
                .commit()

            val restoredProvider =
                AndroidPreferenceProvider.newEncrypted(context, RECOVERY_FILENAME)

            // Prefs are empty: user will re-enter seed phrase to recover wallet
            assertFalse(restoredProvider.hasKey(StringDefaultPreferenceFixture.KEY))

            val quarantined = quarantinedFiles(context, RECOVERY_FILENAME)
            assertEquals(1, quarantined.size)
            assertTrue(quarantined.single().readText().contains("corrupted_keyset_data"))

            assertFalse(sharedPrefsBakFile(context, RECOVERY_FILENAME).exists())
            assertTrue(androidKeyStoreAlias().containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS))
            assertFalse(recreatedMarkerFile(quarantineDirectory(context), RECOVERY_FILENAME).exists())
        }

    @Test
    @SmallTest
    fun graceful_recovery_when_master_key_is_lost_in_migration() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()

            // Faithful D2D simulation: the prefs file holds keysets encrypted with a Keystore
            // master key, and that hardware-bound key does not exist on the target device.
            // We reproduce this by writing through EncryptedSharedPreferences directly (bypassing
            // the factory cache) and then deleting the master key from the Keystore.
            val masterKey =
                MasterKey
                    .Builder(context)
                    .apply { setKeyScheme(MasterKey.KeyScheme.AES256_GCM) }
                    .build()
            EncryptedSharedPreferences
                .create(
                    context,
                    D2D_FILENAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).edit()
                .putString(StringDefaultPreferenceFixture.KEY.key, "secret")
                .commit()

            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }

            val restoredProvider = AndroidPreferenceProvider.newEncrypted(context, D2D_FILENAME)

            // Prefs are empty: user will re-enter seed phrase to recover wallet
            assertFalse(restoredProvider.hasKey(StringDefaultPreferenceFixture.KEY))

            val quarantined = quarantinedFiles(context, D2D_FILENAME)
            assertEquals(1, quarantined.size)

            assertTrue(androidKeyStoreAlias().containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS))
            assertFalse(recreatedMarkerFile(quarantineDirectory(context), D2D_FILENAME).exists())

            val expectedValue = StringDefaultPreferenceFixture.DEFAULT_VALUE + "restored"
            restoredProvider.putString(StringDefaultPreferenceFixture.KEY, expectedValue)
            assertEquals(expectedValue, StringDefaultPreferenceFixture.new().getValue(restoredProvider))
        }

    companion object {
        private const val FILENAME = "encrypted_preference_test"
        private const val RECOVERY_FILENAME = "encrypted_preference_recovery_test"
        private const val D2D_FILENAME = "encrypted_preference_d2d_test"

        /**
         * Internal keyset key names used by EncryptedSharedPreferences to store Tink keysets.
         * These are not part of the public API — if androidx.security.crypto renames them, this
         * test will silently stop simulating corruption (it will just test a normal open instead).
         */
        private const val KEY_KEYSET = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
        private const val VALUE_KEYSET = "__androidx_security_crypto_encrypted_prefs_value_keyset__"

        private suspend fun new() =
            AndroidPreferenceProvider.newEncrypted(
                ApplicationProvider.getApplicationContext(),
                FILENAME
            )

        private fun androidKeyStoreAlias(): KeyStore =
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        private fun sharedPrefsBakFile(
            context: Context,
            filename: String
        ) = encryptedPreferencesBackupFile(sharedPreferencesDirectory(context), filename)

        private fun quarantinedFiles(
            context: Context,
            filename: String
        ): List<File> {
            val quarantineDir = quarantineDirectory(context)
            return quarantineDir
                .listFiles { file -> file.name.startsWith("$filename-") && file.name.endsWith(".xml") }
                ?.toList()
                .orEmpty()
        }
    }
}
