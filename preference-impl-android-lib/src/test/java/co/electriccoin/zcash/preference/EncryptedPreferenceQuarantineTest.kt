package co.electriccoin.zcash.preference

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
}
