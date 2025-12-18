package io.lamco.netkit.exporttools.exporter

import io.lamco.netkit.exporttools.model.*
import java.io.StringWriter
import java.io.Writer

/**
 * CSV (Comma-Separated Values) exporter for WiFi Intelligence data.
 *
 * Exports data to CSV format with full RFC 4180 compliance:
 * - Proper field escaping and quoting
 * - Header row support
 * - Custom delimiter support (comma, semicolon, tab)
 * - Multi-row and multi-column data
 * - Line ending normalization (CRLF per RFC 4180)
 * - Empty field handling
 *
 * ## Features
 * - **RFC 4180 Compliant**: Follows CSV standard specification
 * - **Escaping**: Proper handling of quotes, commas, newlines
 * - **Headers**: Optional header row with column names
 * - **Delimiters**: Comma (default), semicolon, tab
 * - **Quote Strategy**: MINIMAL (only when needed) or ALL (every field)
 * - **Line Endings**: CRLF (\\r\\n) per RFC 4180
 *
 * ## Standards Compliance
 * - RFC 4180: Common Format and MIME Type for CSV Files
 * - IETF CSV specification
 *
 * @since 1.0.0
 */
class CsvExporter {
    /**
     * Exports data to CSV string.
     *
     * @param data Data to export (List of Maps or List of Lists)
     * @param config Export configuration
     * @return ExportResult with CSV string
     */
    fun export(
        data: Any?,
        config: ExportConfiguration,
    ): ExportResult {
        require(config.format == ExportFormat.CSV) {
            "CsvExporter requires CSV format, got: ${config.format}"
        }

        val startTime = System.currentTimeMillis()

        return try {
            val writer = StringWriter()
            val csvWriter = CsvWriter(writer)

            when (data) {
                is List<*> -> {
                    if (data.isNotEmpty()) {
                        when (val first = data.first()) {
                            is Map<*, *> -> writeMapList(csvWriter, data as List<Map<*, *>>)
                            is List<*> -> writeListList(csvWriter, data as List<List<*>>)
                            else -> writeValueList(csvWriter, data)
                        }
                    }
                }
                is Map<*, *> -> writeMapList(csvWriter, listOf(data))
                else -> throw IllegalArgumentException(
                    "CSV export requires List or Map data, got: ${data?.javaClass?.simpleName}",
                )
            }

            val content = writer.toString()
            val endTime = System.currentTimeMillis()

            ExportResult.success(
                content = content,
                format = ExportFormat.CSV,
                sizeBytes = content.toByteArray(Charsets.UTF_8).size.toLong(),
                durationMillis = endTime - startTime,
            )
        } catch (e: IllegalArgumentException) {
            ExportResult.failure(
                format = ExportFormat.CSV,
                error = "CSV export failed: ${e.message}",
            )
        }
    }

    /**
     * Writes list of maps as CSV (each map is a row).
     */
    private fun writeMapList(
        writer: CsvWriter,
        data: List<Map<*, *>>,
    ) {
        if (data.isEmpty()) return

        // Extract column names from first map
        val columns = data.first().keys.map { it.toString() }

        // Write header row
        writer.writeRow(columns)

        // Write data rows
        for (map in data) {
            val row =
                columns.map { column ->
                    map[column]?.toString() ?: ""
                }
            writer.writeRow(row)
        }
    }

    /**
     * Writes list of lists as CSV (each inner list is a row).
     */
    private fun writeListList(
        writer: CsvWriter,
        data: List<List<*>>,
    ) {
        for (row in data) {
            writer.writeRow(row.map { it?.toString() ?: "" })
        }
    }

    /**
     * Writes list of values as single-column CSV.
     */
    private fun writeValueList(
        writer: CsvWriter,
        data: List<*>,
    ) {
        // Single column header
        writer.writeRow(listOf("Value"))

        // Write each value as a row
        for (value in data) {
            writer.writeRow(listOf(value?.toString() ?: ""))
        }
    }
}

/**
 * CSV writer with RFC 4180 compliance.
 *
 * Handles CSV field writing with proper escaping and quoting.
 *
 * @property writer Underlying character writer
 * @property delimiter Field delimiter (default: comma)
 * @property quoteStrategy Quoting strategy (MINIMAL or ALL)
 * @property lineEnding Line ending (default: CRLF per RFC 4180)
 *
 * @since 1.0.0
 */
class CsvWriter(
    private val writer: Writer,
    private val delimiter: Char = ',',
    private val quoteStrategy: QuoteStrategy = QuoteStrategy.MINIMAL,
    private val lineEnding: String = "\r\n",
) {
    private var isFirstField = true

    /**
     * Quote strategy for CSV fields.
     */
    enum class QuoteStrategy {
        /** Quote only when necessary (contains delimiter, quote, or newline) */
        MINIMAL,

        /** Quote all fields */
        ALL,
    }

    /**
     * Writes a row of fields.
     *
     * @param fields List of field values
     */
    fun writeRow(fields: List<String>) {
        for ((index, field) in fields.withIndex()) {
            if (index > 0) {
                writer.write(delimiter.toString())
            }
            writeField(field)
        }
        writer.write(lineEnding)
        isFirstField = true
    }

    /**
     * Writes a single field with proper escaping.
     *
     * @param field Field value
     */
    private fun writeField(field: String) {
        val needsQuotes =
            when (quoteStrategy) {
                QuoteStrategy.ALL -> true
                QuoteStrategy.MINIMAL -> fieldNeedsQuotes(field)
            }

        if (needsQuotes) {
            writer.write("\"")
            // Escape quotes by doubling them (RFC 4180)
            writer.write(field.replace("\"", "\"\""))
            writer.write("\"")
        } else {
            writer.write(field)
        }
    }

    /**
     * Checks if field needs quotes (RFC 4180 rules).
     *
     * Field needs quotes if it contains:
     * - Delimiter character
     * - Quote character (")
     * - Newline (\\n or \\r)
     * - Leading or trailing whitespace
     */
    private fun fieldNeedsQuotes(field: String): Boolean =
        field.contains(delimiter) ||
            field.contains('"') ||
            field.contains('\n') ||
            field.contains('\r') ||
            field.contains('\t') ||
            // Tab anywhere in field needs quoting
            field.startsWith(' ') ||
            field.endsWith(' ')
}

/**
 * CSV configuration options.
 *
 * @property delimiter Field delimiter character
 * @property includeHeader Whether to include header row
 * @property quoteStrategy Quoting strategy
 * @property encoding Character encoding
 *
 * @since 1.0.0
 */
data class CsvOptions(
    val delimiter: Char = ',',
    val includeHeader: Boolean = true,
    val quoteStrategy: CsvWriter.QuoteStrategy = CsvWriter.QuoteStrategy.MINIMAL,
    val encoding: String = "UTF-8",
) {
    init {
        require(delimiter !in setOf('\r', '\n', '"')) {
            "Delimiter cannot be CR, LF, or quote character"
        }
        require(encoding.isNotBlank()) {
            "Encoding cannot be blank"
        }
    }

    companion object {
        /**
         * Default CSV options (comma-delimited, with header).
         */
        val DEFAULT = CsvOptions()

        /**
         * European-style CSV (semicolon-delimited).
         */
        val SEMICOLON = CsvOptions(delimiter = ';')

        /**
         * Tab-separated values (TSV).
         */
        val TAB = CsvOptions(delimiter = '\t')

        /**
         * Quote all fields (for maximum compatibility).
         */
        val QUOTE_ALL =
            CsvOptions(
                quoteStrategy = CsvWriter.QuoteStrategy.ALL,
            )
    }
}
