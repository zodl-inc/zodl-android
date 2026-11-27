package com.flexa.spend.main.places_to_pay

import android.os.Bundle
import androidx.compose.material3.ColorScheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flexa.spend.Spend
import com.flexa.spend.domain.ISpendInteractor
import com.flexa.spend.toCssRgba
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.net.URL

class PlacesToPayViewModel(
    address: String,
    private val interactor: ISpendInteractor = Spend.interactor
) : ViewModel() {

    internal val bundle = Bundle()
    private val useLocalhost = false
    private val host = when {
        useLocalhost -> "http://localhost:8000"
        else -> "https://flexa.network"
    }
    internal val url = URL("$host/$address")
    internal val urlsList = LinkedHashSet<String>()
    internal val themeData = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { initThemeData() }
    }

    internal fun getSDKThemeData(colorScheme: ColorScheme): String =
        """
{
    "android": {
        "light": {
            "backgroundColor": "${colorScheme.background.toCssRgba()}",
            "sortTextColor": "${colorScheme.secondary.toCssRgba()}",
            "titleColor": "${colorScheme.onBackground.toCssRgba()}",
            "cardColor": "${colorScheme.surface.toCssRgba()}",
            "textColor": "${colorScheme.onBackground.toCssRgba()}"
        },
        "dark": {
            "backgroundColor": "${colorScheme.background.toCssRgba()}",
            "sortTextColor": "${colorScheme.secondary.toCssRgba()}",
            "titleColor": "${colorScheme.onBackground.toCssRgba()}",
            "cardColor": "${colorScheme.surface.toCssRgba()}",
            "textColor": "${colorScheme.onBackground.toCssRgba()}"
        }
    }
}
""".trimIndent()

    private suspend fun initThemeData() {
        themeData.value = interactor.getPlacesToPayTheme()
    }
}
