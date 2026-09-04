package co.electriccoin.zcash.preference

import android.content.Context
import co.electriccoin.zcash.spackle.Twig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal const val QUARANTINE_DIRECTORY = "encrypted_prefs_quarantine"
internal const val QUARANTINE_KEEP_NEWEST = 3

internal fun quarantineDirectory(context: Context): File = File(context.noBackupFilesDir, QUARANTINE_DIRECTORY)

internal fun sharedPreferencesDirectory(context: Context): File = File(context.filesDir.parent, "shared_prefs")

internal fun encryptedPreferencesFile(
    sharedPrefsDir: File,
    filename: String
): File = File(sharedPrefsDir, "$filename.xml")

internal fun encryptedPreferencesBackupFile(
    sharedPrefsDir: File,
    filename: String
): File = File(sharedPrefsDir, "$filename.xml.bak")

/**
 * Removes `<filename>.xml` and its `.xml.bak` sibling from [sharedPrefsDir]: the fallback when
 * quarantining them fails, and the cleanup for the empty file that clearing Android's in-memory
 * `SharedPreferences` cache commits back to the vacated path. A missing file is a no-op.
 */
internal fun deleteEncryptedPreferencesFiles(
    sharedPrefsDir: File,
    filename: String
) {
    encryptedPreferencesFile(sharedPrefsDir, filename).delete()
    encryptedPreferencesBackupFile(sharedPrefsDir, filename).delete()
}

/**
 * The persisted "already recreated once" guard for [filename]: its presence means a quarantine
 * already ran for this store and the recreated store has not yet opened successfully, so a later
 * launch must rethrow instead of quarantining again. See `AndroidPreferenceProvider.openEncrypted`.
 */
internal fun recreatedMarkerFile(
    quarantineDir: File,
    filename: String
): File = File(quarantineDir, "$filename.recreated")

/**
 * Moves `<filename>.xml` and its `<filename>.xml.bak` sibling (if present) out of
 * [sharedPrefsDir] into [quarantineDir], timestamped as `<filename>-<nowMillis>.xml[.bak]`, then
 * prunes older quarantined copies of this filename down to [keepNewest]. A no-op when neither
 * file is present.
 */
internal fun quarantineEncryptedPreferencesFiles(
    sharedPrefsDir: File,
    quarantineDir: File,
    filename: String,
    nowMillis: Long = System.currentTimeMillis(),
    keepNewest: Int = QUARANTINE_KEEP_NEWEST,
) {
    val xmlFile = encryptedPreferencesFile(sharedPrefsDir, filename)
    val bakFile = encryptedPreferencesBackupFile(sharedPrefsDir, filename)

    if (!xmlFile.exists() && !bakFile.exists()) {
        return
    }

    quarantineDir.mkdirs()

    val quarantinedXml = File(quarantineDir, "$filename-$nowMillis.xml")
    val quarantinedBak = File(quarantineDir, "$filename-$nowMillis.xml.bak")

    if (xmlFile.exists()) {
        moveFile(xmlFile, quarantinedXml)
    }
    if (bakFile.exists()) {
        moveFile(bakFile, quarantinedBak)
    }

    pruneQuarantine(quarantineDir, filename, keepNewest)
}

private fun moveFile(
    source: File,
    destination: File
) {
    if (!source.renameTo(destination)) {
        source.copyTo(destination, overwrite = true)
        if (!source.delete()) {
            Twig.error { "Failed to delete source file after copy: $source" }
        }
    }
}

/**
 * Keeps the [keepNewest] lexicographically-latest `<filename>-*.xml` quarantine entries (their
 * millisecond timestamps are fixed-width, so lexicographic order matches chronological order) and
 * deletes the rest, along with any `.bak` sibling. Other filenames' entries, including this
 * filename's [recreatedMarkerFile], are left untouched: the marker is named `<filename>.recreated`,
 * which never matches the `<filename>-*.xml` pattern.
 */
internal fun pruneQuarantine(
    quarantineDir: File,
    filename: String,
    keepNewest: Int
) {
    val prefix = "$filename-"
    val xmlFiles =
        quarantineDir.listFiles { file -> file.name.startsWith(prefix) && file.name.endsWith(".xml") }
            ?: return

    xmlFiles
        .sortedByDescending { it.name }
        .drop(keepNewest)
        .forEach { xmlFile ->
            xmlFile.delete()
            File(quarantineDir, "${xmlFile.name}.bak").delete()
        }
}

/**
 * Empties [quarantineDir] wholesale — every filename's set-aside copies and every
 * [recreatedMarkerFile] guard, not just the entries [pruneQuarantine] would age out. Used by
 * "Reset Zodl" so wiping the wallet also removes any ciphertext left behind by an earlier recovery.
 */
internal suspend fun purgeEncryptedPreferencesQuarantine(quarantineDir: File) {
    withContext(Dispatchers.IO) {
        quarantineDir.deleteRecursively()
    }
}
