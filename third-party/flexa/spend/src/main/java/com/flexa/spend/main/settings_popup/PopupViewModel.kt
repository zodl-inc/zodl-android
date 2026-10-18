package com.flexa.spend.main.settings_popup

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flexa.core.sendFlexaReport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PopupViewModel : ViewModel() {

    var opened by mutableStateOf(false)
    var blockGesture by mutableStateOf(false)

    fun switch() {
        viewModelScope.launch {
            blockGesture = true
            opened = !opened
            delay(100)
            blockGesture = false
        }
    }

    fun reportAnIssue(activity: Activity) = activity.sendFlexaReport()
}
