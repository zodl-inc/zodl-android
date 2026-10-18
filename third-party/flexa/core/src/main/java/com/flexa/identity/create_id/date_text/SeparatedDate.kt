package com.flexa.identity.create_id.date_text

class SeparatedDate(
    var day: Int? = null,
    var month: Int? = null,
    var year: Int? = null
) {

    fun isFilled(): Boolean =
        day != null && month != null && year != null
}
