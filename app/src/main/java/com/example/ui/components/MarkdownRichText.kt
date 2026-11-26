package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFamily

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletItem(val marker: String, val text: String) : MarkdownBlock
    data class CodeBlock(val code: String) : MarkdownBlock
}

@Composable
fun MarkdownRichText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val colorScheme = MaterialTheme.colorScheme

    SelectionContainer(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> {
                        val isFinalAnswer = block.text.contains("Final Answer", ignoreCase = true) ||
                            block.text.contains("ចម្លើយ", ignoreCase = true)
                        val style = when (block.level) {
                            1, 2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        }
                        val tint = if (isFinalAnswer) {
                            colorScheme.tertiary
                        } else {
                            colorScheme.primary
                        }
                        Text(
                            text = parseInlineFormatting(block.text, colorScheme.primary, colorScheme.surfaceVariant),
                            style = style.copy(fontWeight = FontWeight.Bold),
                            color = tint,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    is MarkdownBlock.Paragraph -> {
                        Text(
                            text = parseInlineFormatting(block.text, colorScheme.primary, colorScheme.surfaceVariant),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onSurface
                        )
                    }

                    is MarkdownBlock.BulletItem -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = block.marker,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = colorScheme.primary
                            )
                            Text(
                                text = parseInlineFormatting(block.text, colorScheme.primary, colorScheme.surfaceVariant),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    is MarkdownBlock.CodeBlock -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .border(
                                    width = 1.dp,
                                    color = colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = block.code,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                ),
                                color = colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseMarkdownBlocks(input: String): List<MarkdownBlock> {
    val lines = input.replace("\r\n", "\n").lines()
    val result = mutableListOf<MarkdownBlock>()
    val paragraphBuffer = StringBuilder()
    val codeBuffer = StringBuilder()
    var inCodeBlock = false

    fun flushParagraph() {
        if (paragraphBuffer.isNotBlank()) {
            result.add(MarkdownBlock.Paragraph(paragraphBuffer.toString().trim()))
            paragraphBuffer.clear()
        }
    }

    for (rawLine in lines) {
        val trimmed = rawLine.trim()
        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                result.add(MarkdownBlock.CodeBlock(codeBuffer.toString().trimEnd()))
                codeBuffer.clear()
                inCodeBlock = false
            } else {
                flushParagraph()
                inCodeBlock = true
            }
            continue
        }

        if (inCodeBlock) {
            codeBuffer.appendLine(rawLine)
            continue
        }

        if (trimmed.isEmpty()) {
            flushParagraph()
            continue
        }

        val headingMatch = Regex("^(#{1,4})\\s+(.+)$").find(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            result.add(
                MarkdownBlock.Heading(
                    level = headingMatch.groupValues[1].length,
                    text = headingMatch.groupValues[2]
                )
            )
            continue
        }

        val numberedMatch = Regex("^(\\d+[.)])\\s+(.+)$").find(trimmed)
        if (numberedMatch != null) {
            flushParagraph()
            result.add(
                MarkdownBlock.BulletItem(
                    marker = numberedMatch.groupValues[1],
                    text = numberedMatch.groupValues[2]
                )
            )
            continue
        }

        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")) {
            flushParagraph()
            result.add(
                MarkdownBlock.BulletItem(
                    marker = "•",
                    text = trimmed.drop(2).trimStart()
                )
            )
            continue
        }

        if (paragraphBuffer.isNotEmpty()) paragraphBuffer.append('\n')
        paragraphBuffer.append(trimmed)
    }

    if (inCodeBlock && codeBuffer.isNotBlank()) {
        result.add(MarkdownBlock.CodeBlock(codeBuffer.toString().trimEnd()))
    }
    flushParagraph()
    return result
}

private fun parseInlineFormatting(
    text: String,
    accentColor: androidx.compose.ui.graphics.Color,
    codeBgColor: androidx.compose.ui.graphics.Color
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }

            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Medium,
                            color = accentColor,
                            background = codeBgColor
                        )
                    ) {
                        append(" ${text.substring(i + 1, end)} ")
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }

            text[i] == '*' && (i + 1 < text.length && text[i + 1] != ' ') -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }

            else -> {
                append(text[i])
                i++
            }
        }
    }
}
