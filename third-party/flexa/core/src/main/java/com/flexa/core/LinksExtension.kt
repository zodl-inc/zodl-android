package com.flexa.core

import android.net.Uri

fun String.getPathSegments(): List<String> {
    val uri = Uri.parse(this)
    return uri.pathSegments?.toList() ?: emptyList()
}