package co.electriccoin.zcash.ui.screen.redeemgift

import androidx.navigation.NavBackStackEntry
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.reflect.KClass

internal class RecordingNavigationRouter : NavigationRouter {
    var backCount = 0
        private set
    var backToRootCount = 0
        private set
    val forwardedRoutes = mutableListOf<Any>()
    val replacedRoutes = mutableListOf<Any>()

    override fun forward(vararg routes: Any) {
        forwardedRoutes.addAll(routes)
    }

    override fun replace(vararg routes: Any) {
        replacedRoutes.addAll(routes)
    }

    override fun replaceAll(vararg routes: Any) = Unit

    override fun replaceFrom(route: KClass<*>, vararg routes: Any) = Unit

    override fun back() {
        backCount++
    }

    override fun backTo(route: KClass<*>) = Unit

    override fun custom(block: (NavBackStackEntry?) -> NavigationCommand?) = Unit

    override fun backToRoot() {
        backToRootCount++
    }

    override fun observePipeline(): Flow<BaseNavigationCommand> = emptyFlow()
}
