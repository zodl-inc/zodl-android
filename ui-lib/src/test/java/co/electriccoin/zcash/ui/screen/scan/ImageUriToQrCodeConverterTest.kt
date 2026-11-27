package co.electriccoin.zcash.ui.screen.scan

import co.electriccoin.zcash.ui.screen.scan.ImageUriToQrCodeConverter.Companion.MAX_DECODED_PIXELS
import co.electriccoin.zcash.ui.screen.scan.ImageUriToQrCodeConverter.Companion.sampleSizeFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageUriToQrCodeConverterTest {
    @Test
    fun phoneScreenshotsAndSmallImagesAreNotScaled() {
        assertEquals(1, sampleSizeFor(1080, 2400))
        assertEquals(1, sampleSizeFor(2048, 2048))
        assertEquals(1, sampleSizeFor(300, 300))
    }

    @Test
    fun largeImagesAreScaledWithinTheBudget() {
        // A 12 MP photo, a 100 MP photo, and an image far taller than it is wide.
        listOf(4000 to 3000, 12000 to 8000, 1000 to 200_000).forEach { (width, height) ->
            val sampleSize = sampleSizeFor(width, height)
            assertTrue(sampleSize > 1)
            assertTrue(width.toLong() / sampleSize * (height.toLong() / sampleSize) <= MAX_DECODED_PIXELS)
            // The next smaller power of two would still be over the budget.
            assertTrue(width.toLong() / (sampleSize / 2) * (height.toLong() / (sampleSize / 2)) > MAX_DECODED_PIXELS)
        }
    }

    @Test
    fun anImageWhoseSizeCannotBeReadIsNotScaled() {
        // BitmapFactory reports -1 when it cannot read the bounds; decoding then fails on its own.
        assertEquals(1, sampleSizeFor(-1, -1))
        assertEquals(1, sampleSizeFor(0, 0))
    }
}
