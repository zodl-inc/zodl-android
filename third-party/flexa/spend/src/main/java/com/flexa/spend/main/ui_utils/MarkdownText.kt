package com.flexa.spend.main.ui_utils

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


private fun findMarkdownIndexes(text: String): List<Triple<Int, Int, String>> {
    val regex = "(\\*\\*\\*.*?\\*\\*\\*)|(\\*\\*.*?\\*\\*)|(\\*.*?\\*)".toRegex()
    val matches = regex.findAll(text)
    return matches.map { matchResult ->
        val value = matchResult.value
        val marker = when {
            value.startsWith("***") -> "***"
            value.startsWith("**") -> "**"
            value.startsWith("*") -> "*"
            else -> ""
        }
        val startIndex = matchResult.range.first + marker.length
        val endIndex = matchResult.range.last - marker.length + 1
        Triple(startIndex, endIndex, marker)
    }.toList()
}

private fun createAnnotatedString(text: String): AnnotatedString {
    val markdownIndexes = findMarkdownIndexes(text)
    return buildAnnotatedString {
        var lastIndex = 0
        for ((start, end, marker) in markdownIndexes) {
            append(text.substring(lastIndex, (start - marker.length).coerceAtLeast(lastIndex)))
            withStyle(
                style = when (marker) {
                    "***" -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                    "**" -> SpanStyle(fontWeight = FontWeight.Bold)
                    "*" -> SpanStyle(fontStyle = FontStyle.Italic)
                    else -> SpanStyle()
                }
            ) {
                append(text.substring(start, end.coerceAtLeast(start)))
            }
            lastIndex = end + marker.length
        }
        append(text.substring(lastIndex))
    }
}

@Composable
fun MarkdownText(
    modifier: Modifier = Modifier,
    text: String,
    style: TextStyle = TextStyle()
) {
    val annotatedString = createAnnotatedString(text)
    Text(
        modifier = modifier,
        text = annotatedString,
        style = style
    )
}

@Preview(showBackground = true)
@Composable
fun MarkdownTextPreview() {
    MarkdownText(
        modifier = Modifier.padding(8.dp),
        text = "*Scan* as **Gift** or ***Store Credit***",
        style = TextStyle(fontSize = 20.sp)
    )
}
