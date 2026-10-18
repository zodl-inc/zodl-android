package com.flexa.spend.main.keypad

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.flexa.core.theme.FlexaTheme

sealed class KeypadButton

class Symbol(val symbol: String) : KeypadButton()
class Point(val symbol: String = ".") : KeypadButton()
class Backspace(val image: ImageVector = Icons.AutoMirrored.Outlined.Backspace) : KeypadButton()

@Composable
fun Keypad(
    modifier: Modifier = Modifier,
    aspectRatio: Float? = null,
    onClick: (KeypadButton) -> Unit = {}
) {

    LazyVerticalGrid(
        modifier = modifier,
        columns = GridCells.Fixed(3),
        content = {
            for (i in 1..9) {
                item {
                    val b = Symbol(i.toString())
                    KeypadButton(button = b, aspectRatio = aspectRatio, onClick = { onClick.invoke(b) })
                }
            }
            item {
                val b = Point()
                KeypadButton(button = b, aspectRatio = aspectRatio, onClick = { onClick.invoke(b) })
            }
            item {
                val b = Symbol("0")
                KeypadButton(button = b, aspectRatio = aspectRatio, onClick = { onClick.invoke(b) })
            }
            item {
                val b = Backspace(Icons.AutoMirrored.Outlined.Backspace)
                KeypadButton(button = b, aspectRatio = aspectRatio, onClick = { onClick.invoke(b) })
            }
        })
}

@Composable
fun KeypadButton(
    button: KeypadButton,
    aspectRatio: Float? = null,
    onClick: (KeypadButton) -> Unit
) {
    val ratio by remember { mutableFloatStateOf(aspectRatio ?: 1.5f) }
    when (button) {
        is Symbol, is Point -> {
            val symbol = when (button) {
                is Symbol -> button.symbol
                is Point -> button.symbol
                else -> ""
            }
            TextButton(
                modifier = Modifier
                    .aspectRatio(ratio)
                    .clip(CircleShape),
                onClick = { onClick.invoke(button) }) {
                Text(
                    text = symbol, fontSize = 24.sp, fontWeight = FontWeight.W500,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        is Backspace ->
            TextButton(
                modifier = Modifier
                    .aspectRatio(ratio)
                    .clip(CircleShape),
                onClick = { onClick.invoke(button) }) {
                Icon(
                    imageVector = button.image, contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun KeypadPreview() {
    FlexaTheme {
        Keypad(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {}
    }
}