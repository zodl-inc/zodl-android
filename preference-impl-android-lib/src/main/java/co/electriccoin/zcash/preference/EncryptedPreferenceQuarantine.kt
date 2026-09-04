@file:Suppress("TooManyFunctions")

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
 * Removes `<filename>.xml` and its `.xml.bak` sibling from [sharedPrefsDir]: the cleanup for the
 * empty file that clearing Android's in-memory `SharedPreferences` cache commits back to the
 * vacated path, once the real content is safely quarantined. A missing file is a no-op.
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

/**
 * Runs [quarantineEncryptedPreferencesFiles] and writes the [recreatedMarkerFile] guard only once
 * it has succeeded, so a store that was never set aside is still allowed to recover on the next
 * launch.
 *
 * A failing quarantine is rethrown rather than falling back to deleting the original files.
 * [moveFile] degrades to copy-then-delete when `renameTo` fails, and a copy that throws mid-write —
 * low disk being the obvious trigger, and also a plausible reason `renameTo` failed — would leave a
 * truncated copy in quarantine; deleting the original on top of that turns a recoverable failure
 * into unrecoverable loss of the only ciphertext holding the seed. Rethrowing propagates out of
 * `AndroidPreferenceProvider.createEncryptedPreferencesWithRecovery` instead, leaving the store
 * exactly where it is for a later attempt.
 */
internal fun quarantineEncryptedPreferencesFilesAndMarkRecreated(
    sharedPrefsDir: File,
    quarantineDir: File,
    filename: String,
    nowMillis: Long = System.currentTimeMillis(),
    keepNewest: Int = QUARANTINE_KEEP_NEWEST,
) {
    quarantineEncryptedPreferencesFiles(
        sharedPrefsDir = sharedPrefsDir,
        quarantineDir = quarantineDir,
        filename = filename,
        nowMillis = nowMillis,
        keepNewest = keepNewest,
    )

    runCatching {
        quarantineDir.mkdirs()
        val markerCreated = recreatedMarkerFile(quarantineDir, filename).createNewFile()
        if (!markerCreated) {
            Twig.error { "Creating the recreated-marker for $filename returned false" }
        }
    }
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
 * Prunes this filename's quarantine entries down to [keepNewest] of them: the oldest entry
 * unconditionally, plus the [keepNewest] - 1 latest ones. An entry is identified by its shared
 * `<filename>-<millis>` base name rather than by its `.xml` file alone, so an entry that only has
 * a `.xml.bak` — the `.xml` move having failed mid-commit — still counts and still gets pruned
 * like any other, instead of sitting there forever because the `.xml`-only listing never saw it.
 * Everything between the oldest and the kept newest is deleted, both the `.xml` and `.xml.bak` for
 * that base name if present. (The millisecond timestamps are fixed-width, so lexicographic order
 * matches chronological order.)
 *
 * Keeping the oldest is the whole point of the retention: only the first quarantine of a filename
 * can set aside a store that ever held a wallet, because the recovery that follows it recreates
 * that store empty, so every later quarantine of the same filename sets aside an empty or
 * near-empty file. Deleting oldest-first would therefore drop the only copy holding the seed after
 * a handful of launches on flaky Keystore hardware, while keeping it costs a few KB of XML.
 *
 * Other filenames' entries, including this filename's [recreatedMarkerFile], are left untouched:
 * the marker is named `<filename>.recreated`, which never matches the `<filename>-*` pattern.
 */
internal fun pruneQuarantine(
    quarantineDir: File,
    filename: String,
    keepNewest: Int
) {
    val prefix = "$filename-"
    val entries =
        quarantineDir
            .listFiles { file ->
                file.name.startsWith(prefix) && (file.name.endsWith(".xml") || file.name.endsWith(".xml.bak"))
            }?.mapTo(mutableSetOf()) { it.name.removeSuffix(".bak").removeSuffix(".xml") }
            ?: return

    val newestFirst = entries.sortedByDescending { it }
    val oldest = newestFirst.lastOrNull()

    newestFirst
        .drop((keepNewest - 1).coerceAtLeast(0))
        .filterNot { it == oldest }
        .forEach { baseName ->
            File(quarantineDir, "$baseName.xml").delete()
            File(quarantineDir, "$baseName.xml.bak").delete()
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
