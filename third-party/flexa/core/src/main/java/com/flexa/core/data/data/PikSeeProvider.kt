package com.flexa.core.data.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

internal class PikSeeProvider {
    companion object {

        fun getCodeVerifier(): String {
            val secureRandom = SecureRandom()
            val bytes = ByteArray(32)
            secureRandom.nextBytes(bytes)
            val encoding = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            return Base64.encodeToString(bytes, encoding)
        }

        fun getCodeChallenge(codeVerifier: String): String {
            val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
            val messageDigest = MessageDigest.getInstance("SHA-256")
            messageDigest.update(bytes, 0, bytes.size)
            val digest = messageDigest.digest()
            val encoding = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            return Base64.encodeToString(digest, encoding)
        }

        fun getCodeChallenge(): String = getCodeChallenge(getCodeVerifier())


    }
}