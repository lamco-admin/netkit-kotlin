package io.lamco.netkit.exporttools.exporter

import io.lamco.netkit.exporttools.model.*
import java.io.StringWriter
import java.io.Writer

/**
 * Markdown generator for WiFi Intelligence reports.
 *
 * Generates GitHub-Flavored Markdown (GFM) with support for:
 * - Headers (H1-H6)
 * - Emphasis (bold, italic, strikethrough)
 * - Lists (ordered, unordered, nested)
 * - Tables (GFM-style)
 * - Code blocks (fenced with syntax highlighting)
 * - Links and images
 * - Blockquotes
 * - Horizontal rules
 *
 * ## Features
 * - **GFM Tables**: Pipe-delimited tables with alignment
 * - **Code Blocks**: Fenced code blocks with language tags
 * - **Auto-linking**: URLs and email addresses
 * - **Escaping**: Proper Markdown special character escaping
 * - **Task Lists**: GitHub-style checkboxes
 * - **Emphasis**: Bold, italic, strikethrough
 *
 * ## Standards Compliance
 * - CommonMark: Core Markdown specification
 * - GitHub-Flavored Markdown (GFM): Extensions for tables, task lists, etc.
 *
 * @since 1.0.0
 */
class MarkdownGenerator {
    /**
     * Generates Markdown document from data.
     *
     * @param data Data to generate from
     * @param config Export configuration
     * @return ExportResult with Markdown string
     */
    fun export(
        data: Any?,
        config: ExportConfiguration,
    ): ExportResult {
        require(config.format == ExportFormat.MARKDOWN) {
            "MarkdownGenerator requires MARKDOWN format, got: ${config.format}"
        }

        val startTime = System.currentTimeMillis()

        return try {
            val writer = StringWriter()
            val mdWriter = MarkdownWriter(writer)

            when (data) {
                is Map<*, *> -> generateFromMap(mdWriter, data)
                is List<*> -> generateFromList(mdWriter, data)
                else -> mdWriter.text(data?.toString() ?: "")
            }

            val content = writer.toString()
            val endTime = System.currentTimeMillis()

            ExportResult.success(
                content = content,
                format = ExportFormat.MARKDOWN,
                sizeBytes = content.toByteArray(Charsets.UTF_8).size.toLong(),
                durationMillis = endTime - startTime,
            )
        } catch (e: IllegalArgumentException) {
            ExportResult.failure(
                format = ExportFormat.MARKDOWN,
                error = "Markdown generation failed: ${e.message}",
            )
        }
    }

    /**
     * Generates Markdown from map (key-value structure).
     */
    private fun generateFromMap(
        writer: MarkdownWriter,
        map: Map<*, *>,
    ) {
        for ((key, value) in map) {
            writer.header(2, key.toString())
            when (value) {
                is List<*> -> writer.list(value.map { it?.toString() ?: "" })
                is Map<*, *> -> generateFromMap(writer, value)
                else -> writer.text(value?.toString() ?: "")
            }
            writer.blankLine()
        }
    }

    /**
     * Generates Markdown from list.
     */
    private fun generateFromList(
        writer: MarkdownWriter,
        list: List<*>,
    ) {
        if (list.isEmpty()) return

        val first = list.first()
        if (first is Map<*, *>) {
            val keys = first.keys.map { it.toString() }
            val rows =
                list.map { item ->
                    val map = item as? Map<*, *> ?: emptyMap<Any, Any>()
                    keys.map { key -> map[key]?.toString() ?: "" }
                }
            writer.table(keys, rows)
        } else {
            writer.list(list.map { it?.toString() ?: "" })
        }
    }
}

/**
 * Markdown writer with GFM support.
 *
 * Provides methods for writing Markdown elements.
 *
 * @property writer Underlying character writer
 *
 * @since 1.0.0
 */
class MarkdownWriter(
    private val writer: Writer,
) {
    /**
     * Writes header.
     *
     * @param level Header level (1-6)
     * @param text Header text
     */
    fun header(
        level: Int,
        text: String,
    ) {
        require(level in 1..6) { "Header level must be 1-6" }
        writer.write("#".repeat(level))
        writer.write(" ")
        writer.write(escape(text))
        writer.write("\n\n")
    }

    /**
     * Writes paragraph text.
     *
     * @param text Paragraph text
     */
    fun text(text: String) {
        writer.write(escape(text))
        writer.write("\n\n")
    }

    /**
     * Writes bold text inline.
     *
     * @param text Text to bold
     * @return This writer for chaining
     */
    fun bold(text: String): MarkdownWriter {
        writer.write("**")
        writer.write(escape(text))
        writer.write("**")
        return this
    }

    /**
     * Writes italic text inline.
     *
     * @param text Text to italicize
     * @return This writer for chaining
     */
    fun italic(text: String): MarkdownWriter {
        writer.write("*")
        writer.write(escape(text))
        writer.write("*")
        return this
    }

    /**
     * Writes code inline.
     *
     * @param text Code text
     * @return This writer for chaining
     */
    fun code(text: String): MarkdownWriter {
        writer.write("`")
        writer.write(text) // No escaping in code
        writer.write("`")
        return this
    }

    /**
     * Writes unordered list.
     *
     * @param items List items
     */
    fun list(items: List<String>) {
        for (item in items) {
            writer.write("- ")
            writer.write(escape(item))
            writer.write("\n")
        }
        writer.write("\n")
    }

    /**
     * Writes ordered list.
     *
     * @param items List items
     */
    fun orderedList(items: List<String>) {
        for ((index, item) in items.withIndex()) {
            writer.write("${index + 1}. ")
            writer.write(escape(item))
            writer.write("\n")
        }
        writer.write("\n")
    }

    /**
     * Writes GFM table.
     *
     * @param headers Table headers
     * @param rows Table rows
     * @param alignment Column alignment (default: left)
     */
    fun table(
        headers: List<String>,
        rows: List<List<String>>,
        alignment: List<Alignment> = headers.map { Alignment.LEFT },
    ) {
        writer.write("| ")
        writer.write(headers.joinToString(" | ") { escape(it) })
        writer.write(" |\n")

        writer.write("| ")
        val separators =
            headers.indices.map { index ->
                val align = alignment.getOrElse(index) { Alignment.LEFT }
                when (align) {
                    Alignment.LEFT -> ":---"
                    Alignment.CENTER -> ":---:"
                    Alignment.RIGHT -> "---:"
                }
            }
        writer.write(separators.joinToString(" | "))
        writer.write(" |\n")

        for (row in rows) {
            writer.write("| ")
            val paddedRow = row + List(headers.size - row.size) { "" }
            writer.write(paddedRow.take(headers.size).joinToString(" | ") { escape(it) })
            writer.write(" |\n")
        }
        writer.write("\n")
    }

    /**
     * Writes fenced code block.
     *
     * @param code Code content
     * @param language Language for syntax highlighting (optional)
     */
    fun codeBlock(
        code: String,
        language: String = "",
    ) {
        writer.write("```")
        writer.write(language)
        writer.write("\n")
        writer.write(code) // No escaping in code blocks
        writer.write("\n```\n\n")
    }

    /**
     * Writes link.
     *
     * @param text Link text
     * @param url Link URL
     * @return This writer for chaining
     */
    fun link(
        text: String,
        url: String,
    ): MarkdownWriter {
        writer.write("[")
        writer.write(escape(text))
        writer.write("](")
        writer.write(url)
        writer.write(")")
        return this
    }

    /**
     * Writes image.
     *
     * @param altText Alt text
     * @param url Image URL
     * @param title Optional title
     */
    fun image(
        altText: String,
        url: String,
        title: String? = null,
    ) {
        writer.write("![")
        writer.write(escape(altText))
        writer.write("](")
        writer.write(url)
        if (title != null) {
            writer.write(" \"")
            writer.write(escape(title))
            writer.write("\"")
        }
        writer.write(")\n\n")
    }

    /**
     * Writes blockquote.
     *
     * @param text Quote text
     */
    fun blockquote(text: String) {
        for (line in text.lines()) {
            writer.write("> ")
            writer.write(escape(line))
            writer.write("\n")
        }
        writer.write("\n")
    }

    /**
     * Writes horizontal rule.
     */
    fun horizontalRule() {
        writer.write("---\n\n")
    }

    /**
     * Writes blank line.
     */
    fun blankLine() {
        writer.write("\n")
    }

    /**
     * Table column alignment.
     */
    enum class Alignment {
        LEFT,
        CENTER,
        RIGHT,
    }

    /**
     * Escapes Markdown special characters.
     *
     * Escapes: \\ ` * _ { } [ ] ( ) # + - . ! |
     */
    private fun escape(text: String): String =
        text
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("*", "\\*")
            .replace("_", "\\_")
            .replace("{", "\\{")
            .replace("}", "\\}")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("#", "\\#")
            .replace("+", "\\+")
            .replace("-", "\\-")
            .replace(".", "\\.")
            .replace("!", "\\!")
            .replace("|", "\\|")
}
