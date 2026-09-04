package co.electriccoin.zcash.preference

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncryptedPreferenceQuarantineTest {
    private lateinit var root: File
    private lateinit var sharedPrefsDir: File
    private lateinit var quarantineDir: File

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("encrypted_prefs_quarantine_test").toFile()
        sharedPrefsDir = File(root, "shared_prefs").apply { mkdirs() }
        quarantineDir = File(root, QUARANTINE_DIRECTORY)
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `moves the xml and bak files with timestamped names and preserved content`() {
        encryptedPreferencesFile(sharedPrefsDir, "co.electriccoin.zcash.encrypted").writeText("xml content")
        encryptedPreferencesBackupFile(sharedPrefsDir, "co.electriccoin.zcash.encrypted").writeText("bak content")

        quarantineEncryptedPreferencesFiles(
            sharedPrefsDir = sharedPrefsDir,
            quarantineDir = quarantineDir,
            filename = "co.electriccoin.zcash.encrypted",
            nowMillis = 1_700_000_000_000L,
        )

        val quarantinedXml = File(quarantineDir, "co.electriccoin.zcash.encrypted-1700000000000.xml")
        val quarantinedBak = File(quarantineDir, "co.electriccoin.zcash.encrypted-1700000000000.xml.bak")

        assertEquals("xml content", quarantinedXml.readText())
        assertEquals("bak content", quarantinedBak.readText())
        assertFalse(encryptedPreferencesFile(sharedPrefsDir, "co.electriccoin.zcash.encrypted").exists())
        assertFalse(encryptedPreferencesBackupFile(sharedPrefsDir, "co.electriccoin.zcash.encrypted").exists())
    }

    @Test
    fun `does nothing when neither file exists`() {
        quarantineEncryptedPreferencesFiles(
            sharedPrefsDir = sharedPrefsDir,
            quarantineDir = quarantineDir,
            filename = "missing",
            nowMillis = 1_700_000_000_000L,
        )

        assertFalse(quarantineDir.exists())
    }

    @Test
    fun `prune keeps only the newest entries for the filename and leaves others alone`() {
        quarantineDir.mkdirs()
        (1L..5L).forEach { millis ->
            File(quarantineDir, "target-$millis.xml").writeText("v$millis")
            File(quarantineDir, "target-$millis.xml.bak").writeText("v$millis-bak")
        }
        File(quarantineDir, "other-9.xml").writeText("other")

        pruneQuarantine(quarantineDir, "target", keepNewest = 3)

        val remaining =
            quarantineDir
                .listFiles()
                ?.map { it.name }
                ?.toSet()
                .orEmpty()

        assertEquals(
            setOf(
                "target-3.xml",
                "target-3.xml.bak",
                "target-4.xml",
                "target-4.xml.bak",
                "target-5.xml",
                "target-5.xml.bak",
                "other-9.xml"
            ),
            remaining
        )
    }

    @Test
    fun `prune leaves the recreated marker for the filename untouched`() {
        quarantineDir.mkdirs()
        val marker = recreatedMarkerFile(quarantineDir, "target").apply { writeText("marker") }
        (1L..5L).forEach { millis ->
            File(quarantineDir, "target-$millis.xml").writeText("v$millis")
        }

        pruneQuarantine(quarantineDir, "target", keepNewest = 3)

        assertTrue(marker.exists())
    }

    /**
     * The recovery sequence clears Android's in-memory `SharedPreferences` cache after the move,
     * which commits an empty file back to the vacated path. When the recreate that follows fails,
     * that file must already be gone, or the next launch would quarantine an empty file instead of
     * letting the marker guard rethrow.
     */
    @Test
    fun `the empty file left by clearing the cache is not quarantined on the next launch`() {
        encryptedPreferencesFile(sharedPrefsDir, "target").writeText("corrupted")
        encryptedPreferencesBackupFile(sharedPrefsDir, "target").writeText("corrupted bak")

        quarantineEncryptedPreferencesFiles(
            sharedPrefsDir = sharedPrefsDir,
            quarantineDir = quarantineDir,
            filename = "target",
            nowMillis = 1_700_000_000_000L,
        )
        recreatedMarkerFile(quarantineDir, "target").createNewFile()
        encryptedPreferencesFile(sharedPrefsDir, "target").writeText("<map />")

        deleteEncryptedPreferencesFiles(sharedPrefsDir, "target")

        quarantineEncryptedPreferencesFiles(
            sharedPrefsDir = sharedPrefsDir,
            quarantineDir = quarantineDir,
            filename = "target",
            nowMillis = 1_700_000_001_000L,
        )

        assertEquals(
            setOf(
                "target-1700000000000.xml",
                "target-1700000000000.xml.bak",
                "target.recreated"
            ),
            quarantineDir
                .listFiles()
                ?.map { it.name }
                ?.toSet()
                .orEmpty()
        )
    }

    @Test
    fun `purging removes every quarantined copy and every marker`() =
        runTest {
            quarantineDir.mkdirs()
            File(quarantineDir, "target-1.xml").writeText("v1")
            File(quarantineDir, "target-1.xml.bak").writeText("v1-bak")
            File(quarantineDir, "other-9.xml").writeText("other")
            recreatedMarkerFile(quarantineDir, "target").createNewFile()

            purgeEncryptedPreferencesQuarantine(quarantineDir)

            assertFalse(quarantineDir.exists())
        }
}
