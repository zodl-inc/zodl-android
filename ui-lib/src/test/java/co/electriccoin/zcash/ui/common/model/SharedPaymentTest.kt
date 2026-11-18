package co.electriccoin.zcash.ui.common.model

import android.content.Intent
import android.net.Uri
import android.os.BadParcelableException
import android.text.SpannableString
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
class SharedPaymentTest {
    private val image = Uri.parse("content://photos.example/payment.png")

    @Test
    fun sharedTextIsRead() {
        assertEquals(SharedPayment.Text("zcash:recipient"), textIntent("zcash:recipient").sharedPayment())
        assertEquals(SharedPayment.Text("recipient"), textIntent(SpannableString("recipient")).sharedPayment())
    }

    @Test
    fun blankMissingOrWronglyTypedTextIsIgnored() {
        assertNull(textIntent(" \n\t").sharedPayment())
        assertNull(Intent(Intent.ACTION_SEND).setType("text/plain").sharedPayment())
        assertNull(textIntent("recipient").putExtra(Intent.EXTRA_TEXT, 42).sharedPayment())
    }

    @Test
    fun textOfAnotherOrNoMimeTypeIsIgnored() {
        assertNull(textIntent("recipient").setType("application/pdf").sharedPayment())
        assertNull(textIntent("recipient").setType(null).sharedPayment())
    }

    @Test
    fun sharedImageIsReadAndItsCaptionIgnored() {
        val intent = imageIntent(image).putExtra(Intent.EXTRA_TEXT, "caption")

        assertEquals(SharedPayment.Image(image), intent.sharedPayment())
    }

    @Test
    fun firstOfSeveralImagesIsRead() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg")
        intent.putParcelableArrayListExtra(
            Intent.EXTRA_STREAM,
            arrayListOf(image, Uri.parse("content://photos.example/second.jpg"))
        )

        assertEquals(SharedPayment.Image(image), intent.sharedPayment())
    }

    @Test
    fun missingImageDoesNotFallBackToItsCaption() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/png").putExtra(Intent.EXTRA_TEXT, "caption")
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf<Uri>())

        assertNull(intent.sharedPayment())
        assertNull(intent.setAction(Intent.ACTION_SEND).sharedPayment())
    }

    @Test
    fun onlyAContentUriIsAccepted() {
        assertNull(imageIntent(Uri.parse("file:///data/user/0/wallet/private.png")).sharedPayment())
        assertNull(imageIntent(Uri.parse("https://example.com/payment.png")).sharedPayment())
    }

    @Test
    fun wronglyTypedStreamIsIgnored() {
        val intent = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, Intent())

        assertNull(intent.sharedPayment())
    }

    @Test
    fun unreadableExtrasDoNotCrashTheExportedEntryPoint() {
        val intent = mockk<Intent>()
        every { intent.flags } returns 0
        every { intent.action } returns Intent.ACTION_SEND
        every { intent.type } returns "text/plain"
        every { intent.getCharSequenceExtra(Intent.EXTRA_TEXT) } throws BadParcelableException("unreadable")

        assertNull(intent.sharedPayment())
    }

    @Test
    fun otherActionsAreNotShares() {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("zcash:recipient"), "text/plain")

        assertNull(view.putExtra(Intent.EXTRA_TEXT, "recipient").sharedPayment())
        assertNull(textIntent("recipient").setAction(Intent.ACTION_MAIN).sharedPayment())
    }

    @Test
    fun shareReopenedFromRecentsIsIgnored() {
        assertNull(textIntent("recipient").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY).sharedPayment())
    }

    private fun textIntent(text: CharSequence): Intent =
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)

    private fun imageIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
}
