package com.flexa.identity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.flexa.core.Flexa
import com.flexa.core.theme.FlexaTheme


class IdentityActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreSDKContext()
        setContent {
            FlexaTheme {
                IdentityNavHost(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = MaterialTheme.colorScheme.background),
                    deepLink = intent?.getStringExtra(KEY_DEEP_LINK)
                )
            }
        }
    }

    private fun restoreSDKContext() {
        if (Flexa.context == null) {
            Flexa.context = applicationContext
        }
    }

    companion object {
        internal const val KEY_DEEP_LINK = "deep_link"
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun IdentityNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    deepLink: String? = null
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = AUTH_ROUTE,
    ) {
        identityNavGraph(
            modifier = modifier,
            navController = navController,
            deepLink = deepLink
        )
    }
}
