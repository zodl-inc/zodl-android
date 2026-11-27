package com.flexa.identity

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.flexa.core.Flexa
import com.flexa.core.toNavArgument
import com.flexa.identity.coppa.CoppaScreen
import com.flexa.identity.create_id.CreateId
import com.flexa.identity.main.LoginScreen
import com.flexa.identity.main.WebView
import com.flexa.identity.restricted_region.RestrictedRegion
import com.flexa.identity.secret_code.SecretCodeScreen
import com.flexa.identity.secret_code.SecretCodeViewModel
import com.flexa.identity.terms_of_use.TermsOfUse
import com.flexa.identity.verify_email.VerifyEmail

const val AUTH_ROUTE = "com.flexa.identity"

sealed class Route(val name: String) {
    data object RestrictedRegion : Route("$AUTH_ROUTE.restricted_region")
    data object Entrance : Route("$AUTH_ROUTE.entrance")
    data object Main : Route("$AUTH_ROUTE.main")
    data object CreateId : Route("$AUTH_ROUTE.create_id")
    data object VerifyEmail : Route("$AUTH_ROUTE.verify_email")
    data object Coppa : Route("$AUTH_ROUTE.coppa")
    data object TermsOfUse : Route("$AUTH_ROUTE.terms_of_use")
    data object SecretCode :
        Route("$AUTH_ROUTE.secret_code?deepLink={deepLink}") {
        const val KEY_DEEP_LINK = "deepLink"
        val arguments = listOf(navArgument(KEY_DEEP_LINK) {
            type = NavType.StringType
            defaultValue = ""
        })

        fun createRoute(deepLink: String = ""): String {
            return "$AUTH_ROUTE.secret_code?$KEY_DEEP_LINK=${deepLink.toNavArgument()}"
        }
    }

    data object WebView : Route("$AUTH_ROUTE.web_view?url={url}") {
        const val KEY = "url"
        val arguments = listOf(navArgument(KEY) {
            type = NavType.StringType
            defaultValue = ""
        })

        fun createRoute(url: String = ""): String {
            return "$AUTH_ROUTE.web_view?$KEY=${url.toNavArgument()}"
        }
    }
}

val enterTransition = fadeIn() + scaleIn(initialScale = 1.1F)
val popEnterTransition = fadeIn() + scaleIn(initialScale = .9F)
val exitTransition = fadeOut() + scaleOut(targetScale = .9F)
val popExitTransition = fadeOut() + scaleOut(targetScale = 1.1F)

fun NavGraphBuilder.identityNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    deepLink: String? = null
) {
    composable(Route.RestrictedRegion.name) {
        val context = LocalContext.current
        RestrictedRegion(modifier = modifier) {
            close(context, navController)
        }
    }
    navigation(startDestination = Route.Entrance.name, route = AUTH_ROUTE) {
        composable(
            Route.Entrance.name,
        ) {
            LaunchedEffect(Unit) {
                if (deepLink != null) {
                    navController.navigate(Route.SecretCode.createRoute(deepLink)) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                } else
                    navController.navigate(Route.Main.name) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
            }
        }
        composable(
            Route.Main.name,
            enterTransition = { enterTransition },
            exitTransition = { exitTransition },
            popEnterTransition = { popEnterTransition },
            popExitTransition = { popExitTransition }
        ) {
            val context = LocalContext.current
            val canSpend by Flexa.canSpend.collectAsStateWithLifecycle()

            LaunchedEffect(canSpend) {
                if (!canSpend) {
                    navController.navigate(Route.RestrictedRegion.name) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            LoginScreen(
                modifier = modifier,
                viewModel = viewModel(),
                userVM = viewModel(context.getActivity() ?: it),
                toBack = { close(context, navController) },
                toContinue = { navController.navigate(Route.VerifyEmail.name) },
                toSignIn = { navController.navigate(Route.CreateId.name) },
                toUrl = { url -> navController.navigate(Route.WebView.createRoute(url)) }
            )
        }
        composable(
            Route.CreateId.name,
            enterTransition = { enterTransition },
            exitTransition = { exitTransition },
            popEnterTransition = { popEnterTransition },
            popExitTransition = { popExitTransition }
        ) { entry ->
            val context = LocalContext.current
            CreateId(
                modifier = modifier.background(MaterialTheme.colorScheme.background),
                viewModel = viewModel(),
                userVM = viewModel(context.getActivity() ?: entry),
                toBack = { navController.navigateUp() },
                toPrivacy = { navController.navigate(Route.WebView.createRoute("https://flexa.co/legal/privacy")) },
                toCoppa = { navController.navigate(Route.Coppa.name) },
                toTermsOfUse = { navController.navigate(Route.TermsOfUse.name) },
                toContinue = { navController.navigate(Route.VerifyEmail.name) },
            )
        }
        composable(
            route = Route.VerifyEmail.name,
            enterTransition = { enterTransition },
            exitTransition = { exitTransition },
            popEnterTransition = { popEnterTransition },
            popExitTransition = { popExitTransition }
        ) {
            val context = LocalContext.current
            VerifyEmail(
                modifier = modifier
                    .background(MaterialTheme.colorScheme.background)
                    .systemBarsPadding(),
                userViewModel = viewModel(context.getActivity() ?: it),
                toContinue = {
                    context.getActivity()?.let { activity ->
                        try {
                            val intent = Intent(Intent.ACTION_MAIN)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            intent.addCategory(Intent.CATEGORY_APP_EMAIL)
                            activity.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Log.e("TAG", "openEmailApplication: ", e)
                        }
                    }
                    navController.navigate(Route.SecretCode.createRoute())
                },
                toBack = { navController.navigateUp() }
            )
        }
        composable(
            route = Route.Coppa.name,
            enterTransition = { enterTransition },
            exitTransition = { exitTransition },
            popEnterTransition = { popEnterTransition },
            popExitTransition = { popExitTransition }
        ) {
            CoppaScreen(
                modifier = modifier.systemBarsPadding(),
                toBack = {
                    navController.popBackStack(route = AUTH_ROUTE, true)
                }
            )
        }
        composable(
            route = Route.TermsOfUse.name,
            enterTransition = { enterTransition },
            exitTransition = { exitTransition },
            popEnterTransition = { popEnterTransition },
            popExitTransition = { popExitTransition }
        ) {
            TermsOfUse(
                modifier = modifier,
                url = "https://flexa.network/legal/terms"
            ) {
                navController.navigateUp()
            }
        }
        composable(
            route = Route.SecretCode.name,
            arguments = Route.SecretCode.arguments,
            enterTransition = { enterTransition },
            exitTransition = { exitTransition },
            popEnterTransition = { popEnterTransition },
            popExitTransition = { popExitTransition }
        ) { entry ->
            val context = LocalContext.current
            val linkParam = entry.arguments?.getString(Route.SecretCode.KEY_DEEP_LINK)
            val link = if (linkParam?.isNotBlank() == true) linkParam else null
            SecretCodeScreen(
                modifier = modifier.systemBarsPadding(),
                viewModel = viewModel(initializer = { SecretCodeViewModel(deepLink = link) }),
                onBack = {
                    if (!navController.popBackStack()) {
                        close(context, navController)
                    }
                },
                onClose = { close(context, navController) }
            )
        }
    }
    composable(
        Route.WebView.name,
        arguments = Route.WebView.arguments,
        enterTransition = { enterTransition },
        exitTransition = { exitTransition },
        popEnterTransition = { popEnterTransition },
        popExitTransition = { popExitTransition }
    ) { entry ->
        val url = entry.arguments?.getString(Route.WebView.KEY) ?: ""
        WebView(
            modifier = modifier,
            url = url
        ) { navController.popBackStack() }
    }
}

private fun close(context: Context, navController: NavHostController) {
    if (context is IdentityActivity) context.finish()
    else
        navController.popBackStack(route = AUTH_ROUTE, true)
    Identity.sendResult(Identity.onResult)
}
