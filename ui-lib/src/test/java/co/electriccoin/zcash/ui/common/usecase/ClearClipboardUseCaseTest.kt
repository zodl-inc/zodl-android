package co.electriccoin.zcash.ui.common.usecase

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import co.electriccoin.zcash.spackle.AndroidApiVersion
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * [ClearClipboardUseCase] clears the primary clip where the platform can, never fails the paste that asked for it,
 * and overwrites it with an empty clip below Android 9, which has no API to clear it.
 */
class ClearClipboardUseCaseTest {
    private val clipboard = mockk<ClipboardManager>(relaxUnitFun = true)

    private val context =
        mockk<Context> {
            every { getSystemService(ClipboardManager::class.java) } returns clipboard
        }

    @BeforeTest
    fun setUp() {
        mockkObject(AndroidApiVersion)
        every { AndroidApiVersion.isAtLeastP } returns true
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun clearsThePrimaryClip() {
        ClearClipboardUseCase(context)()

        verify(exactly = 1) { clipboard.clearPrimaryClip() }
    }

    @Test
    fun aClipboardThatRefusesToBeClearedDoesNotFailTheCaller() {
        every { clipboard.clearPrimaryClip() } throws SecurityException("not the foreground app")

        ClearClipboardUseCase(context)()

        verify(exactly = 1) { clipboard.clearPrimaryClip() }
    }

    @Test
    fun belowAndroid9TheClipIsReplacedByAnEmptyOne() {
        every { AndroidApiVersion.isAtLeastP } returns false
        val empty = mockk<ClipData>()
        mockkStatic(ClipData::class)
        every { ClipData.newPlainText("", "") } returns empty

        ClearClipboardUseCase(context)()

        verify(exactly = 1) { clipboard.setPrimaryClip(empty) }
        verify(exactly = 0) { clipboard.clearPrimaryClip() }
    }
}
