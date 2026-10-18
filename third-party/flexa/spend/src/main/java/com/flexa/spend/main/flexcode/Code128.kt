package com.flexa.spend.main.flexcode

import android.graphics.Bitmap
import android.graphics.Color.BLACK
import android.graphics.Color.TRANSPARENT
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.flexa.core.theme.FlexaTheme
import com.flexa.spend.main.main_screen.ZERO
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.oned.Code128Writer
import java.util.Collections


@Composable
fun Code128(
    modifier: Modifier = Modifier,
    code: String,
) {

    var barcode128 by remember {
        mutableStateOf(
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap()
        )
    }
    var code128Size by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(code, code128Size) {
        val _code = code.ifEmpty { ZERO }
        val matrix = Code128Writer().encode(
            _code, BarcodeFormat.CODE_128, code128Size.width, code128Size.height,
            Collections.singletonMap(EncodeHintType.CHARACTER_SET, "utf-8")
        )
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix[x, y]) BLACK else TRANSPARENT
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        barcode128 = bitmap.asImageBitmap()
    }

    Image(
        modifier = modifier
            .onGloballyPositioned {
                code128Size = IntSize(it.size.width, it.size.height)
            },
        bitmap = barcode128,
        contentDescription = "Code 128 barcode"
    )
}

@Preview
@Composable
private fun LocalFlexcodePreview() {
    FlexaTheme {
        Code128(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(androidx.compose.ui.graphics.Color.White),
            code = "134435"
        )
    }
}
