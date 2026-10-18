package com.flexa.identity.create_id.date_text

enum class Mask(val mask: String, val hint: String) {
    US("##/##/####", "MMDDYYYY"),
    CAen_GB_MX(US.mask, "DDMMYYYY"),
    CAfr("####-##-##", "YYYYMMDD")
}
