package com.flexa.spend.main.main_screen

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.flexa.spend.main.assets.AssetsViewModel

@Composable
fun SpendLifecycleRelatedMethods(
    viewModel: AssetsViewModel
) {

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.poll()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        viewModel.unsubscribePoll()
    }
}
