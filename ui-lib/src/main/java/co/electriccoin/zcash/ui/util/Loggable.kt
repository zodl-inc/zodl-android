package co.electriccoin.zcash.ui.util

import co.electriccoin.zcash.ui.BuildConfig

interface Loggable{
    operator fun invoke(message: String, excetion: Exception? = null)
}

fun loggable(tag: String, enabled: Boolean = !BuildConfig.DEBUG) = object : Loggable {

    override fun invoke(message: String, excetion: Exception?) {
        if (enabled) {
            if (excetion != null) {
                android.util.Log.e(tag, message, excetion)
            } else {
                android.util.Log.d(tag, message)
            }
        }
    }
}
