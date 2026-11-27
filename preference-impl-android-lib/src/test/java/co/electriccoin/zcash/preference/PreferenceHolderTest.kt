package co.electriccoin.zcash.preference

import co.electriccoin.zcash.preference.api.PreferenceProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PreferenceHolderTest {
    @Test
    fun `second invoke returns the cached instance without calling create again`() =
        runTest {
            var creationCount = 0
            val holder =
                FakePreferenceHolder {
                    creationCount++
                    FakePreferenceProvider()
                }

            val first = holder()
            val second = holder()

            assertSame(first, second)
            assertEquals(1, creationCount)
        }

    @Test
    fun `concurrent first invokes call create exactly once`() =
        runTest {
            var creationCount = 0
            val creationStarted = CompletableDeferred<Unit>()
            val releaseCreation = CompletableDeferred<Unit>()
            val holder =
                FakePreferenceHolder {
                    creationCount++
                    creationStarted.complete(Unit)
                    releaseCreation.await()
                    FakePreferenceProvider()
                }

            val firstResult = CompletableDeferred<PreferenceProvider>()
            val secondResult = CompletableDeferred<PreferenceProvider>()

            launch { firstResult.complete(holder()) }
            launch { secondResult.complete(holder()) }

            creationStarted.await()
            releaseCreation.complete(Unit)

            assertSame(firstResult.await(), secondResult.await())
            assertEquals(1, creationCount)
        }

    @Test
    fun `create throwing propagates and a later invoke retries successfully`() =
        runTest {
            var attempts = 0
            val holder =
                FakePreferenceHolder {
                    attempts++
                    if (attempts == 1) {
                        error("boom")
                    }
                    FakePreferenceProvider()
                }

            assertFailsWith<IllegalStateException> { holder() }

            val provider = holder()

            assertEquals(2, attempts)
            assertSame(provider, holder())
        }
}

private class FakePreferenceHolder(
    private val creator: suspend () -> PreferenceProvider
) : PreferenceHolder() {
    override suspend fun create(): PreferenceProvider = creator()
}
