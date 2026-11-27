package co.electriccoin.zcash.ui.util

import android.content.Intent
import android.net.Uri
import android.provider.Settings

object SettingsUtil {
    internal const val SETTINGS_URI_PREFIX = "package:"

    internal const val FLAGS =
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_NO_HISTORY or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS

    /**
     * Returns an intent to the system Settings page of the app given by packageName parameter.
     *
     * @param packageName of the app, which should be opened in the Settings
     *
     * @return Intent for launching the system Settings app
     */
    fun newSettingsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            data = Uri.parse("$SETTINGS_URI_PREFIX$packageName")
            flags = FLAGS
        }

    /**
     * Returns an intent to the system Storage Settings page.
     *
     * @return Intent for launching the system Settings app
     */
    internal fun newStorageSettingsIntent(): Intent =
        Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
            flags = FLAGS
        }
}
