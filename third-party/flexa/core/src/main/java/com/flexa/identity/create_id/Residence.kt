package com.flexa.identity.create_id

import com.google.gson.annotations.SerializedName

data class Residence(

    @field:SerializedName("key")
    val key: String? = null,

    @field:SerializedName("is_abbreviation")
    val isAbbreviation: Boolean? = null,

    @field:SerializedName("suggestion")
    val suggestion: String? = null,

    @field:SerializedName("administrative_division")
    val administrativeDivision: String? = null,

    @field:SerializedName("administrative_division_code")
    val administrativeDivisionCode: String? = null,

    @field:SerializedName("region")
    val country: String? = null,

    @field:SerializedName("region_code")
    val countryCode: String? = null
) {

    val residenceName: String get() = "$administrativeDivision, $country"

}
