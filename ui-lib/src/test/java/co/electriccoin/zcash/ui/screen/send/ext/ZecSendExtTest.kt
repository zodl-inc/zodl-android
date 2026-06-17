package co.electriccoin.zcash.ui.screen.send.ext

import androidx.compose.runtime.saveable.SaverScope
import cash.z.ecc.android.sdk.model.ZecSend
import cash.z.ecc.sdk.fixture.ZecSendFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ZecSendExtTest {
    @Test
    fun round_trip() =
        runTest {
            val original = ZecSendFixture.new()
            val saved =
                with(ZecSend.Saver) {
                    val allowingScope = SaverScope { true }

                    allowingScope.save(original)
                }

            val restored = ZecSend.Saver.restore(saved!!)

            assertEquals(original, restored)
        }

    @Test
    fun restore_empty() {
        val restored = ZecSend.Saver.restore(emptyList<Any?>())
        assertEquals(null, restored)
    }
}
