package com.avago.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Lightweight regex-based Markdown-to-[AnnotatedString] converter.
 *
 * Supported syntax:
 *   **text**          → bold
 *   *text*            → italic
 *   `code`            → monospace with background tint
 *   ~~text~~          → strikethrough
 *   [text](url)       → coloured underline link with "URL" string annotation
 *   \n                → newline (pass-through)
 *
 * Order matters: longer delimiters are matched before shorter ones to avoid
 * `**bold**` being partially consumed by the single-star italic rule.
 */
object MarkdownParser {

    // ---------------------------------------------------------------------------
    // Token types
    // ---------------------------------------------------------------------------

    private sealed interface Token {
        data class Literal(val text: String) : Token
        data class Bold(val text: String) : Token
        data class Italic(val text: String) : Token
        data class Code(val text: String) : Token
        data class Strike(val text: String) : Token
        data class Link(val label: String, val url: String) : Token
    }

    // ---------------------------------------------------------------------------
    // Regex patterns  (DOTALL so they can span a single conceptual span)
    // ---------------------------------------------------------------------------

    // Important: ~~, **, [...](...) before * to avoid greedy mismatch
    private val PATTERN = Regex(
        """(?x)
        \[([^\]]*)\]\(([^)]*)\)   # [label](url)
        |~~((?:(?!~~).)+?)~~       # ~~strike~~
        |\*\*((?:(?!\*\*).)+?)\*\* # **bold**
        |`((?:[^`])+?)`            # `code`
        |\*((?:(?!\*).)+?)\*       # *italic*
        """
    )

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Converts [text] to an [AnnotatedString] applying inline Markdown spans.
     *
     * @param linkColor      Colour used for hyperlink text.
     * @param codeBackground Background colour used behind inline code spans.
     */
    fun toAnnotatedString(
        text: String,
        linkColor: Color,
        codeBackground: Color,
    ): AnnotatedString {
        val tokens = tokenize(text)
        return buildAnnotatedString {
            for (token in tokens) {
                when (token) {
                    is Token.Literal -> append(token.text)

                    is Token.Bold -> {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(token.text)
                        pop()
                    }

                    is Token.Italic -> {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(token.text)
                        pop()
                    }

                    is Token.Code -> {
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBackground,
                            )
                        )
                        append(token.text)
                        pop()
                    }

                    is Token.Strike -> {
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                        append(token.text)
                        pop()
                    }

                    is Token.Link -> {
                        val start = length
                        pushStyle(
                            SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            )
                        )
                        append(token.label)
                        pop()
                        addStringAnnotation(
                            tag = "URL",
                            annotation = token.url,
                            start = start,
                            end = length,
                        )
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Tokenizer
    // ---------------------------------------------------------------------------

    private fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var cursor = 0
        for (match in PATTERN.findAll(text)) {
            // Emit literal text before this match
            if (match.range.first > cursor) {
                tokens += Token.Literal(text.substring(cursor, match.range.first))
            }
            tokens += matchToToken(match)
            cursor = match.range.last + 1
        }
        // Remaining literal text after the last match
        if (cursor < text.length) {
            tokens += Token.Literal(text.substring(cursor))
        }
        return tokens
    }

    private fun matchToToken(match: MatchResult): Token {
        val (link, url, strike, bold, code, italic) = match.destructured
        return when {
            link.isNotEmpty() || url.isNotEmpty() -> Token.Link(link, url)
            strike.isNotEmpty()                   -> Token.Strike(strike)
            bold.isNotEmpty()                     -> Token.Bold(bold)
            code.isNotEmpty()                     -> Token.Code(code)
            else                                  -> Token.Italic(italic)
        }
    }
}
