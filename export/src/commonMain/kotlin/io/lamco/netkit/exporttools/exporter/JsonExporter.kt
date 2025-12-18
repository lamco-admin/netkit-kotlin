package io.lamco.netkit.exporttools.exporter

import io.lamco.netkit.exporttools.model.*
import java.io.StringWriter
import java.io.Writer
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * JSON exporter for WiFi Intelligence data.
 *
 * Exports data to JSON format with support for:
 * - Pretty-printing and compact formats
 * - Proper escaping of strings
 * - Schema validation (basic)
 * - Streaming for large datasets
 * - ISO 8601 date formatting
 * - Numeric precision control
 *
 * ## Features
 * - **Pretty-print**: Indented, human-readable JSON
 * - **Compact**: Minimal whitespace for size efficiency
 * - **Escaping**: Proper JSON string escaping (\", \\, \n, \r, \t, etc.)
 * - **Types**: Strings, numbers, booleans, null, arrays, objects
 * - **Validation**: Basic structure validation
 *
 * ## Standards Compliance
 * - RFC 8259: JSON Data Interchange Format
 * - ECMA-404: JSON Data Interchange Syntax
 * - ISO 8601: Date and time format
 *
 * @since 1.0.0
 */
class JsonExporter {
    /**
     * Exports data to JSON string.
     *
     * @param data Data to export (Map, List, or primitive types)
     * @param config Export configuration
     * @return ExportResult with JSON string
     */
    fun export(
        data: Any?,
        config: ExportConfiguration,
    ): ExportResult {
        require(config.format == ExportFormat.JSON) {
            "JsonExporter requires JSON format, got: ${config.format}"
        }

        val startTime = System.currentTimeMillis()

        return try {
            val writer = StringWriter()
            val jsonWriter = JsonWriter(writer, config.effectivePrettify)

            jsonWriter.writeValue(data)

            val content = writer.toString()
            val endTime = System.currentTimeMillis()

            val validationResult =
                if (config.effectiveValidate) {
                    validateJson(content)
                } else {
                    ValidationResult.success()
                }

            if (validationResult.isValid) {
                ExportResult.success(
                    content = content,
                    format = ExportFormat.JSON,
                    sizeBytes = content.toByteArray(Charsets.UTF_8).size.toLong(),
                    durationMillis = endTime - startTime,
                    validation = validationResult,
                )
            } else {
                ExportResult.failure(
                    format = ExportFormat.JSON,
                    error = "JSON validation failed",
                    validation = validationResult,
                )
            }
        } catch (e: IllegalArgumentException) {
            ExportResult.failure(
                format = ExportFormat.JSON,
                error = "JSON export failed: ${e.message}",
                validation =
                    ValidationResult.failure(
                        ValidationIssue.format("Export error: ${e.message}"),
                    ),
            )
        }
    }

    /**
     * Validates JSON string for basic correctness.
     *
     * @param json JSON string to validate
     * @return ValidationResult
     */
    private fun validateJson(json: String): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        // Check balanced braces and brackets
        var braceDepth = 0
        var bracketDepth = 0
        var inString = false
        var escaped = false

        for (c in json) {
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString ->
                    when (c) {
                        '{' -> braceDepth++
                        '}' -> braceDepth--
                        '[' -> bracketDepth++
                        ']' -> bracketDepth--
                    }
            }
        }

        if (braceDepth != 0) {
            issues.add(
                ValidationIssue.format(
                    "Unbalanced braces: $braceDepth unclosed",
                ),
            )
        }
        if (bracketDepth != 0) {
            issues.add(
                ValidationIssue.format(
                    "Unbalanced brackets: $bracketDepth unclosed",
                ),
            )
        }
        if (inString) {
            issues.add(
                ValidationIssue.format(
                    "Unclosed string",
                ),
            )
        }

        return if (issues.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(issues)
        }
    }
}

/**
 * JSON writer with pretty-print support.
 *
 * Handles JSON serialization with proper escaping and formatting.
 *
 * @property writer Underlying character writer
 * @property prettyPrint Enable pretty-printing with indentation
 * @property indent Indentation string (default: 2 spaces)
 *
 * @since 1.0.0
 */
class JsonWriter(
    private val writer: Writer,
    private val prettyPrint: Boolean = true,
    private val indent: String = "  ",
) {
    private var currentIndent = 0
    private var needsComma = false

    /**
     * Writes any value as JSON.
     *
     * @param value Value to write (null, Boolean, Number, String, List, Map)
     */
    fun writeValue(value: Any?) {
        when (value) {
            null -> writeNull()
            is Boolean -> writeBoolean(value)
            is Number -> writeNumber(value)
            is String -> writeString(value)
            is List<*> -> writeArray(value)
            is Map<*, *> -> writeObject(value)
            is Instant -> writeString(formatInstant(value))
            else -> writeString(value.toString())
        }
    }

    /**
     * Writes null.
     */
    private fun writeNull() {
        writer.write("null")
    }

    /**
     * Writes boolean.
     */
    private fun writeBoolean(value: Boolean) {
        writer.write(if (value) "true" else "false")
    }

    /**
     * Writes number with proper formatting.
     */
    private fun writeNumber(value: Number) {
        when (value) {
            is Double -> {
                if (value.isNaN() || value.isInfinite()) {
                    writeNull() // JSON doesn't support NaN/Infinity
                } else {
                    writer.write(value.toString())
                }
            }
            is Float -> {
                if (value.isNaN() || value.isInfinite()) {
                    writeNull()
                } else {
                    writer.write(value.toString())
                }
            }
            else -> writer.write(value.toString())
        }
    }

    /**
     * Writes string with proper JSON escaping.
     *
     * Escapes: \", \\, \n, \r, \t, \b, \f, and control characters
     */
    private fun writeString(value: String) {
        writer.write("\"")
        for (c in value) {
            when (c) {
                '"' -> writer.write("\\\"")
                '\\' -> writer.write("\\\\")
                '\n' -> writer.write("\\n")
                '\r' -> writer.write("\\r")
                '\t' -> writer.write("\\t")
                '\b' -> writer.write("\\b")
                '\u000C' -> writer.write("\\f") // Form feed
                in '\u0000'..'\u001F' -> {
                    // Control characters
                    writer.write("\\u")
                    writer.write(String.format("%04x", c.code))
                }
                else -> writer.write(c.toString())
            }
        }
        writer.write("\"")
    }

    /**
     * Writes array.
     */
    private fun writeArray(list: List<*>) {
        writer.write("[")
        if (list.isNotEmpty()) {
            if (prettyPrint) {
                currentIndent++
                for ((index, item) in list.withIndex()) {
                    writeLine()
                    writeIndent()
                    writeValue(item)
                    if (index < list.size - 1) {
                        writer.write(",")
                    }
                }
                currentIndent--
                writeLine()
                writeIndent()
            } else {
                for ((index, item) in list.withIndex()) {
                    writeValue(item)
                    if (index < list.size - 1) {
                        writer.write(",")
                    }
                }
            }
        }
        writer.write("]")
    }

    /**
     * Writes object.
     */
    private fun writeObject(map: Map<*, *>) {
        writer.write("{")
        if (map.isNotEmpty()) {
            if (prettyPrint) {
                currentIndent++
                val entries = map.entries.toList()
                for ((index, entry) in entries.withIndex()) {
                    writeLine()
                    writeIndent()
                    writeString(entry.key.toString())
                    writer.write(": ")
                    writeValue(entry.value)
                    if (index < entries.size - 1) {
                        writer.write(",")
                    }
                }
                currentIndent--
                writeLine()
                writeIndent()
            } else {
                val entries = map.entries.toList()
                for ((index, entry) in entries.withIndex()) {
                    writeString(entry.key.toString())
                    writer.write(":")
                    writeValue(entry.value)
                    if (index < entries.size - 1) {
                        writer.write(",")
                    }
                }
            }
        }
        writer.write("}")
    }

    /**
     * Writes newline (if pretty-print enabled).
     */
    private fun writeLine() {
        if (prettyPrint) {
            writer.write('\n'.toString())
        }
    }

    /**
     * Writes indentation (if pretty-print enabled).
     */
    private fun writeIndent() {
        if (prettyPrint) {
            repeat(currentIndent) {
                writer.write(indent)
            }
        }
    }

    /**
     * Formats Instant to ISO 8601 string.
     */
    private fun formatInstant(instant: Instant): String = DateTimeFormatter.ISO_INSTANT.format(instant)
}
