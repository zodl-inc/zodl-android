package co.electriccoin.zcash.ui.screen.redeemgift

import androidx.navigation.NavBackStackEntry
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class RecordingNavigationRouter : NavigationRouter {
    var backCount = 0
        private set
    var backToRootCount = 0
        private set
    val forwardedRoutes = mutableListOf<Any>()
    val replacedRoutes = mutableListOf<Any>()

    /** Every [replaceFrom] call: the route popped back to, inclusive, and the routes added in its place. */
    val replacedFrom = mutableListOf<Pair<KClass<*>, List<Any>>>()

    /**
     * The redeem screens that replaced the gift card flow's screens from the gift card scanner on, one per
     * [replaceFrom] call; fails on any other [replaceFrom] call.
     */
    fun redeemReplacingGiftCardScan(): List<RedeemGiftArgs> =
        replacedFrom.map { (route, routes) ->
            assertEquals(ScanGiftCardArgs::class, route)
            assertIs<RedeemGiftArgs>(routes.single())
        }

    override fun forward(vararg routes: Any) {
        forwardedRoutes.addAll(routes)
    }

    override fun replace(vararg routes: Any) {
        replacedRoutes.addAll(routes)
    }

    override fun replaceAll(vararg routes: Any) = Unit

    override fun replaceFrom(route: KClass<*>, vararg routes: Any) {
        replacedFrom += route to routes.toList()
    }

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
