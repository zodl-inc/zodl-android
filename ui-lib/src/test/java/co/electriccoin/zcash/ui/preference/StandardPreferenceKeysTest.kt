package co.electriccoin.zcash.ui.preference

import co.electriccoin.zcash.preference.model.entry.PreferenceDefault
import org.junit.Test
import kotlin.reflect.full.memberProperties
import kotlin.test.assertFalse

class StandardPreferenceKeysTest {
    // This test is primary to prevent copy-paste errors in preference keys
    @Test
    fun unique_keys() {
        val fieldValueSet = mutableSetOf<String>()

        StandardPreferenceKeys::class
            .memberProperties
            .map { it.getter.call(StandardPreferenceKeys) }
            .map { it as PreferenceDefault<*> }
            .map { it.key }
            .forEach {
                assertFalse(fieldValueSet.contains(it.key), "Duplicate key $it")

                fieldValueSet.add(it.key)
            }
    }
}
