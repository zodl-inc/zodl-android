package co.electriccoin.zcash.preference

import co.electriccoin.zcash.preference.api.PreferenceProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PreferenceProviderCacheTest {
    @Test
    fun `same filename twice returns same instance and invokes creator once`() =
        runTest {
            val cache = PreferenceProviderCache()
            var creationCount = 0

            val first =
                cache.getOrCreate("filename") {
                    creationCount++
                    FakePreferenceProvider()
                }
            val second =
                cache.getOrCreate("filename") {
                    creationCount++
                    FakePreferenceProvider()
                }

            assertSame(first, second)
            assertEquals(1, creationCount)
        }

    @Test
    fun `concurrent callers for the same filename share a single creation`() =
        runTest {
            val cache = PreferenceProviderCache()
            var creationCount = 0
            val creationStarted = CompletableDeferred<Unit>()
            val releaseCreation = CompletableDeferred<Unit>()

            val firstResult = CompletableDeferred<PreferenceProvider>()
            val secondResult = CompletableDeferred<PreferenceProvider>()

            launch {
                firstResult.complete(
                    cache.getOrCreate("filename") {
                        creationCount++
                        creationStarted.complete(Unit)
                        releaseCreation.await()
                        FakePreferenceProvider()
                    }
                )
            }
            launch {
                creationStarted.await()
                secondResult.complete(cache.getOrCreate("filename") { error("must not be invoked") })
            }

            creationStarted.await()
            releaseCreation.complete(Unit)

            assertSame(firstResult.await(), secondResult.await())
            assertEquals(1, creationCount)
        }

    @Test
    fun `one filename's stalled creation does not block another filename`() =
        runTest {
            val cache = PreferenceProviderCache()
            val creationStartedA = CompletableDeferred<Unit>()
            val releaseA = CompletableDeferred<Unit>()

            val resultA = CompletableDeferred<PreferenceProvider>()
            launch {
                resultA.complete(
                    cache.getOrCreate("A") {
                        creationStartedA.complete(Unit)
                        releaseA.await()
                        FakePreferenceProvider()
                    }
                )
            }

            creationStartedA.await()

            val resultB = cache.getOrCreate("B") { FakePreferenceProvider() }

            assertSame(resultB, cache.getOrCreate("B") { error("must not be invoked") })

            releaseA.complete(Unit)
            resultA.await()
        }

    @Test
    fun `a creator that throws caches nothing and a later call retries successfully`() =
        runTest {
            val cache = PreferenceProviderCache()
            var attempts = 0

            assertFailsWith<IllegalStateException> {
                cache.getOrCreate("filename") {
                    attempts++
                    error("boom")
                }
            }

            val provider =
                cache.getOrCreate("filename") {
                    attempts++
                    FakePreferenceProvider()
                }

            assertEquals(2, attempts)
            assertSame(provider, cache.getOrCreate("filename") { error("must not be invoked") })
        }
}
