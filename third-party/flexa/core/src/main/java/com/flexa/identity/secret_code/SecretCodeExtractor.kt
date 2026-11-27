package com.flexa.identity.secret_code

class SecretCodeExtractor(
    private val data: String,
    private val maxLength: Int
) {
    val code: String?
        get() {
            val digits = data.filter { it.isDigit() }
            return try {
                val res = digits.trim().substring(
                    IntRange(
                        0, (maxLength - 1)
                            .coerceAtLeast(1)
                    )
                )
                if (res.length == maxLength) res else null
            } catch (e: StringIndexOutOfBoundsException) {
                null
            }
        }
}
