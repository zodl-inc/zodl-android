package co.electriccoin.zcash.ui.screen.exchangerate.picker

import androidx.navigation.NavBackStackEntry
import co.electriccoin.zcash.ui.BaseNavigationCommand
import co.electriccoin.zcash.ui.NavigationCommand
import co.electriccoin.zcash.ui.NavigationRouter
import co.electriccoin.zcash.ui.common.model.supportedFiatCurrencies
import co.electriccoin.zcash.ui.common.usecase.NavigateToSelectFiatCurrencyUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The currency picker lists every supported currency in display order and highlights exactly the
 * caller's current selection (MOB-1124).
 */
class CurrencyConversionPickerVMTest {
    private fun pickerVM(selectedCode: String) =
        CurrencyConversionPickerVM(
            args = CurrencyConversionPickerArgs(selectedCode = selectedCode),
            navigateToSelectFiatCurrency = NavigateToSelectFiatCurrencyUseCase(NoopNavigationRouter())
        )

    @Test
    fun exposesAllSupportedCurrenciesInOrder() {
        val items = pickerVM(selectedCode = "USD").state.value.items
        assertEquals(supportedFiatCurrencies.map { it.code }, items.map { it.key })
    }

    @Test
    fun marksOnlyTheCurrentSelectionAsSelected() {
        val items = pickerVM(selectedCode = "EUR").state.value.items
        assertEquals(listOf("EUR"), items.filter { it.isSelected }.map { it.key })
    }
}

private class NoopNavigationRouter : NavigationRouter {
    override fun forward(vararg routes: Any) = Unit

    override fun replace(vararg routes: Any) = Unit

    override fun replaceAll(vararg routes: Any) = Unit

    override fun back() = Unit

    override fun backTo(route: KClass<*>) = Unit

    override fun custom(block: (NavBackStackEntry?) -> NavigationCommand?) = Unit

    override fun backToRoot() = Unit

    override fun observePipeline(): Flow<BaseNavigationCommand> = emptyFlow()
}
