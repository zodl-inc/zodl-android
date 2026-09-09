package co.electriccoin.zcash.ui.screen.support.model

import co.electriccoin.zcash.configuration.AndroidConfigurationFactory
import co.electriccoin.zcash.ui.common.provider.IsTorEnabledStorageProvider
import co.electriccoin.zcash.ui.test.getAppContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupportFinancialInfoStateTest {
    @Test
    fun filter_time() =
        runTest {
            val supportInfo = newSupportInfo()

            val individualExpected = supportInfo.timeInfo.toSupportString()

            val actualIncluded = supportInfo.toSupportString(setOf(SupportInfoType.Time))
            assertTrue(actualIncluded.contains(individualExpected))

            val actualExcluded = supportInfo.toSupportString(emptySet())
            assertFalse(actualExcluded.contains(individualExpected))
        }

    @Test
    fun filter_app() =
        runTest {
            val supportInfo = newSupportInfo()

            val individualExpected = supportInfo.appInfo.toSupportString()

            val actualIncluded = supportInfo.toSupportString(setOf(SupportInfoType.App))
            assertTrue(actualIncluded.contains(individualExpected))

            val actualExcluded = supportInfo.toSupportString(emptySet())
            assertFalse(actualExcluded.contains(individualExpected))
        }

    @Test
    fun filter_os() =
        runTest {
            val supportInfo = newSupportInfo()

            val individualExpected = supportInfo.operatingSystemInfo.toSupportString()

            val actualIncluded = supportInfo.toSupportString(setOf(SupportInfoType.Os))
            assertTrue(actualIncluded.contains(individualExpected))

            val actualExcluded = supportInfo.toSupportString(emptySet())
            assertFalse(actualExcluded.contains(individualExpected))
        }

    @Test
    fun filter_device() =
        runTest {
            val supportInfo = newSupportInfo()

            val individualExpected = supportInfo.deviceInfo.toSupportString()

            val actualIncluded = supportInfo.toSupportString(setOf(SupportInfoType.Device))
            assertTrue(actualIncluded.contains(individualExpected))

            val actualExcluded = supportInfo.toSupportString(emptySet())
            assertFalse(actualExcluded.contains(individualExpected))
        }

    @Test
    fun filter_crash() =
        runTest {
            val supportInfo = newSupportInfo()

            val individualExpected = supportInfo.crashInfo.toCrashSupportString()

            val actualIncluded = supportInfo.toSupportString(setOf(SupportInfoType.Crash))
            assertTrue(actualIncluded.contains(individualExpected))
        }

    @Test
    fun filter_environment() =
        runTest {
            val supportInfo = newSupportInfo()

            val individualExpected = supportInfo.environmentInfo.toSupportString()

            val actualIncluded = supportInfo.toSupportString(setOf(SupportInfoType.Environment))
            assertTrue(actualIncluded.contains(individualExpected))

            val actualExcluded = supportInfo.toSupportString(emptySet())
            assertFalse(actualExcluded.contains(individualExpected))
        }

    @Test
    fun filter_tor() =
        runTest {
            val supportInfo = newSupportInfo()

            val individualExpected = supportInfo.torInfo.toSupportString()

            val actualIncluded = supportInfo.toSupportString(setOf(SupportInfoType.Tor))
            assertTrue(actualIncluded.contains(individualExpected))

            val actualExcluded = supportInfo.toSupportString(emptySet())
            assertFalse(actualExcluded.contains(individualExpected))
        }

    @Test
    fun tor_enabled() =
        runTest {
            val supportInfo = newSupportInfo(isTorEnabled = true)

            val actual = supportInfo.toSupportString(setOf(SupportInfoType.Tor))
            assertTrue(actual.contains("Tor enabled: Yes"))
        }

    @Test
    fun tor_disabled() =
        runTest {
            val supportInfo = newSupportInfo(isTorEnabled = false)

            val actual = supportInfo.toSupportString(setOf(SupportInfoType.Tor))
            assertTrue(actual.contains("Tor enabled: No"))
        }

    @Test
    fun tor_not_set() =
        runTest {
            val supportInfo = newSupportInfo(isTorEnabled = null)

            val actual = supportInfo.toSupportString(setOf(SupportInfoType.Tor))
            assertTrue(actual.contains("Tor enabled: Not set"))
        }

    @Test
    fun filter_permission() =
        runTest {
            val supportInfo = newSupportInfo()

            val individualExpected = supportInfo.permissionInfo.toPermissionSupportString()

            val actualIncluded = supportInfo.toSupportString(setOf(SupportInfoType.Permission))
            assertTrue(actualIncluded.contains(individualExpected))

            val actualExcluded = supportInfo.toSupportString(emptySet())
            assertFalse(actualExcluded.contains(individualExpected))
        }
}

private suspend fun newSupportInfo(isTorEnabled: Boolean? = true) =
    SupportInfo.new(
        getAppContext(),
        AndroidConfigurationFactory.new(),
        FakeIsTorEnabledStorageProvider(isTorEnabled)
    )

private class FakeIsTorEnabledStorageProvider(
    private val value: Boolean?
) : IsTorEnabledStorageProvider {
    override suspend fun get(): Boolean? = value

    override suspend fun store(amount: Boolean) = Unit

    override fun observe(): Flow<Boolean?> = flowOf(value)

    override suspend fun clear() = Unit
}
