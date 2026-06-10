package co.electriccoin.zcash.ui.configuration

import co.electriccoin.zcash.configuration.model.entry.DefaultEntry
import org.junit.Test
import kotlin.reflect.full.memberProperties
import kotlin.test.assertFalse

class ConfigurationEntriesTest {
    // This test is primary to prevent copy-paste errors in configuration keys
    @Test
    fun keys_unique() {
        val fieldValueSet = mutableSetOf<String>()

        ConfigurationEntries::class
            .memberProperties
            .map { it.getter.call(ConfigurationEntries) }
            .map { it as DefaultEntry<*> }
            .map { it.key }
            .forEach {
                assertFalse(fieldValueSet.contains(it.key), "Duplicate key $it")

                fieldValueSet.add(it.key)
            }
    }
}
