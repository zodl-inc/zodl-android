package co.electriccoin.zcash.ui.screen.scankeystone.e2e

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.BuildConfig
import kotlinx.coroutines.delay
import java.io.File

/**
 * Test-only replacement for the camera in [co.electriccoin.zcash.ui.screen.scankeystone.view.ScanKeystoneView],
 * used by the Keystone simulator E2E harness (see KEYSTONE_E2E_CI_PLAN.md in the workspace root) to feed real
 * captured Keystone QR frames into the app without a physical/simulated camera.
 *
 * Disabled unless BOTH hold:
 * - this is a debug build ([BuildConfig.DEBUG]), and
 * - the inbox file returned by [inboxFile] exists in the app's private files dir at the moment the scan screen
 *   composes. A CI harness creates it via `adb exec-out run-as <package> sh -c 'cat > files/...'` before driving
 *   the app to this screen; a normal user's device will never have it.
 *
 * When enabled, this polls the inbox file and calls [onScan] once per new line, mirroring the shape of a real
 * camera delivering one frame per analysis cycle -- the receiving side (the same onScan lambda
 * [co.electriccoin.zcash.ui.screen.scankeystone.viewmodel.ScanKeystoneSignInRequestViewModel.onScanned] would
 * get from a camera) never knows the difference, so the real UR-decoding/navigation logic runs unmodified.
 */
object E2eKeystoneQrFrameSource {
    private const val INBOX_FILE_NAME = "e2e_keystone_scan_inbox.txt"
    private const val POLL_INTERVAL_MS = 250L

    fun inboxFile(context: Context): File = File(context.filesDir, INBOX_FILE_NAME)

    fun isEnabled(context: Context): Boolean = BuildConfig.DEBUG && inboxFile(context).exists()

    @Composable
    fun Compose(onScan: (String) -> Unit) {
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            Twig.debug { "E2E Keystone scan: fake camera active, polling ${inboxFile(context)}" }
            var linesDelivered = 0
            while (true) {
                val lines =
                    runCatching {
                        inboxFile(context).takeIf { it.exists() }?.readLines().orEmpty()
                    }.getOrDefault(emptyList())

                if (lines.size > linesDelivered) {
                    for (index in linesDelivered until lines.size) {
                        val frame = lines[index].trim()
                        if (frame.isNotEmpty()) {
                            Twig.debug { "E2E Keystone scan: delivering frame $index" }
                            onScan(frame)
                        }
                    }
                    linesDelivered = lines.size
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }
}
