package com.flexa.spend.main.flexcode

import android.graphics.Bitmap
import android.graphics.Color.BLACK
import android.graphics.Color.TRANSPARENT
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.flexa.spend.containsLetters
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.pdf417.PDF417Writer
import com.google.zxing.pdf417.encoder.Compaction
import com.google.zxing.pdf417.encoder.Dimensions


@Composable
fun PDF417(
    modifier: Modifier = Modifier,
    columns: Int = 4,
    rows: Int = 20,
    code: String,
) {
    val complexCode by remember { mutableStateOf(code.containsLetters()) }
    var barcode417 by remember {
        mutableStateOf(
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap()
        )
    }
    var code417Size by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(code417Size, code) {
        val writer = if (complexCode) PDF417Writer() else FlexaPDF417Writer(3F)
        runCatching {
            val matrix = writer.encode(
                    code, BarcodeFormat.PDF_417,
                    code417Size.width, code417Size.height,
                    mapOf(
                        EncodeHintType.PDF417_COMPACT to true,
                        EncodeHintType.PDF417_COMPACTION to Compaction.AUTO,
                        EncodeHintType.PDF417_DIMENSIONS to Dimensions(
                            columns,
                            columns,
                            rows,
                            rows
                        ),
                    )
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
            barcode417 = bitmap.trimBorders(TRANSPARENT).asImageBitmap()
        }.onFailure {
            Log.e(null, "PDF417: code[$code]", it)
        }
    }
    Image(
        modifier = modifier
            .onGloballyPositioned {
                code417Size = IntSize(it.size.width, it.size.height)
            },
        contentScale = ContentScale.Fit,
        bitmap = barcode417,
        alignment = Alignment.Center,
        contentDescription = "PDF 417 barcode"
    )
}

fun Bitmap.trimBorders(color: Int): Bitmap {
    var startX = 0
    loop@ for (x in 0 until width) {
        for (y in 0 until height) {
            if (getPixel(x, y) != color) {
                startX = x
                break@loop
            }
        }
    }
    var startY = 0
    loop@ for (y in 0 until height) {
        for (x in 0 until width) {
            if (getPixel(x, y) != color) {
                startY = y
                break@loop
            }
        }
    }
    var endX = width - 1
    loop@ for (x in endX downTo 0) {
        for (y in 0 until height) {
            if (getPixel(x, y) != color) {
                endX = x
                break@loop
            }
        }
    }
    var endY = height - 1
    loop@ for (y in endY downTo 0) {
        for (x in 0 until width) {
            if (getPixel(x, y) != color) {
                endY = y
                break@loop
            }
        }
    }

    val newWidth = endX - startX + 1
    val newHeight = endY - startY + 1

    return Bitmap.createBitmap(this, startX, startY, newWidth, newHeight)
}

@Preview
@Composable
private fun LocalFlexcodePreview() {
    val code by remember { mutableStateOf("123456789012") }
    Box(
        modifier = Modifier
            .size(300.dp)
    ) {
        PDF417(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .rotate(180F),
            rows = 17,
            columns = 1,
            code = code,
        )
    }
}
