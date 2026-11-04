package com.flexa.core.view

import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

/**
 * Composition local for providing the direct connections allowed state to Flexa composables.
 * This allows DirectConnectionConsent to react to privacy mode changes.
 */
val LocalDirectConnectionsAllowed = compositionLocalOf<State<Boolean>> { mutableStateOf(false) }
