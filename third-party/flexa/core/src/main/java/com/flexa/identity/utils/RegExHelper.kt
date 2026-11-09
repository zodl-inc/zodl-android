package com.flexa.identity.utils

import android.util.Patterns

class RegExHelper {

    companion object {
        fun isEmailValid(value: String?): Boolean {
            return value?.isNotBlank() == true &&
                    Patterns.EMAIL_ADDRESS
                        .matcher(value?:"").matches()
        }
    }
}
