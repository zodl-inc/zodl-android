package co.electriccoin.zcash.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import co.electriccoin.zcash.ui.common.compose.LocalActivity
import co.electriccoin.zcash.ui.common.migration.MigrationAppHooks
import co.electriccoin.zcash.ui.common.provider.AppearanceModeStorageProvider
import co.electriccoin.zcash.ui.common.provider.ApplicationStateProvider
import co.electriccoin.zcash.ui.common.provider.IsOledEnabledStorageProvider
import co.electriccoin.zcash.ui.common.repository.GiftCardLinkStore
import co.electriccoin.zcash.ui.common.usecase.NavigateToRedeemGiftCardUseCase
import co.electriccoin.zcash.ui.common.viewmodel.SecretState
import co.electriccoin.zcash.ui.common.viewmodel.WalletViewModel
import co.electriccoin.zcash.ui.design.LocalKeyboardManager
import co.electriccoin.zcash.ui.design.animation.ScreenAnimation.enterTransition
import co.electriccoin.zcash.ui.design.animation.ScreenAnimation.exitTransition
import co.electriccoin.zcash.ui.design.animation.ScreenAnimation.popEnterTransition
import co.electriccoin.zcash.ui.design.animation.ScreenAnimation.popExitTransition
import co.electriccoin.zcash.ui.design.util.LocalNavController
import co.electriccoin.zcash.ui.screen.flexa.FlexaViewModel
import co.electriccoin.zcash.ui.screen.warning.viewmodel.StorageCheckViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun RootNavGraph(
    secretState: SecretState,
    walletViewModel: WalletViewModel,
    storageCheckViewModel: StorageCheckViewModel = koinViewModel(),
) {
    val keyboardManager = LocalKeyboardManager.current
    val flexaViewModel = koinViewModel<FlexaViewModel>()
    val navigationRouter = koinInject<NavigationRouter>()
    val applicationStateProvider = koinInject<ApplicationStateProvider>()
    val appearanceModeStorageProvider = koinInject<AppearanceModeStorageProvider>()
    val isOledEnabledStorageProvider = koinInject<IsOledEnabledStorageProvider>()
    val migrationAppHooks = koinInject<MigrationAppHooks>()
    val giftCardLinkStore = koinInject<GiftCardLinkStore>()
    val navigateToRedeemGiftCard = koinInject<NavigateToRedeemGiftCardUseCase>()
    val navController = LocalNavController.current
    val activity = LocalActivity.current
    val navigator: Navigator =
        remember(
            activity,
            navController,
            flexaViewModel,
            keyboardManager,
            applicationStateProvider,
            appearanceModeStorageProvider,
            isOledEnabledStorageProvider
        ) {
            NavigatorImpl(
                activity = activity,
                navController = navController,
                flexaViewModel = flexaViewModel,
                keyboardManager = keyboardManager,
                applicationStateProvider = applicationStateProvider,
                appearanceModeStorageProvider = appearanceModeStorageProvider,
                isOledEnabledStorageProvider = isOledEnabledStorageProvider
            )
        }

    LaunchedEffect(navigationRouter) {
        navigationRouter.observePipeline().collect {
            when (it) {
                is CustomNavigationCommand -> navigator.executeCommand(it)
                is NavigationCommand -> navigator.executeCommand(it)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = OnboardingGraph,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { enterTransition() },
        exitTransition = { exitTransition() },
        popEnterTransition = { popEnterTransition() },
        popExitTransition = { popExitTransition() }
    ) {
        this.onboardingNavGraph(
            activity = activity,
            navigationRouter = navigationRouter,
            walletViewModel = walletViewModel
        )

        this.walletNavGraph(
            storageCheckViewModel = storageCheckViewModel,
            walletViewModel = walletViewModel,
            navigationRouter = navigationRouter
        )
    }

    LaunchedEffect(secretState, navController) {
        if (secretState == SecretState.READY &&
            navController.currentDestination?.parent?.route != MainAppGraph::class.qualifiedName
        ) {
            keyboardManager.close()
            navController.navigate(MainAppGraph) {
                popUpTo(OnboardingGraph) {
                    inclusive = true
                }
            }
            // Same pattern as MainActivity.handleMigrationIntent — Home always lands on the
            // back stack first, then we redirect on top of it if a migration transfer needs
            // attention. isSyncBlocked() (fed into the synchronizer directly) already stopped
            // sync regardless of whether this redirect lands — this is routing only.
            migrationAppHooks.checkRecovery()
        } else if (
            secretState == SecretState.NONE &&
            navController.currentDestination?.parent?.route != OnboardingGraph::class.qualifiedName
        ) {
            keyboardManager.close()
            navController.navigate(OnboardingGraph) {
                popUpTo(MainAppGraph) {
                    inclusive = true
                }
            }
        }
    }

    // Gift card links from outside the app can arrive before there is a wallet, or before the wallet graph is shown
    // (cold start, onboarding). MainActivity only records that one arrived (the link itself is discarded); open the
    // gift card scanner once the wallet graph is up, so the user scans the card with the app.
    LaunchedEffect(secretState, navController) {
        if (secretState != SecretState.READY) return@LaunchedEffect
        navController.currentBackStackEntryFlow.first {
            it.destination.parent?.route == MainAppGraph::class.qualifiedName
        }
        giftCardLinkStore.isInAppScanRequested.filter { it }.collect {
            navigateToRedeemGiftCard.openRequestedInAppScan()
        }
    }
}

@Serializable
data object OnboardingGraph

@Serializable
data object MainAppGraph
