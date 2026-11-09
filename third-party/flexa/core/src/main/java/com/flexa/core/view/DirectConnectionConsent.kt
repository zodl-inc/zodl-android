package com.flexa.core.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flexa.R
import com.flexa.core.Flexa
import kotlinx.coroutines.delay

/**
 * Shows [content] (a web view that loads a Flexa page directly) only when direct connections to Flexa are
 * allowed ([Flexa.allowsDirectConnections]) or the user agreed on this screen. Otherwise it shows a
 * disclosure that Flexa will see the user's IP address, with a button to load the page anyway: the page is
 * never loaded silently while the app is on a private connection or Tor. The agreement is not saved: if the
 * privacy mode changes to Tor or Private while the page is open, or the screen is left, the page is removed and the
 * consent screen shows again.
 */
@Composable
fun DirectConnectionConsent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Not saved: an agreement belongs to this screen as it is now. Leaving it (or the process dying) ends it, so
    // coming back after the app went to Tor or Private asks again.
    var agreed by remember { mutableStateOf(false) }
    val allowed by produceState(initialValue = Flexa.allowsDirectConnections()) {
        while (true) {
            value = Flexa.allowsDirectConnections()
            delay(ALLOWED_POLL_MS)
        }
    }
    LaunchedEffect(allowed) { agreed = agreementAfterModeChange(agreed, allowed) }
    if (agreed || LocalInspectionMode.current || allowed) {
        content()
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.direct_connection_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.direct_connection_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = { agreed = true }) {
            Text(text = stringResource(R.string.direct_connection_load))
        }
    }
}

private const val ALLOWED_POLL_MS = 500L

/**
 * The agreement after [allowed] changed: it ends when direct connections are off, so the page that was loaded
 * on it goes away. It runs when [allowed] changes, not while the user agrees to a page that is off.
 */
internal fun agreementAfterModeChange(agreed: Boolean, allowed: Boolean): Boolean = agreed && allowed
