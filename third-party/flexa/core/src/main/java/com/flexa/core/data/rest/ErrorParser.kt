package com.flexa.core.data.rest

import com.google.gson.annotations.SerializedName
import org.json.JSONException
import org.json.JSONObject

class ErrorParser {

    companion object {
        fun parseError(error: Throwable?): ErrorBundle {
            return try {
                val raw = error?.localizedMessage ?: error?.message ?: ""
                val mainObject = JSONObject(raw)
                val code = mainObject.getInt("code")
                val message = mainObject.getString("message")
                val status = mainObject.getString("status")
                ErrorBundle(
                    code = code, message = message, status = status
                )
            } catch (e: JSONException) {
                ErrorBundle( -1, null, null)
            }
        }
    }
}

class ErrorBundle(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String?,
    @SerializedName("status")
    val status: String?,
)
