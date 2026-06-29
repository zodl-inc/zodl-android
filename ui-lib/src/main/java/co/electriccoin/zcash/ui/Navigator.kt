package co.electriccoin.zcash.ui

import android.annotation.SuppressLint
import android.app.Activity
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.serialization.generateHashCode
import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
import co.electriccoin.zcash.ui.design.KeyboardManager
import co.electriccoin.zcash.ui.screen.ExternalUrl
import co.electriccoin.zcash.ui.screen.about.util.WebBrowserUtil
import co.electriccoin.zcash.ui.screen.flexa.FlexaViewModel
import com.flexa.core.Flexa
import com.flexa.spend.buildSpend
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer

interface Navigator {
    suspend fun executeCommand(command: NavigationCommand)

    suspend fun executeCommand(command: CustomNavigationCommand)
}

class NavigatorImpl(
    private val activity: Activity,
    private val navController: NavHostController,
    private val flexaViewModel: FlexaViewModel,
    private val keyboardManager: KeyboardManager,
    private val applicationStateProvider: ApplicationStateProvider,
) : Navigator {
    override suspend fun executeCommand(command: NavigationCommand) {
        keyboardManager.close()

        when (command) {
            is NavigationCommand.Forward -> forward(command)
            is NavigationCommand.Replace -> replace(command)
            is NavigationCommand.ReplaceAll -> replaceAll(command)
            NavigationCommand.Back -> navController.popBackStack()
            is NavigationCommand.BackTo -> backTo(command)
            NavigationCommand.BackToRoot -> backToRoot()
        }
    }

    override suspend fun executeCommand(command: CustomNavigationCommand) {
        val current = navController.currentBackStackEntry
        val new = command.block(current)
        if (new != null) {
            executeCommand(new)
        }
    }

    @SuppressLint("RestrictedApi")
    @OptIn(InternalSerializationApi::class)
    private fun backTo(command: NavigationCommand.BackTo) {
        navController.popBackStack(
            destinationId = command.route.serializer().generateHashCode(),
            inclusive = false
        )
    }

    private fun backToRoot() {
        navController.currentDestination?.parent?.startDestinationId?.let {
            navController.popBackStack(
                destinationId = it,
                inclusive = false
            )
        }
    }

    private fun replaceAll(command: NavigationCommand.ReplaceAll) {
        command.routes.forEachIndexed { index, route ->
            when (route) {
                co.electriccoin.zcash.ui.screen.flexa.Flexa -> {
                    if (index == 0) {
                        navController.currentDestination?.parent?.startDestinationId?.let {
                            navController.popBackStack(
                                route = it,
                                inclusive = false
                            )
                        }
                    }

                    if (index != command.routes.lastIndex) {
                        throw UnsupportedOperationException("Flexa can be opened as last screen only")
                    }

                    createFlexaFlow(flexaViewModel)
                }

                is ExternalUrl -> {
                    if (index == 0) {
                        navController.currentDestination?.parent?.startDestinationId?.let {
                            navController.popBackStack(
                                route = it,
                                inclusive = false
                            )
                        }
                    }

                    if (index != command.routes.lastIndex) {
                        throw UnsupportedOperationException("External url can be opened as last screen only")
                    }

                    applicationStateProvider.onThirdPartyUiShown()
                    WebBrowserUtil.startActivity(activity, route.url)
                }

                else -> {
                    navController.executeNavigation(route = route) {
                        if (index == 0) {
                            navController.currentDestination?.parent?.startDestinationId?.let {
                                popUpTo(it) {
                                    inclusive = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun replace(command: NavigationCommand.Replace) {
        command.routes.forEachIndexed { index, route ->
            when (route) {
                co.electriccoin.zcash.ui.screen.flexa.Flexa -> {
                    if (index == 0) {
                        navController.popBackStack()
                    }

                    if (index != command.routes.lastIndex) {
                        throw UnsupportedOperationException("Flexa can be opened as last screen only")
                    }

                    createFlexaFlow(flexaViewModel)
                }

                is ExternalUrl -> {
                    if (index == 0) {
                        navController.popBackStack()
                    }

                    if (index != command.routes.lastIndex) {
                        throw UnsupportedOperationException("External url can be opened as last screen only")
                    }

                    applicationStateProvider.onThirdPartyUiShown()
                    WebBrowserUtil.startActivity(activity, route.url)
                }

                else -> {
                    navController.executeNavigation(route = route) {
                        if (index == 0) {
                            popUpTo(navController.currentBackStackEntry?.destination?.id ?: 0) {
                                inclusive = true
                            }
                        }
                    }
                }
            }
        }
    }

    private fun forward(command: NavigationCommand.Forward) {
        command.routes.forEach { route ->
            when (route) {
                co.electriccoin.zcash.ui.screen.flexa.Flexa -> createFlexaFlow(flexaViewModel)
                is ExternalUrl -> WebBrowserUtil.startActivity(activity, route.url)
                else -> navController.executeNavigation(route = route)
            }
        }

        if (command.routes.lastOrNull() in listOf(ExternalUrl, co.electriccoin.zcash.ui.screen.flexa.Flexa)) {
            applicationStateProvider.onThirdPartyUiShown()
        }
    }

    private fun NavHostController.executeNavigation(
        route: Any,
        builder: (NavOptionsBuilder.() -> Unit)? = null
    ) {
        if (route is String) {
            if (builder == null) {
                navigate(route)
            } else {
                navigate(route, builder)
            }
        } else {
            if (builder == null) {
                navigate(route)
            } else {
                navigate(route, builder)
            }
        }
    }

    private fun createFlexaFlow(flexaViewModel: FlexaViewModel) {
        applicationStateProvider.onThirdPartyUiShown()
        Flexa
            .buildSpend()
            .onTransactionRequest { result -> flexaViewModel.createTransaction(result) }
            .build()
            .open(activity)
    }
}
