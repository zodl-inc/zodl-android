package co.electriccoin.zcash.ui.design.newcomponent

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import kotlin.annotation.AnnotationRetention.SOURCE

@Preview(name = "1: Light preview", showBackground = true)
@Preview(name = "2: Dark preview", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "es")
@Retention(SOURCE)
annotation class PreviewScreens

@Preview(name = "1: Light preview", showBackground = true)
@Preview(
    name = "2: Light preview small",
    showBackground = true,
    device = Devices.NEXUS_5,
    fontScale = 1.5f
)
@Preview(name = "3: Dark preview", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(
    name = "4: Dark preview small",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    device = Devices.NEXUS_5,
    fontScale = 1.5f
)
@Retention(SOURCE)
annotation class PreviewScreenSizes
