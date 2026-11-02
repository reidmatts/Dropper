package mod.dropper.com.presentation.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

@SuppressLint("ClickableViewAccessibility")
@Composable
fun ModrinthWebsiteContent(
    rawMarkdownOrHtml: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val textColorHex = remember(colorScheme.onSurface) { colorScheme.onSurface.toCssHex() }
    val mutedTextHex = remember(colorScheme.onSurfaceVariant) { colorScheme.onSurfaceVariant.toCssHex() }
    val primaryHex = remember(colorScheme.primary) { colorScheme.primary.toCssHex() }
    val codeBgHex = remember(colorScheme.surfaceContainerHighest) { colorScheme.surfaceContainerHighest.toCssHex() }
    val borderHex = remember(colorScheme.outlineVariant) { colorScheme.outlineVariant.toCssHex() }

    val fullHtml = remember(rawMarkdownOrHtml, textColorHex, primaryHex, codeBgHex, borderHex) {
        ModrinthHtmlFormatter.toWebsiteHtml(
            raw = rawMarkdownOrHtml,
            textColorHex = textColorHex,
            mutedTextHex = mutedTextHex,
            primaryColorHex = primaryHex,
            codeBgHex = codeBgHex,
            borderColorHex = borderHex
        )
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = false

                // Prevent ModalBottomSheet from intercepting vertical scroll gestures inside the webpage
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    false
                }

                settings.apply {
                    javaScriptEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    loadsImagesAutomatically = true
                    domStorageEnabled = false
                    loadWithOverviewMode = true
                    useWideViewPort = false
                    defaultTextEncodingName = "utf-8"
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val scheme = request?.url?.scheme?.lowercase() ?: return null
                        // Strictly block any local file://, content://, or non-HTTPS subresource requests
                        if (scheme != "https") {
                            return WebResourceResponse(
                                "text/plain",
                                "UTF-8",
                                ByteArrayInputStream(ByteArray(0))
                            )
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val uri = request?.url ?: return true
                        val scheme = uri.scheme?.lowercase()
                        if (scheme == "https" || scheme == "http") {
                            runCatching {
                                val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                    addCategory(Intent.CATEGORY_BROWSABLE)
                                }
                                context.startActivity(browserIntent)
                            }
                        }
                        // Always block internal WebView navigation
                        return true
                    }
                }
            }
        },
        update = { webView ->
            if (webView.tag != fullHtml) {
                webView.tag = fullHtml
                webView.loadDataWithBaseURL(
                    "https://modrinth.com/",
                    fullHtml,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    )
}

private fun Color.toCssHex(): String {
    val argb = this.toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return String.format("#%02X%02X%02X", r, g, b)
}

object ModrinthHtmlFormatter {

    private val HTML_COMMENT_REGEX = Regex("""<!--[\s\S]*?-->""")
    private val DANGEROUS_BLOCK_TAG_REGEX = Regex(
        """<(script|iframe|object|embed|style|form|applet|svg)\b[^>]*>[\s\S]*?</\1\s*>""",
        RegexOption.IGNORE_CASE
    )
    private val DANGEROUS_VOID_TAG_REGEX = Regex(
        """</?(script|iframe|object|embed|meta|base|link|style|form|input|button|applet)\b[^>]*>""",
        RegexOption.IGNORE_CASE
    )
    private val INLINE_EVENT_ATTR_REGEX = Regex(
        """\s+on[a-zA-Z]+\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)""",
        RegexOption.IGNORE_CASE
    )
    private val DANGEROUS_URI_ATTR_REGEX = Regex(
        """(href|src)\s*=\s*["']?\s*(javascript:|file:|content:|data:|vbscript:|intent:)[^"'\s>]*["']?""",
        RegexOption.IGNORE_CASE
    )
    private val FENCED_CODE_REGEX = Regex("""```([a-zA-Z0-9_-]*)\s*\n([\s\S]*?)```""")
    private val MULTILINE_HTML_TAG_REGEX = Regex("""<([/!]?[a-zA-Z][a-zA-Z0-9:-]*)\b([^>]*?)>""")
    private val LINKED_IMAGE_REGEX = Regex(
        """\[\s*!\[\s*([^\]]*?)\s*\]\(\s*([^\)\s]+?)(?:\s+"[^"]*")?\s*\)\s*\]\(\s*([^\)\s]+?)\s*\)"""
    )
    private val IMAGE_REGEX = Regex(
        """!\[\s*([^\]]*?)\s*\]\(\s*([^\)\s]+?)(?:\s+"[^"]*")?\s*\)"""
    )
    private val LINK_REGEX = Regex(
        """(?<!!)\[\s*([^\]]+?)\s*\]\(\s*([^\)\s]+?)(?:\s+"[^"]*")?\s*\)"""
    )
    private val INLINE_CODE_REGEX = Regex("""`([^`\n]+)`""")
    private val BOLD_ITALIC_REGEX = Regex("""\*\*\*([^\*\n]+)\*\*\*""")
    private val BOLD_AST_REGEX = Regex("""\*\*([^\*\n]+)\*\*""")
    private val BOLD_UND_REGEX = Regex("""__([^_\n]+)__""")
    private val ITALIC_AST_REGEX = Regex("""(?<!\*)\*([^\*\n]+)\*(?!\*)""")
    private val STRIKE_REGEX = Regex("""~~([^~\n]+)~~""")

    fun toWebsiteHtml(
        raw: String,
        textColorHex: String,
        mutedTextHex: String,
        primaryColorHex: String,
        codeBgHex: String,
        borderColorHex: String
    ): String {
        val bodyHtml = convertMarkdownAndHtml(raw)
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                <style>
                    * {
                        box-sizing: border-box;
                        word-wrap: break-word;
                        overflow-wrap: break-word;
                    }
                    body {
                        margin: 0;
                        padding: 4px 2px 36px 2px;
                        background: transparent;
                        color: $textColorHex;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        font-size: 14.5px;
                        line-height: 1.6;
                    }
                    h1, h2, h3, h4, h5, h6 {
                        color: $textColorHex;
                        margin-top: 18px;
                        margin-bottom: 8px;
                        font-weight: 700;
                        line-height: 1.3;
                    }
                    h1 { font-size: 1.4em; border-bottom: 1px solid $borderColorHex; padding-bottom: 6px; }
                    h2 { font-size: 1.25em; border-bottom: 1px solid $borderColorHex; padding-bottom: 5px; }
                    h3 { font-size: 1.1em; }
                    p {
                        margin-top: 0;
                        margin-bottom: 12px;
                    }
                    a {
                        color: $primaryColorHex;
                        text-decoration: none;
                        font-weight: 500;
                    }
                    img {
                        max-width: 100% !important;
                        height: auto !important;
                        border-radius: 10px;
                        display: block;
                        margin: 10px auto;
                    }
                    a.badge-link {
                        display: inline-block;
                        margin: 3px 4px 3px 0;
                        vertical-align: middle;
                    }
                    a.badge-link img, img.badge-img, img[src*="shields.io"], img[src*="badge"] {
                        display: inline-block !important;
                        max-height: 36px !important;
                        width: auto !important;
                        margin: 0 !important;
                        border-radius: 6px !important;
                        vertical-align: middle;
                    }
                    center, div[align="center"] {
                        text-align: center;
                    }
                    center img, div[align="center"] img {
                        margin-left: auto;
                        margin-right: auto;
                    }
                    hr {
                        border: 0;
                        border-top: 1px solid $borderColorHex;
                        margin: 16px 0;
                    }
                    pre {
                        background: $codeBgHex;
                        padding: 12px;
                        border-radius: 10px;
                        overflow-x: auto;
                        font-family: monospace;
                        font-size: 13px;
                        border: 1px solid $borderColorHex;
                    }
                    code {
                        background: $codeBgHex;
                        padding: 2px 5px;
                        border-radius: 5px;
                        font-family: monospace;
                        font-size: 0.9em;
                    }
                    pre code {
                        background: transparent;
                        padding: 0;
                        border-radius: 0;
                    }
                    blockquote {
                        margin: 10px 0;
                        padding: 8px 14px;
                        border-left: 4px solid $primaryColorHex;
                        background: $codeBgHex;
                        color: $mutedTextHex;
                        border-radius: 0 8px 8px 0;
                    }
                    ul, ol {
                        padding-left: 22px;
                        margin-top: 4px;
                        margin-bottom: 12px;
                    }
                    li {
                        margin-bottom: 4px;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin: 12px 0;
                        font-size: 13.5px;
                    }
                    th, td {
                        border: 1px solid $borderColorHex;
                        padding: 8px;
                        text-align: left;
                    }
                    th {
                        background: $codeBgHex;
                    }
                    details {
                        background: $codeBgHex;
                        padding: 10px 12px;
                        border-radius: 8px;
                        margin-bottom: 10px;
                        border: 1px solid $borderColorHex;
                    }
                    summary {
                        font-weight: 600;
                        cursor: pointer;
                    }
                </style>
            </head>
            <body>
                $bodyHtml
            </body>
            </html>
        """.trimIndent()
    }

    private fun convertMarkdownAndHtml(input: String): String {
        if (input.isBlank()) return "<p>No description available.</p>"

        // 1. Strip HTML comments
        var text = HTML_COMMENT_REGEX.replace(input, "")

        // 2. Extract fenced code blocks into placeholders before HTML/Markdown transforms
        val codeBlocks = mutableListOf<String>()
        text = FENCED_CODE_REGEX.replace(text) { match ->
            val codeContent = escapeHtml(match.groupValues[2].trimEnd())
            val index = codeBlocks.size
            codeBlocks.add("<pre><code>$codeContent</code></pre>")
            "@@CODE_BLOCK_${index}@@"
        }

        // 3. Sanitize raw HTML: strip dangerous tags, inline event handlers, and non-HTTP(S) schemes
        text = DANGEROUS_BLOCK_TAG_REGEX.replace(text, "")
        text = DANGEROUS_VOID_TAG_REGEX.replace(text, "")
        text = MULTILINE_HTML_TAG_REGEX.replace(text) { match ->
            val tagName = match.groupValues[1]
            var attrs = match.groupValues[2].replace(Regex("""\s*\n\s*"""), " ")
            attrs = INLINE_EVENT_ATTR_REGEX.replace(attrs, "")
            attrs = DANGEROUS_URI_ATTR_REGEX.replace(attrs, "")
            "<$tagName$attrs>"
        }

        // 4. Multi-line linked images [![alt](img)](link) -> badge links
        text = LINKED_IMAGE_REGEX.replace(text) { match ->
            val alt = match.groupValues[1].replace("\n", " ").trim()
            val imgUrl = sanitizeUrl(match.groupValues[2].trim()) ?: return@replace ""
            val linkUrl = sanitizeUrl(match.groupValues[3].trim()) ?: return@replace ""
            """<a href="$linkUrl" class="badge-link"><img src="$imgUrl" alt="${escapeHtml(alt)}" class="badge-img" /></a>"""
        }

        // 5. Standalone markdown images ![alt](img)
        text = IMAGE_REGEX.replace(text) { match ->
            val alt = match.groupValues[1].replace("\n", " ").trim()
            val imgUrl = sanitizeUrl(match.groupValues[2].trim()) ?: return@replace ""
            """<img src="$imgUrl" alt="${escapeHtml(alt)}" />"""
        }

        // 6. Standard markdown links [label](url)
        text = LINK_REGEX.replace(text) { match ->
            val label = escapeHtml(match.groupValues[1].replace("\n", " ").trim())
            val url = sanitizeUrl(match.groupValues[2].trim()) ?: return@replace label
            """<a href="$url">$label</a>"""
        }

        // 7. Line-by-line block & inline formatting
        val lines = text.lines()
        val sb = StringBuilder()
        var inUl = false
        var inOl = false

        fun closeLists() {
            if (inUl) {
                sb.append("</ul>\n")
                inUl = false
            }
            if (inOl) {
                sb.append("</ol>\n")
                inOl = false
            }
        }

        for (rawLine in lines) {
            val trimmed = rawLine.trim()

            if (trimmed.isEmpty()) {
                closeLists()
                sb.append("\n")
                continue
            }

            // Horizontal rules
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                closeLists()
                sb.append("<hr />\n")
                continue
            }

            // Headings
            if (trimmed.startsWith("#")) {
                val level = trimmed.takeWhile { it == '#' }.length
                if (level in 1..6 && trimmed.getOrNull(level) == ' ') {
                    closeLists()
                    val headingText = formatInlineMarkdown(trimmed.substring(level + 1).trim())
                    sb.append("<h$level>$headingText</h$level>\n")
                    continue
                }
            }

            // Blockquote
            if (trimmed.startsWith("> ")) {
                closeLists()
                val quoteContent = formatInlineMarkdown(trimmed.removePrefix("> ").trim())
                sb.append("<blockquote>$quoteContent</blockquote>\n")
                continue
            }

            // Unordered list item
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
                if (inOl) {
                    sb.append("</ol>\n")
                    inOl = false
                }
                if (!inUl) {
                    sb.append("<ul>\n")
                    inUl = true
                }
                val itemContent = formatInlineMarkdown(trimmed.substring(2).trim())
                sb.append("<li>$itemContent</li>\n")
                continue
            }

            // Ordered list item
            val olMatch = Regex("""^(\d+)\.\s+(.*)$""").find(trimmed)
            if (olMatch != null) {
                if (inUl) {
                    sb.append("</ul>\n")
                    inUl = false
                }
                if (!inOl) {
                    sb.append("<ol>\n")
                    inOl = true
                }
                val itemContent = formatInlineMarkdown(olMatch.groupValues[2].trim())
                sb.append("<li>$itemContent</li>\n")
                continue
            }

            closeLists()

            // Check if line is already a raw HTML tag or code block placeholder
            if (trimmed.startsWith("@@CODE_BLOCK_") ||
                (trimmed.startsWith("<") && !trimmed.startsWith("<a "))
            ) {
                sb.append(formatInlineMarkdown(rawLine)).append("\n")
            } else {
                sb.append("<p>").append(formatInlineMarkdown(rawLine)).append("</p>\n")
            }
        }
        closeLists()

        var finalHtml = sb.toString()
        for ((index, blockHtml) in codeBlocks.withIndex()) {
            finalHtml = finalHtml.replace("@@CODE_BLOCK_${index}@@", blockHtml)
        }
        return finalHtml
    }

    private fun sanitizeUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        val lower = trimmed.lowercase()
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            return null
        }
        return trimmed
            .replace("\"", "%22")
            .replace("'", "%27")
            .replace("<", "%3C")
            .replace(">", "%3E")
    }

    private fun formatInlineMarkdown(line: String): String {
        var result = line
        result = INLINE_CODE_REGEX.replace(result) { "<code>${escapeHtml(it.groupValues[1])}</code>" }
        result = BOLD_ITALIC_REGEX.replace(result) { "<strong><em>${it.groupValues[1]}</em></strong>" }
        result = BOLD_AST_REGEX.replace(result) { "<strong>${it.groupValues[1]}</strong>" }
        result = BOLD_UND_REGEX.replace(result) { "<strong>${it.groupValues[1]}</strong>" }
        result = ITALIC_AST_REGEX.replace(result) { "<em>${it.groupValues[1]}</em>" }
        result = STRIKE_REGEX.replace(result) { "<del>${it.groupValues[1]}</del>" }
        return result
    }

    private fun escapeHtml(raw: String): String {
        return raw
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
