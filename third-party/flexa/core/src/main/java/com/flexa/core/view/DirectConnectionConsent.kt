package com.flexa.core.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flexa.R
import com.flexa.core.Flexa

/**
 * Shows [content] (a web view that loads a Flexa page directly) only when direct connections to Flexa are
 * allowed ([directConnectionsAllowed]) or the user agreed on this screen. Otherwise it shows a
 * disclosure that Flexa will see the user's IP address, with a button to load the page anyway: the page is
 * never loaded silently while the app is on a private connection or Tor. If the privacy mode changes to
 * Tor or Private while the page is open, the page is removed and the consent screen shows again.
 */
@Composable
fun DirectConnectionConsent(
    directConnectionsAllowed: State<Boolean> = LocalDirectConnectionsAllowed.current,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var agreed by rememberSaveable { mutableStateOf(false) }
    // Reset agreed state when privacy mode changes to restrictive (the user must re-consent).
    if (!directConnectionsAllowed.value) {
        agreed = false
    }
    if (agreed || LocalInspectionMode.current || directConnectionsAllowed.value) {
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
