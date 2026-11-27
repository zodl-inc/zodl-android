package com.flexa.identity.main

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Date

internal class UserViewModel: ViewModel() {

    val userData = MutableStateFlow(UserData())

}

internal data class UserData(
    var email: String? = null,
    var firstName: String? = null,
    var lastName: String? = null,
    var birthday: Date? = null
)
