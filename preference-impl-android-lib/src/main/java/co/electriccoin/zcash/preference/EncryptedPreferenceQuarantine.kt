package co.electriccoin.zcash.preference

import android.content.Context
import java.io.File

internal const val QUARANTINE_DIRECTORY = "encrypted_prefs_quarantine"
internal const val QUARANTINE_KEEP_NEWEST = 3

/**
 * Moves `<filename>.xml` and its `<filename>.xml.bak` sibling (if present) out of
 * [sharedPrefsDir] into [quarantineDir], timestamped as `<filename>-<nowMillis>.xml[.bak]`, then
 * prunes older quarantined copies of this filename down to [keepNewest]. Returns the quarantined
 * `.xml` destination, or the `.bak` destination when only the backup existed, or null when
 * neither file was present.
 */
internal fun quarantineEncryptedPreferencesFiles(
    sharedPrefsDir: File,
    quarantineDir: File,
    filename: String,
    nowMillis: Long = System.currentTimeMillis(),
    keepNewest: Int = QUARANTINE_KEEP_NEWEST,
): File? {
    val xmlFile = File(sharedPrefsDir, "$filename.xml")
    val bakFile = File(sharedPrefsDir, "$filename.xml.bak")

    if (!xmlFile.exists() && !bakFile.exists()) {
        return null
    }

    quarantineDir.mkdirs()

    val quarantinedXml = File(quarantineDir, "$filename-$nowMillis.xml")
    val quarantinedBak = File(quarantineDir, "$filename-$nowMillis.xml.bak")

    var result: File? = null
    if (xmlFile.exists()) {
        moveFile(xmlFile, quarantinedXml)
        result = quarantinedXml
    }
    if (bakFile.exists()) {
        moveFile(bakFile, quarantinedBak)
        result = result ?: quarantinedBak
    }

    pruneQuarantine(quarantineDir, filename, keepNewest)

    return result
}

private fun moveFile(
    source: File,
    destination: File
) {
    if (!source.renameTo(destination)) {
        source.copyTo(destination, overwrite = true)
        source.delete()
    }
}

/**
 * Keeps the [keepNewest] lexicographically-latest `<filename>-*.xml` quarantine entries (their
 * millisecond timestamps are fixed-width, so lexicographic order matches chronological order) and
 * deletes the rest, along with any `.bak` sibling. Other filenames' entries are left untouched.
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
 * Deletes every quarantined encrypted preferences file. Used by "Reset Zodl" so wiping the wallet
 * also removes any set-aside ciphertext left behind by an earlier recovery.
 */
fun purgeEncryptedPreferencesQuarantine(context: Context) {
    File(context.noBackupFilesDir, QUARANTINE_DIRECTORY).deleteRecursively()
}
