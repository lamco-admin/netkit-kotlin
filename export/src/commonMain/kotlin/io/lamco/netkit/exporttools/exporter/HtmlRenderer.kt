package io.lamco.netkit.exporttools.exporter

import io.lamco.netkit.exporttools.model.*
import java.io.StringWriter
import java.io.Writer

/**
 * HTML renderer for WiFi Intelligence reports.
 *
 * Generates HTML5-compliant documents with:
 * - Semantic HTML5 elements
 * - Embedded CSS styling
 * - Responsive design (mobile-friendly)
 * - Proper HTML escaping
 * - Meta tags and viewport settings
 * - Tables, lists, and structured content
 *
 * ## Features
 * - **HTML5**: Modern semantic elements
 * - **Embedded CSS**: Self-contained styling
 * - **Responsive**: Mobile-first design
 * - **Escaping**: Proper HTML entity escaping
 * - **Accessibility**: ARIA labels and semantic markup
 * - **Print-friendly**: CSS print media queries
 *
 * ## Standards Compliance
 * - HTML5 (WHATWG Living Standard)
 * - CSS3 (W3C)
 * - WCAG 2.1 (Web Content Accessibility Guidelines)
 *
 * @since 1.0.0
 */
class HtmlRenderer {
    /**
     * Renders data to HTML document.
     *
     * @param data Data to render
     * @param config Export configuration
     * @return ExportResult with HTML string
     */
    fun export(
        data: Any?,
        config: ExportConfiguration,
    ): ExportResult {
        require(config.format == ExportFormat.HTML) {
            "HtmlRenderer requires HTML format, got: ${config.format}"
        }

        val startTime = System.currentTimeMillis()

        return try {
            val writer = StringWriter()
            val htmlWriter = HtmlWriter(writer, config.title ?: "WiFi Intelligence Report")

            htmlWriter.beginDocument()

            when (data) {
                is Map<*, *> -> renderFromMap(htmlWriter, data)
                is List<*> -> renderFromList(htmlWriter, data)
                else -> htmlWriter.paragraph(data?.toString() ?: "")
            }

            htmlWriter.endDocument()

            val content = writer.toString()
            val endTime = System.currentTimeMillis()

            ExportResult.success(
                content = content,
                format = ExportFormat.HTML,
                sizeBytes = content.toByteArray(Charsets.UTF_8).size.toLong(),
                durationMillis = endTime - startTime,
            )
        } catch (e: IllegalArgumentException) {
            ExportResult.failure(
                format = ExportFormat.HTML,
                error = "HTML rendering failed: ${e.message}",
            )
        }
    }

    /**
     * Renders data from map structure.
     */
    private fun renderFromMap(
        writer: HtmlWriter,
        map: Map<*, *>,
    ) {
        for ((key, value) in map) {
            writer.heading(2, key.toString())
            when (value) {
                is List<*> -> writer.list(value.map { it?.toString() ?: "" })
                is Map<*, *> -> renderFromMap(writer, value)
                else -> writer.paragraph(value?.toString() ?: "")
            }
        }
    }

    /**
     * Renders data from list.
     */
    private fun renderFromList(
        writer: HtmlWriter,
        list: List<*>,
    ) {
        if (list.isEmpty()) return

        val first = list.first()
        if (first is Map<*, *>) {
            // Table format
            val headers = first.keys.map { it.toString() }
            val rows =
                list.map { item ->
                    val map = item as? Map<*, *> ?: emptyMap<Any, Any>()
                    headers.map { key -> map[key]?.toString() ?: "" }
                }
            writer.table(headers, rows)
        } else {
            // Simple list
            writer.list(list.map { it?.toString() ?: "" })
        }
    }
}

/**
 * HTML writer for generating HTML5 documents.
 *
 * @property writer Underlying character writer
 * @property title Document title
 *
 * @since 1.0.0
 */
class HtmlWriter(
    private val writer: Writer,
    private val title: String,
) {
    private var indent = 0

    /**
     * Begins HTML document with DOCTYPE, head, and body.
     */
    fun beginDocument() {
        writer.write("<!DOCTYPE html>\n")
        writer.write("<html lang=\"en\">\n")
        writer.write("<head>\n")
        writer.write("  <meta charset=\"UTF-8\">\n")
        writer.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
        writer.write("  <title>${escape(title)}</title>\n")
        writeStyles()
        writer.write("</head>\n")
        writer.write("<body>\n")
        writer.write("  <main class=\"container\">\n")
        writer.write("    <h1>${escape(title)}</h1>\n")
        indent = 2
    }

    /**
     * Ends HTML document.
     */
    fun endDocument() {
        writer.write("  </main>\n")
        writer.write("</body>\n")
        writer.write("</html>\n")
    }

    /**
     * Writes embedded CSS styles.
     */
    private fun writeStyles() {
        writer.write("  <style>\n")
        writer.write(
            """
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif; line-height: 1.6; color: #333; background: #f5f5f5; }
            .container { max-width: 1200px; margin: 0 auto; padding: 2rem; background: white; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
            h1 { color: #2c3e50; margin-bottom: 1.5rem; padding-bottom: 0.5rem; border-bottom: 3px solid #3498db; }
            h2 { color: #34495e; margin-top: 2rem; margin-bottom: 1rem; padding-bottom: 0.3rem; border-bottom: 1px solid #e0e0e0; }
            h3 { color: #34495e; margin-top: 1.5rem; margin-bottom: 0.75rem; }
            p { margin-bottom: 1rem; }
            ul, ol { margin-bottom: 1rem; padding-left: 2rem; }
            li { margin-bottom: 0.5rem; }
            table { width: 100%; border-collapse: collapse; margin-bottom: 1.5rem; }
            th { background: #3498db; color: white; padding: 0.75rem; text-align: left; font-weight: 600; }
            td { padding: 0.75rem; border-bottom: 1px solid #e0e0e0; }
            tr:hover { background: #f8f9fa; }
            code { background: #f4f4f4; padding: 0.2rem 0.4rem; border-radius: 3px; font-family: 'Courier New', monospace; }
            @media (max-width: 768px) { .container { padding: 1rem; } table { font-size: 0.9rem; } th, td { padding: 0.5rem; } }
            @media print { body { background: white; } .container { box-shadow: none; } }
            """.trimIndent(),
        )
        writer.write("\n  </style>\n")
    }

    /**
     * Writes heading.
     *
     * @param level Heading level (1-6)
     * @param text Heading text
     */
    fun heading(
        level: Int,
        text: String,
    ) {
        require(level in 1..6) { "Heading level must be 1-6" }
        writeIndent()
        writer.write("<h$level>")
        writer.write(escape(text))
        writer.write("</h$level>\n")
    }

    /**
     * Writes paragraph.
     *
     * @param text Paragraph text
     */
    fun paragraph(text: String) {
        writeIndent()
        writer.write("<p>")
        writer.write(escape(text))
        writer.write("</p>\n")
    }

    /**
     * Writes unordered list.
     *
     * @param items List items
     */
    fun list(items: List<String>) {
        writeIndent()
        writer.write("<ul>\n")
        indent++
        for (item in items) {
            writeIndent()
            writer.write("<li>")
            writer.write(escape(item))
            writer.write("</li>\n")
        }
        indent--
        writeIndent()
        writer.write("</ul>\n")
    }

    /**
     * Writes ordered list.
     *
     * @param items List items
     */
    fun orderedList(items: List<String>) {
        writeIndent()
        writer.write("<ol>\n")
        indent++
        for (item in items) {
            writeIndent()
            writer.write("<li>")
            writer.write(escape(item))
            writer.write("</li>\n")
        }
        indent--
        writeIndent()
        writer.write("</ol>\n")
    }

    /**
     * Writes table.
     *
     * @param headers Table headers
     * @param rows Table rows
     */
    fun table(
        headers: List<String>,
        rows: List<List<String>>,
    ) {
        writeIndent()
        writer.write("<table>\n")
        indent++

        writeIndent()
        writer.write("<thead>\n")
        indent++
        writeIndent()
        writer.write("<tr>\n")
        indent++
        for (header in headers) {
            writeIndent()
            writer.write("<th>")
            writer.write(escape(header))
            writer.write("</th>\n")
        }
        indent--
        writeIndent()
        writer.write("</tr>\n")
        indent--
        writeIndent()
        writer.write("</thead>\n")

        writeIndent()
        writer.write("<tbody>\n")
        indent++
        for (row in rows) {
            writeIndent()
            writer.write("<tr>\n")
            indent++
            val paddedRow = row + List(headers.size - row.size) { "" }
            for (cell in paddedRow.take(headers.size)) {
                writeIndent()
                writer.write("<td>")
                writer.write(escape(cell))
                writer.write("</td>\n")
            }
            indent--
            writeIndent()
            writer.write("</tr>\n")
        }
        indent--
        writeIndent()
        writer.write("</tbody>\n")

        indent--
        writeIndent()
        writer.write("</table>\n")
    }

    /**
     * Writes indentation.
     */
    private fun writeIndent() {
        repeat(indent) {
            writer.write("  ")
        }
    }

    /**
     * Escapes HTML special characters.
     *
     * Escapes: < > & " '
     */
    private fun escape(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}
