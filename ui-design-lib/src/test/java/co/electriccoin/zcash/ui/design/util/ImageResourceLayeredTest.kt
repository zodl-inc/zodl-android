package co.electriccoin.zcash.ui.design.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import co.electriccoin.zcash.ui.design.R
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.design.theme.colors.Gray
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A [ImageResource.Layered] image draws its circle in the given colour, the theme's avatar circle
 * when the colour is unspecified, and tints its glyph white on a dark circle and black on a light
 * one. Probed on the pixels of the composed view drawn into a bitmap: compose-test's
 * `captureToImage` waits for a hardware frame that Robolectric's paused looper never draws.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageResourceLayeredTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun anUnspecifiedBackgroundDrawsTheThemesAvatarCircle() {
        val pixels = render(Color.Unspecified)

        assertEquals(Gray.`50`.toArgb(), pixels.circleEdge())
    }

    @Test
    fun aDarkCircleTintsTheGlyphWhiteAndALightCircleBlack() {
        val onBlue = render(Color(BLUE))
        assertEquals(BLUE.toInt(), onBlue.circleEdge())
        assertTrue(WHITE in onBlue.colors, "no white glyph pixel on #0088FF")
        assertFalse(BLACK in onBlue.colors, "a black glyph pixel on #0088FF")

        val onYellow = render(Color(YELLOW))
        assertEquals(YELLOW.toInt(), onYellow.circleEdge())
        assertTrue(BLACK in onYellow.colors, "no black glyph pixel on #FFCC00")
        assertFalse(WHITE in onYellow.colors, "a white glyph pixel on #FFCC00")
    }

    private var shownBackground by mutableStateOf(Color.Unspecified)

    private var view: View? = null

    /** Shows the image on [background], composing it on the first call only, and returns its pixels. */
    private fun render(background: Color): Pixels {
        shownBackground = background
        if (view == null) {
            composeTestRule.setContent {
                view = LocalView.current
                ZcashTheme {
                    ImageResource
                        .Layered(
                            background = shownBackground,
                            foreground = ImageResource.ByDrawable(R.drawable.ic_item_zodl_glyph)
                        ).Compose(modifier = Modifier.size(SIZE.dp).testTag(TAG))
                }
            }
        }
        composeTestRule.waitForIdle()
        val view = checkNotNull(view)
        val bounds = composeTestRule.onNodeWithTag(TAG).getUnclippedBoundsInRoot()
        val density = composeTestRule.density.density
        val side = (SIZE * density).toInt()
        val left = (bounds.left.value * density).toInt()
        val top = (bounds.top.value * density).toInt()
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        composeTestRule.runOnUiThread { view.draw(Canvas(bitmap)) }
        return Pixels(
            width = side,
            height = side,
            argb = List(side * side) { bitmap.getPixel(left + it % side, top + it / side) },
        )
    }

    /** The rendered pixels as ARGB, row by row. */
    private class Pixels(
        val width: Int,
        val height: Int,
        val argb: List<Int>,
    ) {
        val colors: Set<Int> = argb.toSet()

        /** A pixel just inside the circle's top edge, where the glyph's built-in padding leaves only the circle. */
        fun circleEdge(): Int = argb[(height / EDGE_DIVISOR) * width + width / 2]
    }

    private companion object {
        const val SIZE = 48
        const val TAG = "layered"
        const val EDGE_DIVISOR = 12
        const val BLUE = 0xFF0088FF
        const val YELLOW = 0xFFFFCC00
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
    }
}
