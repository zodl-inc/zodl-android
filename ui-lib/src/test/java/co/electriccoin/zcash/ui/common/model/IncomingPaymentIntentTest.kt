package co.electriccoin.zcash.ui.common.model

import android.content.Intent
import android.net.Uri
import android.os.BadParcelableException
import android.text.SpannableString
import co.electriccoin.zcash.ui.common.usecase.HandleSharedPaymentUseCase
import io.mockk.every
import io.mockk.mockk
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class IncomingPaymentIntentTest {
    private val image = Uri.parse("content://photos.example/payment.png")

    @Test
    fun viewWithDataAlwaysUsesThirdPartyDetectionEvenWithShareExtras() {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("zcash:recipient"), "text/plain")
        intent.putExtra(Intent.EXTRA_TEXT, "different recipient")

        assertEquals(IncomingPaymentIntent.ThirdPartyView, intent.incomingPayment())
    }

    @Test
    fun viewWithoutDataDoesNotBecomeAShare() {
        val intent = textIntent("recipient").setAction(Intent.ACTION_VIEW)
        assertNull(intent.incomingPayment())
    }

    @Test
    fun sendTextIsPrefillInputAndNeverThirdPartyView() {
        val intent = textIntent("zcash:recipient?amount=1")
        intent.setDataAndType(Uri.parse("zcash:other"), "text/plain")
        assertEquals(IncomingPaymentIntent.Text("zcash:recipient?amount=1"), intent.incomingPayment())
    }

    @Test
    fun styledTextIsAcceptedWithoutAStringCast() {
        val intent = textIntent(SpannableString("shared recipient"))
        assertEquals(IncomingPaymentIntent.Text("shared recipient"), intent.incomingPayment())
    }

    @Test
    fun blankAndMissingTextAreIgnored() {
        assertNull(textIntent(" \n\t").incomingPayment())
        assertNull(Intent(Intent.ACTION_SEND).setType("text/plain").incomingPayment())
    }

    @Test
    fun unsupportedAndMissingMimeTypesCannotSmuggleText() {
        assertNull(textIntent("recipient").setType("application/pdf").incomingPayment())
        assertNull(textIntent("recipient").setType(null).incomingPayment())
    }

    @Test
    fun textLengthBoundaryIsAcceptedButLargerInputIsNot() {
        val limit = HandleSharedPaymentUseCase.MAX_SHARED_TEXT_LENGTH
        assertEquals(IncomingPaymentIntent.Text("a".repeat(limit)), textIntent("a".repeat(limit)).incomingPayment())
        assertNull(textIntent("a".repeat(limit + 1)).incomingPayment())
    }

    @Test
    fun imageStreamWinsOverACaption() {
        val intent = Intent(Intent.ACTION_SEND).setType("image/png")
        intent.putExtra(Intent.EXTRA_STREAM, image)
        intent.putExtra(Intent.EXTRA_TEXT, "caption")
        assertEquals(IncomingPaymentIntent.Image(image), intent.incomingPayment())
    }

    @Test
    fun multipleImagesUseOnlyTheFirstAsDocumented() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg")
        intent.putParcelableArrayListExtra(
            Intent.EXTRA_STREAM,
            arrayListOf(image, Uri.parse("content://photos.example/second"))
        )
        assertEquals(IncomingPaymentIntent.Image(image), intent.incomingPayment())
    }

    @Test
    fun anEmptyImageListOrMissingStreamDoesNotFallBackToACaption() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/png")
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf<Uri>())
        intent.putExtra(Intent.EXTRA_TEXT, "caption")
        assertNull(intent.incomingPayment())
        assertNull(intent.setAction(Intent.ACTION_SEND).incomingPayment())
    }

    @Test
    fun localFilesRemoteUrlsAndMissingProviderAuthoritiesAreNotOpened() {
        listOf(
            "file:///data/user/0/wallet/private.png",
            "https://example.com/payment.png",
            "content:/payment.png"
        ).forEach {
            val intent = Intent(Intent.ACTION_SEND).setType("image/png")
            intent.putExtra(Intent.EXTRA_STREAM, Uri.parse(it))
            intent.putExtra(Intent.EXTRA_TEXT, "caption")
            assertNull(intent.incomingPayment())
        }
    }

    @Test
    fun wronglyTypedStreamOrTextIsIgnored() {
        val intent = Intent(Intent.ACTION_SEND).setType("image/png")
        intent.putExtra(Intent.EXTRA_STREAM, Intent(Intent.ACTION_MAIN))
        assertNull(intent.incomingPayment())
        assertNull(textIntent("recipient").putExtra(Intent.EXTRA_TEXT, 42).incomingPayment())
    }

    @Test
    fun unparcelableExtrasDoNotCrashTheExportedEntryPoint() {
        val intent = mockk<Intent>()
        every { intent.flags } returns 0
        every { intent.action } returns Intent.ACTION_SEND
        every { intent.type } returns "text/plain"
        every { intent.getCharSequenceExtra(Intent.EXTRA_TEXT) } throws BadParcelableException("unreadable")
        assertNull(intent.incomingPayment())
    }

    @Test
    fun launcherAndUnknownActionsIgnoreSharedExtras() {
        assertNull(textIntent("recipient").setAction(Intent.ACTION_MAIN).incomingPayment())
        assertNull(textIntent("recipient").setAction("untrusted.action").incomingPayment())
    }

    @Test
    fun recentsNeverReplaysAnySupportedAction() {
        listOf(textIntent("recipient"), Intent(Intent.ACTION_VIEW, Uri.parse("zcash:recipient"))).forEach {
            it.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
            assertNull(it.incomingPayment())
        }
    }

    private fun textIntent(text: CharSequence): Intent =
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
}
