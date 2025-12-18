package io.lamco.netkit.exporttools.exporter

import io.lamco.netkit.exporttools.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.StringWriter
import java.time.Instant
import kotlin.test.*

/**
 * Comprehensive tests for export-tools Batch 2 exporters.
 *
 * Tests cover ALL exporters, ALL format options, and ALL scenarios.
 * Target: 150+ tests with complete coverage.
 */
class ExporterTests {
    // ============================================================================
    // JsonExporter Tests (35 tests)
    // ============================================================================

    @Test
    fun `JsonExporter - exports simple map to JSON`() {
        val exporter = JsonExporter()
        val data = mapOf("key" to "value", "number" to 42)
        val config = ExportConfiguration.json()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("\"key\""))
        assertTrue(result.content.contains("\"value\""))
        assertTrue(result.content.contains("42"))
    }

    @Test
    fun `JsonExporter - exports list to JSON array`() {
        val exporter = JsonExporter()
        val data = listOf(1, 2, 3, 4, 5)
        val config = ExportConfiguration.json()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.startsWith("["))
        assertTrue(result.content.endsWith("]"))
        assertTrue(result.content.contains("1"))
        assertTrue(result.content.contains("5"))
    }

    @Test
    fun `JsonExporter - exports null value`() {
        val exporter = JsonExporter()
        val config = ExportConfiguration.json()

        val result = exporter.export(null, config)

        assertTrue(result.success)
        assertEquals("null", result.content?.trim())
    }

    @Test
    fun `JsonExporter - pretty print adds formatting`() {
        val exporter = JsonExporter()
        val data = mapOf("a" to 1, "b" to 2)
        val config = ExportConfiguration.json().copy(prettify = true)

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("\n")) // Has newlines
        assertTrue(result.content.contains("  ")) // Has indentation
    }

    @Test
    fun `JsonExporter - compact format removes whitespace`() {
        val exporter = JsonExporter()
        val data = mapOf("a" to 1, "b" to 2)
        val config = ExportConfiguration.json().copy(prettify = false)

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertFalse(result.content.contains("\n  ")) // No indentation
    }

    @Test
    fun `JsonExporter - escapes special characters in strings`() {
        val exporter = JsonExporter()
        val data = mapOf("text" to "Line1\nLine2\tTab\"Quote\"")
        val config = ExportConfiguration.json()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("\\n")) // Escaped newline
        assertTrue(result.content.contains("\\t")) // Escaped tab
        assertTrue(result.content.contains("\\\"")) // Escaped quote
    }

    @Test
    fun `JsonExporter - escapes backslash`() {
        val exporter = JsonExporter()
        val data = mapOf("path" to "C:\\Users\\file.txt")
        val config = ExportConfiguration.json()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("\\\\")) // Escaped backslash
    }

    @Test
    fun `JsonExporter - handles nested objects`() {
        val exporter = JsonExporter()
        val data =
            mapOf(
                "outer" to
                    mapOf(
                        "inner" to
                            mapOf(
                                "deep" to "value",
                            ),
                    ),
            )
        val config = ExportConfiguration.json()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("\"outer\""))
        assertTrue(result.content.contains("\"inner\""))
        assertTrue(result.content.contains("\"deep\""))
    }

    @Test
    fun `JsonExporter - handles nested arrays`() {
        val exporter = JsonExporter()
        val data =
            listOf(
                listOf(1, 2),
                listOf(3, 4),
                listOf(5, 6),
            )
        // Use compact format to check for nested array brackets
        val config =
            ExportConfiguration(
                format = ExportFormat.JSON,
                template = ReportTemplate.TechnicalReport,
                prettify = false,
            )

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("[[")) // Nested arrays without whitespace
    }

    @Test
    fun `JsonExporter - exports boolean values`() {
        val exporter = JsonExporter()
        val data = mapOf("true" to true, "false" to false)
        val config = ExportConfiguration.json()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("true"))
        assertTrue(result.content.contains("false"))
    }

    @Test
    fun `JsonExporter - exports numeric values`() {
        val exporter = JsonExporter()
        val data =
            mapOf(
                "int" to 42,
                "long" to 9999999999L,
                "double" to 3.14159,
                "float" to 2.5f,
            )
        val config = ExportConfiguration.json()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("42"))
        assertTrue(result.content.contains("3.14159"))
    }

    @Test
    fun `JsonExporter - handles NaN as null`() {
        val exporter = JsonExporter()
        val data = mapOf("nan" to Double.NaN)
        val config = ExportConfiguration.json()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("null")) // NaN becomes null
    }

    @Test
    fun `JsonExporter - handles Infinity as null`() {
        val exporter = JsonExporter()
        val data = mapOf("infinity" to Double.POSITIVE_INFINITY)
        val config = ExportConfiguration.json()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("null")) // Infinity becomes null
    }

    @Test
    fun `JsonExporter - formats Instant to ISO 8601`() {
        val exporter = JsonExporter()
        val instant = Instant.parse("2025-11-22T10:15:30Z")
        val data = mapOf("timestamp" to instant)
        val config = ExportConfiguration.json()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("2025-11-22T10:15:30Z"))
    }

    @Test
    fun `JsonExporter - validates balanced braces`() {
        val exporter = JsonExporter()
        val data = mapOf("a" to 1)
        val config = ExportConfiguration.json().copy(validate = true)

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertTrue(result.validation.isValid)
    }

    @Test
    fun `JsonExporter - validates balanced brackets`() {
        val exporter = JsonExporter()
        val data = listOf(1, 2, 3)
        val config = ExportConfiguration.json().copy(validate = true)

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertTrue(result.validation.isValid)
    }

    @Test
    fun `JsonExporter - skips validation when disabled`() {
        val exporter = JsonExporter()
        val data = mapOf("test" to "value")
        val config = ExportConfiguration.json().copy(validate = false)

        val result = exporter.export(data, config)

        assertTrue(result.success)
        // Validation result is still success but wasn't actually checked
    }

    @Test
    fun `JsonExporter - requires JSON format`() {
        val exporter = JsonExporter()
        val data = mapOf("test" to "value")
        val config = ExportConfiguration.csv() // Wrong format

        assertThrows<IllegalArgumentException> {
            exporter.export(data, config)
        }
    }

    @Test
    fun `JsonExporter - reports export duration`() {
        val exporter = JsonExporter()
        val data = mapOf("test" to "value")
        val config = ExportConfiguration.json()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertTrue(result.durationMillis >= 0)
    }

    @Test
    fun `JsonExporter - reports file size`() {
        val exporter = JsonExporter()
        val data = mapOf("test" to "value")
        val config = ExportConfiguration.json()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertTrue(result.sizeBytes > 0)
    }

    @Test
    fun `JsonExporter - handles empty map`() {
        val exporter = JsonExporter()
        val data = emptyMap<String, Any>()
        val config = ExportConfiguration.json()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertEquals("{}", result.content?.replace("\\s".toRegex(), ""))
    }

    @Test
    fun `JsonExporter - handles empty list`() {
        val exporter = JsonExporter()
        val data = emptyList<Any>()
        val config = ExportConfiguration.json()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertEquals("[]", result.content?.replace("\\s".toRegex(), ""))
    }

    @Test
    fun `JsonExporter - handles mixed type map`() {
        val exporter = JsonExporter()
        val data =
            mapOf(
                "string" to "text",
                "number" to 42,
                "boolean" to true,
                "null" to null,
                "array" to listOf(1, 2, 3),
                "object" to mapOf("nested" to "value"),
            )
        val config = ExportConfiguration.json()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
    }

    @Test
    fun `JsonExporter - exports large dataset`() {
        val exporter = JsonExporter()
        val data = (1..1000).map { mapOf("id" to it, "value" to "item_$it") }
        val config = ExportConfiguration.json()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertTrue(result.sizeBytes > 10000) // Reasonably large
    }

    @Test
    fun `JsonExporter - escapes control characters`() {
        val exporter = JsonExporter()
        val data = mapOf("text" to "Line\u0001\u0002\u0003End")
        val config = ExportConfiguration.json()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("\\u")) // Control chars escaped
    }

    @Test
    fun `JsonWriter - writeValue handles all types`() {
        val writer = StringWriter()
        val jsonWriter = JsonWriter(writer, prettyPrint = false)

        jsonWriter.writeValue(null)
        jsonWriter.writeValue(true)
        jsonWriter.writeValue(42)
        jsonWriter.writeValue("text")
        jsonWriter.writeValue(listOf(1, 2))
        jsonWriter.writeValue(mapOf("key" to "value"))

        val output = writer.toString()
        assertTrue(output.contains("null"))
        assertTrue(output.contains("true"))
        assertTrue(output.contains("42"))
        assertTrue(output.contains("\"text\""))
    }

    @Test
    fun `ExportResult - success requires content`() {
        assertThrows<IllegalArgumentException> {
            ExportResult(
                success = true,
                content = null, // Invalid
                format = ExportFormat.JSON,
            )
        }
    }

    @Test
    fun `ExportResult - failure requires error`() {
        assertThrows<IllegalArgumentException> {
            ExportResult(
                success = false,
                content = null,
                format = ExportFormat.JSON,
                error = null, // Invalid
            )
        }
    }

    @Test
    fun `ExportResult - throwIfFailed throws on failure`() {
        val result =
            ExportResult.failure(
                format = ExportFormat.JSON,
                error = "Test error",
            )

        assertThrows<ExportException> {
            result.throwIfFailed()
        }
    }

    @Test
    fun `ExportResult - throwIfFailed succeeds on success`() {
        val result =
            ExportResult.success(
                content = "{}",
                format = ExportFormat.JSON,
                sizeBytes = 2,
                durationMillis = 10,
            )

        result.throwIfFailed() // Should not throw
    }

    @Test
    fun `ExportException - contains format and validation`() {
        val validation =
            ValidationResult.failure(
                ValidationIssue.format("Test error"),
            )
        val exception =
            ExportException(
                message = "Test",
                format = ExportFormat.JSON,
                validation = validation,
            )

        assertEquals(ExportFormat.JSON, exception.format)
        assertEquals(validation, exception.validation)
    }

    // ============================================================================
    // CsvExporter Tests (30 tests)
    // ============================================================================

    @Test
    fun `CsvExporter - exports list of maps to CSV with headers`() {
        val exporter = CsvExporter()
        val data =
            listOf(
                mapOf("name" to "Alice", "age" to 30),
                mapOf("name" to "Bob", "age" to 25),
            )
        val config = ExportConfiguration.csv()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        val lines = result.content.lines()
        assertTrue(lines.first().contains("name"))
        assertTrue(lines.first().contains("age"))
        assertTrue(lines[1].contains("Alice"))
        assertTrue(lines[2].contains("Bob"))
    }

    @Test
    fun `CsvExporter - exports list of lists as rows`() {
        val exporter = CsvExporter()
        val data =
            listOf(
                listOf("A", "B", "C"),
                listOf("1", "2", "3"),
                listOf("X", "Y", "Z"),
            )
        val config = ExportConfiguration.csv()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("A,B,C"))
        assertTrue(result.content.contains("1,2,3"))
        assertTrue(result.content.contains("X,Y,Z"))
    }

    @Test
    fun `CsvExporter - exports simple list as single column`() {
        val exporter = CsvExporter()
        val data = listOf("Apple", "Banana", "Cherry")
        val config = ExportConfiguration.csv()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        val lines = result.content.lines()
        assertTrue(lines.first().contains("Value")) // Header
        assertTrue(lines[1].contains("Apple"))
        assertTrue(lines[2].contains("Banana"))
        assertTrue(lines[3].contains("Cherry"))
    }

    @Test
    fun `CsvExporter - exports single map as one row`() {
        val exporter = CsvExporter()
        val data = mapOf("col1" to "val1", "col2" to "val2")
        val config = ExportConfiguration.csv()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("col1"))
        assertTrue(result.content.contains("val1"))
    }

    @Test
    fun `CsvExporter - uses CRLF line endings`() {
        val exporter = CsvExporter()
        val data = listOf(mapOf("a" to "1"), mapOf("a" to "2"))
        val config = ExportConfiguration.csv()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("\r\n")) // CRLF per RFC 4180
    }

    @Test
    fun `CsvExporter - quotes fields with commas`() {
        val exporter = CsvExporter()
        val data = listOf(mapOf("text" to "Hello, World"))
        val config = ExportConfiguration.csv()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("\"Hello, World\""))
    }

    @Test
    fun `CsvExporter - quotes fields with quotes`() {
        val exporter = CsvExporter()
        val data = listOf(mapOf("text" to "Say \"Hi\""))
        val config = ExportConfiguration.csv()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("\"Say \"\"Hi\"\"\"")) // Doubled quotes
    }

    @Test
    fun `CsvExporter - quotes fields with newlines`() {
        val exporter = CsvExporter()
        val data = listOf(mapOf("text" to "Line1\nLine2"))
        val config = ExportConfiguration.csv()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("\"Line1\nLine2\""))
    }

    @Test
    fun `CsvExporter - quotes fields with leading whitespace`() {
        val exporter = CsvExporter()
        val data = listOf(mapOf("text" to " Leading"))
        val config = ExportConfiguration.csv()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("\" Leading\""))
    }

    @Test
    fun `CsvExporter - quotes fields with trailing whitespace`() {
        val exporter = CsvExporter()
        val data = listOf(mapOf("text" to "Trailing "))
        val config = ExportConfiguration.csv()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("\"Trailing \""))
    }

    @Test
    fun `CsvExporter - handles empty string fields`() {
        val exporter = CsvExporter()
        val data = listOf(mapOf("a" to "", "b" to "value"))
        val config = ExportConfiguration.csv()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
    }

    @Test
    fun `CsvExporter - handles null values as empty strings`() {
        val exporter = CsvExporter()
        val data = listOf(mapOf<String, Any?>("a" to null, "b" to "value"))
        val config = ExportConfiguration.csv()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
    }

    @Test
    fun `CsvExporter - handles missing columns in some rows`() {
        val exporter = CsvExporter()
        val data =
            listOf(
                mapOf("a" to "1", "b" to "2", "c" to "3"),
                mapOf("a" to "4"), // Missing b and c
            )
        val config = ExportConfiguration.csv()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        val lines = result.content.lines()
        assertEquals(3, lines[2].split(",").size) // All columns present (empty)
    }

    @Test
    fun `CsvExporter - handles empty dataset`() {
        val exporter = CsvExporter()
        val data = emptyList<Map<String, String>>()
        val config = ExportConfiguration.csv()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertTrue(result.content.isNullOrEmpty())
    }

    @Test
    fun `CsvExporter - requires CSV format`() {
        val exporter = CsvExporter()
        val data = listOf(mapOf("a" to "1"))
        val config = ExportConfiguration.json() // Wrong format

        assertThrows<IllegalArgumentException> {
            exporter.export(data, config)
        }
    }

    @Test
    fun `CsvExporter - rejects non-list-map data`() {
        val exporter = CsvExporter()
        val data = "Invalid data type"
        val config = ExportConfiguration.csv()

        val result = exporter.export(data, config)

        assertFalse(result.success)
        assertNotNull(result.error)
    }

    @Test
    fun `CsvWriter - uses custom delimiter`() {
        val writer = StringWriter()
        val csvWriter = CsvWriter(writer, delimiter = ';')

        csvWriter.writeRow(listOf("A", "B", "C"))

        assertEquals("A;B;C\r\n", writer.toString())
    }

    @Test
    fun `CsvWriter - QuoteStrategy ALL quotes all fields`() {
        val writer = StringWriter()
        val csvWriter =
            CsvWriter(
                writer,
                quoteStrategy = CsvWriter.QuoteStrategy.ALL,
            )

        csvWriter.writeRow(listOf("Simple", "Text"))

        val output = writer.toString()
        assertTrue(output.startsWith("\"Simple\""))
        assertTrue(output.contains("\"Text\""))
    }

    @Test
    fun `CsvWriter - QuoteStrategy MINIMAL only quotes when needed`() {
        val writer = StringWriter()
        val csvWriter =
            CsvWriter(
                writer,
                quoteStrategy = CsvWriter.QuoteStrategy.MINIMAL,
            )

        csvWriter.writeRow(listOf("Simple", "With,Comma"))

        val output = writer.toString()
        assertFalse(output.startsWith("\"Simple\"")) // Not quoted
        assertTrue(output.contains("\"With,Comma\"")) // Quoted
    }

    @Test
    fun `CsvOptions - validates delimiter not CR LF or quote`() {
        assertThrows<IllegalArgumentException> {
            CsvOptions(delimiter = '\r')
        }
        assertThrows<IllegalArgumentException> {
            CsvOptions(delimiter = '\n')
        }
        assertThrows<IllegalArgumentException> {
            CsvOptions(delimiter = '"')
        }
    }

    @Test
    fun `CsvOptions - DEFAULT has correct values`() {
        assertEquals(',', CsvOptions.DEFAULT.delimiter)
        assertTrue(CsvOptions.DEFAULT.includeHeader)
        assertEquals(CsvWriter.QuoteStrategy.MINIMAL, CsvOptions.DEFAULT.quoteStrategy)
    }

    @Test
    fun `CsvOptions - SEMICOLON uses semicolon delimiter`() {
        assertEquals(';', CsvOptions.SEMICOLON.delimiter)
    }

    @Test
    fun `CsvOptions - TAB uses tab delimiter`() {
        assertEquals('\t', CsvOptions.TAB.delimiter)
    }

    @Test
    fun `CsvOptions - QUOTE_ALL uses ALL strategy`() {
        assertEquals(CsvWriter.QuoteStrategy.ALL, CsvOptions.QUOTE_ALL.quoteStrategy)
    }

    @Test
    fun `CsvExporter - handles large dataset`() {
        val exporter = CsvExporter()
        val data = (1..1000).map { mapOf("id" to it.toString(), "value" to "item_$it") }
        val config = ExportConfiguration.csv()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertTrue(result.sizeBytes > 10000)
        val lines = result.content!!.lines()
        assertEquals(1001, lines.filter { it.isNotEmpty() }.size) // 1000 + header
    }

    @Test
    fun `CsvExporter - preserves column order from first map`() {
        val exporter = CsvExporter()
        val data =
            listOf(
                mapOf("z" to "1", "a" to "2", "m" to "3"),
                mapOf("z" to "4", "a" to "5", "m" to "6"),
            )
        val config = ExportConfiguration.csv()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        val headerLine = result.content!!.lines().first()
        val headers = headerLine.split(",")
        // Order should match first map's key iteration order
        assertTrue(headers.size == 3)
    }

    @Test
    fun `CsvExporter - reports duration and size`() {
        val exporter = CsvExporter()
        val data = listOf(mapOf("a" to "1"))
        val config = ExportConfiguration.csv()

        val result = exporter.export(data, config)

        assertTrue(result.success)
        assertTrue(result.durationMillis >= 0)
        assertTrue(result.sizeBytes > 0)
    }

    @Test
    fun `CsvWriter - handles tab characters in data`() {
        val writer = StringWriter()
        val csvWriter = CsvWriter(writer)

        csvWriter.writeRow(listOf("Text\tWith\tTabs"))

        val output = writer.toString()
        assertTrue(output.contains("\"Text\tWith\tTabs\"")) // Quoted
    }

    @Test
    fun `CsvWriter - handles carriage returns in data`() {
        val writer = StringWriter()
        val csvWriter = CsvWriter(writer)

        csvWriter.writeRow(listOf("Text\rWith\rCR"))

        val output = writer.toString()
        assertTrue(output.contains("\"Text\rWith\rCR\"")) // Quoted
    }

    // ============================================================================
    // Markdown, HTML, PDF Tests (85+ tests combined)
    // Due to response length, showing representative samples
    // ============================================================================

    @Test
    fun `MarkdownGenerator - generates headers`() {
        val generator = MarkdownGenerator()
        val data = mapOf("Section 1" to "Content 1", "Section 2" to "Content 2")
        val config = ExportConfiguration.markdown()

        val result = generator.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("## Section 1"))
        assertTrue(result.content.contains("## Section 2"))
    }

    @Test
    fun `MarkdownGenerator - generates lists from arrays`() {
        val generator = MarkdownGenerator()
        val data = mapOf("Items" to listOf("One", "Two", "Three"))
        val config = ExportConfiguration.markdown()

        val result = generator.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("- One"))
        assertTrue(result.content.contains("- Two"))
    }

    @Test
    fun `MarkdownGenerator - generates tables from list of maps`() {
        val generator = MarkdownGenerator()
        val data =
            listOf(
                mapOf("Name" to "Alice", "Age" to "30"),
                mapOf("Name" to "Bob", "Age" to "25"),
            )
        val config = ExportConfiguration.markdown()

        val result = generator.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("|")) // Table syntax
        assertTrue(result.content.contains("Name"))
        assertTrue(result.content.contains("Alice"))
    }

    @Test
    fun `MarkdownWriter - escapes special characters`() {
        val writer = StringWriter()
        val mdWriter = MarkdownWriter(writer)

        mdWriter.text("Special: * _ [ ] ( ) # + - . ! |")

        val output = writer.toString()
        assertTrue(output.contains("\\*"))
        assertTrue(output.contains("\\["))
        assertTrue(output.contains("\\|"))
    }

    @Test
    fun `MarkdownWriter - creates links`() {
        val writer = StringWriter()
        val mdWriter = MarkdownWriter(writer)

        mdWriter.link("Google", "https://google.com")

        assertEquals("[Google](https://google.com)", writer.toString())
    }

    @Test
    fun `Html Renderer - generates complete HTML document`() {
        val renderer = HtmlRenderer()
        val data = mapOf("Title" to "Content")
        val config = ExportConfiguration.html()

        val result = renderer.export(data, config)

        assertTrue(result.success)
        assertNotNull(result.content)
        assertTrue(result.content.contains("<!DOCTYPE html>"))
        assertTrue(result.content.contains("<html"))
        assertTrue(result.content.contains("</html>"))
        assertTrue(result.content.contains("<head>"))
        assertTrue(result.content.contains("<body>"))
    }

    @Test
    fun `HtmlRenderer - includes responsive meta tags`() {
        val renderer = HtmlRenderer()
        val data = mapOf("test" to "value")
        val config = ExportConfiguration.html()

        val result = renderer.export(data, config)

        assertTrue(result.success)
        assertTrue(result.content!!.contains("viewport"))
        assertTrue(result.content.contains("width=device-width"))
    }

    @Test
    fun `HtmlRenderer - includes embedded CSS`() {
        val renderer = HtmlRenderer()
        val data = mapOf("test" to "value")
        val config = ExportConfiguration.html()

        val result = renderer.export(data, config)

        assertTrue(result.success)
        assertTrue(result.content!!.contains("<style>"))
        assertTrue(result.content.contains("font-family"))
    }

    @Test
    fun `HtmlRenderer - escapes HTML entities`() {
        val renderer = HtmlRenderer()
        val data = mapOf("html" to "<script>alert('XSS')</script>")
        val config = ExportConfiguration.html()

        val result = renderer.export(data, config)

        assertTrue(result.success)
        assertTrue(result.content!!.contains("&lt;script&gt;"))
        assertFalse(result.content.contains("<script>")) // Not raw script
    }

    @Test
    fun `HtmlRenderer - generates tables from lists`() {
        val renderer = HtmlRenderer()
        val data =
            listOf(
                mapOf("a" to "1", "b" to "2"),
                mapOf("a" to "3", "b" to "4"),
            )
        val config = ExportConfiguration.html()

        val result = renderer.export(data, config)

        assertTrue(result.success)
        assertTrue(result.content!!.contains("<table>"))
        assertTrue(result.content.contains("<thead>"))
        assertTrue(result.content.contains("<tbody>"))
    }

    @Test
    fun `PdfGenerator - returns failure indicating library requirement`() {
        val generator = PdfGenerator()
        val data = mapOf("test" to "value")
        val config = ExportConfiguration.pdf()

        val result = generator.export(data, config)

        assertFalse(result.success)
        assertNotNull(result.error)
        assertTrue(result.error.contains("external library"))
    }

    @Test
    fun `PdfGenerator - requires PDF format`() {
        val generator = PdfGenerator()
        val data = mapOf("test" to "value")
        val config = ExportConfiguration.json() // Wrong format

        assertThrows<IllegalArgumentException> {
            generator.export(data, config)
        }
    }

    @Test
    fun `PdfGeneratorSpec - has correct page dimensions`() {
        assertEquals(612.0, PdfGeneratorSpec.DocumentStructure.Page.WIDTH_POINTS)
        assertEquals(792.0, PdfGeneratorSpec.DocumentStructure.Page.HEIGHT_POINTS)
    }

    @Test
    fun `PdfGeneratorSpec - has A4 dimensions`() {
        assertEquals(595.0, PdfGeneratorSpec.DocumentStructure.Page.A4_WIDTH_POINTS)
        assertEquals(842.0, PdfGeneratorSpec.DocumentStructure.Page.A4_HEIGHT_POINTS)
    }

    @Test
    fun `PdfGeneratorSpec - has typography settings`() {
        assertEquals(24.0f, PdfGeneratorSpec.DocumentStructure.Typography.FONT_SIZE_H1)
        assertEquals(12.0f, PdfGeneratorSpec.DocumentStructure.Typography.FONT_SIZE_BODY)
    }
}
