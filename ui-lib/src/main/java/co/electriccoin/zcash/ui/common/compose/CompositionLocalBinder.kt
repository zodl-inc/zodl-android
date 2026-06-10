package co.electriccoin.zcash.ui.common.compose

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import co.electriccoin.zcash.spackle.FirebaseTestLabUtil
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.design.component.LocalScreenBrightness
import co.electriccoin.zcash.ui.design.component.ScreenBrightnessState
import co.electriccoin.zcash.ui.design.util.LocalNavController
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun MainActivity.BindCompLocalProvider(content: @Composable () -> Unit) {
    val navController = rememberNavController().also { navControllerForTesting = it }
    CompositionLocalProvider(
        LocalNavController provides navController,
        LocalActivity provides this@BindCompLocalProvider,
    ) {
        ObserveScreenSecurityFlag()
        ObserveScreenBrightnessFlag()
        ObserveScreenTimeoutFlag()
        content()
    }
}

@Composable
private fun ObserveScreenSecurityFlag() {
    val screenSecurity = LocalScreenSecurity.current
    val context = LocalContext.current.applicationContext
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    DisposableEffect(screenSecurity) {
        val job =
            scope.launch {
                screenSecurity.referenceCount
                    .map { it > 0 }
                    .collect { isSecure ->
                        val isTest = FirebaseTestLabUtil.isFirebaseTestLab(context)

                        if (isSecure && !isTest) {
                            activity.window.setFlags(
                                WindowManager.LayoutParams.FLAG_SECURE,
                                WindowManager.LayoutParams.FLAG_SECURE
                            )
                        } else {
                            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
            }

        onDispose {
            job.cancel()
        }
    }
}

@Composable
private fun ObserveScreenBrightnessFlag() {
    val screenBrightness = LocalScreenBrightness.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    DisposableEffect(screenBrightness) {
        val job =
            scope.launch {
                screenBrightness.referenceSwitch
                    .map { it == ScreenBrightnessState.FULL }
                    .collect { maxBrightness ->
                        if (maxBrightness) {
                            activity.window.attributes =
                                activity.window.attributes.apply {
                                    this.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                                }
                        } else {
                            activity.window.attributes =
                                activity.window.attributes.apply {
                                    this.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                                }
                        }
                    }
            }

        onDispose {
            job.cancel()
            activity.window.attributes =
                activity.window.attributes.apply {
                    this.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
        }
    }
}

@Composable
private fun ObserveScreenTimeoutFlag() {
    val screenTimeout = LocalScreenTimeout.current
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    DisposableEffect(screenTimeout) {
        val job =
            scope.launch {
                screenTimeout.referenceCount.map { it > 0 }.collect { disableTimeout ->
                    if (disableTimeout) {
                        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }

        onDispose {
            job.cancel()
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
