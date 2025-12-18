package io.lamco.netkit.exporttools.model

/**
 * Supported export formats for WiFi Intelligence reports and data.
 *
 * Each format has specific capabilities, MIME types, and use cases.
 * Formats range from structured data (JSON, CSV) to presentation formats (PDF, HTML, Markdown).
 *
 * ## Format Categories
 * - **Data Formats**: JSON, CSV (machine-readable, structured data)
 * - **Document Formats**: PDF (print-ready, archival)
 * - **Web Formats**: HTML (browser display, interactive)
 * - **Markup Formats**: MARKDOWN (documentation, version control)
 *
 * ## Format Selection Guide
 * - **JSON**: API integration, data interchange, schema validation
 * - **CSV**: Spreadsheet import, database loading, simple analysis
 * - **PDF**: Reports, presentations, archival, printing
 * - **HTML**: Web display, dashboards, interactive reports
 * - **MARKDOWN**: Documentation, GitHub, version control
 *
 * @property displayName Human-readable format name
 * @property fileExtension Standard file extension (without leading dot)
 * @property mimeType MIME type for HTTP responses and file metadata
 * @property supportsCharts Whether format can embed charts and visualizations
 * @property supportsTables Whether format supports tabular data rendering
 * @property supportsSections Whether format supports hierarchical sections
 * @property supportsFormatting Whether format supports text formatting (bold, italic, etc.)
 * @property supportsPagination Whether format supports page breaks and pagination
 * @property supportsInteractivity Whether format supports interactive elements
 * @property supportsValidation Whether format supports schema/structure validation
 * @property binaryFormat Whether format is binary (true) or text (false)
 * @property compressionRecommended Whether compression is recommended for this format
 *
 * @since 1.0.0
 */
enum class ExportFormat(
    val displayName: String,
    val fileExtension: String,
    val mimeType: String,
    val supportsCharts: Boolean,
    val supportsTables: Boolean,
    val supportsSections: Boolean,
    val supportsFormatting: Boolean,
    val supportsPagination: Boolean,
    val supportsInteractivity: Boolean,
    val supportsValidation: Boolean,
    val binaryFormat: Boolean,
    val compressionRecommended: Boolean,
) {
    /**
     * JSON (JavaScript Object Notation) format.
     *
     * **Best for**:
     * - API responses and data interchange
     * - Machine-to-machine communication
     * - Schema-validated data export
     * - Complex nested data structures
     *
     * **Features**:
     * - Hierarchical data structure
     * - Schema validation (JSON Schema)
     * - Wide language support
     * - Human-readable when formatted
     *
     * **Limitations**:
     * - No built-in chart support
     * - No visual formatting
     * - Requires parsing for display
     *
     * **Standards**: RFC 8259, ECMA-404, JSON Schema
     */
    JSON(
        displayName = "JSON",
        fileExtension = "json",
        mimeType = "application/json",
        supportsCharts = false,
        supportsTables = true, // As nested arrays
        supportsSections = true, // As nested objects
        supportsFormatting = false,
        supportsPagination = false,
        supportsInteractivity = false,
        supportsValidation = true, // JSON Schema
        binaryFormat = false,
        compressionRecommended = true, // gzip for large files
    ),

    /**
     * CSV (Comma-Separated Values) format.
     *
     * **Best for**:
     * - Spreadsheet import (Excel, Google Sheets)
     * - Database bulk loading
     * - Simple tabular data
     * - Large dataset export
     *
     * **Features**:
     * - Universal spreadsheet support
     * - Simple, compact format
     * - Easy parsing
     * - Stream processing friendly
     *
     * **Limitations**:
     * - Flat structure only (no nesting)
     * - No formatting or styling
     * - Escaping complexity (commas, quotes, newlines)
     * - No charts or visualizations
     *
     * **Standards**: RFC 4180
     */
    CSV(
        displayName = "CSV",
        fileExtension = "csv",
        mimeType = "text/csv",
        supportsCharts = false,
        supportsTables = true,
        supportsSections = false, // Flat structure
        supportsFormatting = false,
        supportsPagination = false,
        supportsInteractivity = false,
        supportsValidation = false,
        binaryFormat = false,
        compressionRecommended = true, // For large datasets
    ),

    /**
     * PDF (Portable Document Format).
     *
     * **Best for**:
     * - Professional reports and presentations
     * - Archival and long-term storage
     * - Print-ready documents
     * - Multi-page formatted reports
     *
     * **Features**:
     * - Embedded charts and images
     * - Rich formatting and layouts
     * - Table of contents and bookmarks
     * - Page numbering and headers/footers
     * - Cross-platform consistency
     *
     * **Limitations**:
     * - Binary format (not easily edited)
     * - Larger file sizes
     * - Requires PDF library for generation
     * - No interactivity (in basic PDF)
     *
     * **Standards**: ISO 32000-1 (PDF 1.7), PDF/A (archival)
     */
    PDF(
        displayName = "PDF",
        fileExtension = "pdf",
        mimeType = "application/pdf",
        supportsCharts = true,
        supportsTables = true,
        supportsSections = true,
        supportsFormatting = true,
        supportsPagination = true,
        supportsInteractivity = false, // Basic PDF (PDF forms require extra)
        supportsValidation = false,
        binaryFormat = true,
        compressionRecommended = false, // Already compressed internally
    ),

    /**
     * HTML (HyperText Markup Language) format.
     *
     * **Best for**:
     * - Web display and dashboards
     * - Interactive reports
     * - Embedded visualizations
     * - Responsive layouts
     *
     * **Features**:
     * - Rich formatting and styling (CSS)
     * - Embedded charts (SVG, Canvas)
     * - Interactive elements (JavaScript)
     * - Responsive design
     * - Hyperlinks and navigation
     *
     * **Limitations**:
     * - Requires browser or HTML renderer
     * - Multi-file (HTML + CSS + JS + images) unless embedded
     * - Print rendering varies by browser
     *
     * **Standards**: HTML5 (WHATWG), CSS3, W3C standards
     */
    HTML(
        displayName = "HTML",
        fileExtension = "html",
        mimeType = "text/html",
        supportsCharts = true, // SVG, Canvas
        supportsTables = true,
        supportsSections = true,
        supportsFormatting = true,
        supportsPagination = false, // Browser-dependent
        supportsInteractivity = true, // JavaScript
        supportsValidation = true, // HTML5 validation
        binaryFormat = false,
        compressionRecommended = true, // gzip for web delivery
    ),

    /**
     * Markdown format (GitHub-flavored).
     *
     * **Best for**:
     * - Documentation and README files
     * - Version control friendly (Git)
     * - GitHub, GitLab, Bitbucket display
     * - Lightweight formatted text
     *
     * **Features**:
     * - Simple, readable syntax
     * - Tables support (GFM)
     * - Code blocks and syntax highlighting
     * - Hyperlinks and images
     * - Converts to HTML easily
     * - Version control friendly (diff-friendly)
     *
     * **Limitations**:
     * - Limited formatting options
     * - No native charts (image embedding only)
     * - No precise layout control
     * - Rendering varies by viewer
     *
     * **Standards**: CommonMark, GitHub-Flavored Markdown (GFM)
     */
    MARKDOWN(
        displayName = "Markdown",
        fileExtension = "md",
        mimeType = "text/markdown",
        supportsCharts = false, // Images only
        supportsTables = true, // GFM tables
        supportsSections = true, // Headers
        supportsFormatting = true, // Bold, italic, code
        supportsPagination = false,
        supportsInteractivity = false,
        supportsValidation = false,
        binaryFormat = false,
        compressionRecommended = false, // Small files typically
    ),
    ;

    /**
     * Returns the full filename with extension.
     *
     * @param baseName Base filename without extension (e.g., "wifi-report")
     * @return Full filename with extension (e.g., "wifi-report.json")
     */
    fun toFilename(baseName: String): String {
        require(baseName.isNotBlank()) { "Base filename cannot be blank" }
        require(!baseName.contains('/') && !baseName.contains('\\')) {
            "Base filename cannot contain path separators"
        }
        return "$baseName.$fileExtension"
    }

    /**
     * Checks if this format is suitable for the given use case.
     *
     * @param requiresCharts True if charts/visualizations are required
     * @param requiresFormatting True if text formatting is required
     * @param requiresInteractivity True if interactive elements are required
     * @param requiresValidation True if schema/structure validation is required
     * @return True if format meets all requirements
     */
    fun isSuitableFor(
        requiresCharts: Boolean = false,
        requiresFormatting: Boolean = false,
        requiresInteractivity: Boolean = false,
        requiresValidation: Boolean = false,
    ): Boolean {
        if (requiresCharts && !supportsCharts) return false
        if (requiresFormatting && !supportsFormatting) return false
        if (requiresInteractivity && !supportsInteractivity) return false
        if (requiresValidation && !supportsValidation) return false
        return true
    }

    /**
     * Returns formats suitable for data export (structured, machine-readable).
     */
    val isDataFormat: Boolean
        get() = this in listOf(JSON, CSV)

    /**
     * Returns formats suitable for document export (presentation, reports).
     */
    val isDocumentFormat: Boolean
        get() = this in listOf(PDF, HTML, MARKDOWN)

    /**
     * Returns whether this is a web-friendly format.
     */
    val isWebFormat: Boolean
        get() = this in listOf(HTML, JSON)

    /**
     * Returns the recommended character encoding for this format.
     */
    val recommendedEncoding: String
        get() = if (binaryFormat) "binary" else "UTF-8"

    /**
     * Returns the Content-Type header value for HTTP responses.
     *
     * @param charset Character encoding (default: UTF-8 for text formats)
     * @return Content-Type header value
     */
    fun contentType(charset: String = "UTF-8"): String =
        if (binaryFormat) {
            mimeType
        } else {
            "$mimeType; charset=$charset"
        }

    companion object {
        /**
         * All data formats (JSON, CSV).
         */
        val dataFormats: List<ExportFormat> = listOf(JSON, CSV)

        /**
         * All document formats (PDF, HTML, Markdown).
         */
        val documentFormats: List<ExportFormat> = listOf(PDF, HTML, MARKDOWN)

        /**
         * All web formats (HTML, JSON).
         */
        val webFormats: List<ExportFormat> = listOf(HTML, JSON)

        /**
         * All text-based formats (non-binary).
         */
        val textFormats: List<ExportFormat> = entries.filter { !it.binaryFormat }

        /**
         * All binary formats.
         */
        val binaryFormats: List<ExportFormat> = entries.filter { it.binaryFormat }

        /**
         * Formats that support charts and visualizations.
         */
        val chartsCapable: List<ExportFormat> = entries.filter { it.supportsCharts }

        /**
         * Formats that support interactive elements.
         */
        val interactiveCapable: List<ExportFormat> = entries.filter { it.supportsInteractivity }

        /**
         * Formats that support schema/structure validation.
         */
        val validationCapable: List<ExportFormat> = entries.filter { it.supportsValidation }

        /**
         * Finds format by file extension (case-insensitive).
         *
         * @param extension File extension with or without leading dot
         * @return Matching format or null if not found
         */
        fun fromExtension(extension: String): ExportFormat? {
            val ext = extension.removePrefix(".").lowercase()
            return entries.find { it.fileExtension.lowercase() == ext }
        }

        /**
         * Finds format by MIME type.
         *
         * @param mimeType MIME type string
         * @return Matching format or null if not found
         */
        fun fromMimeType(mimeType: String): ExportFormat? {
            val cleanMimeType =
                mimeType
                    .split(';')
                    .first()
                    .trim()
                    .lowercase()
            return entries.find { it.mimeType.lowercase() == cleanMimeType }
        }

        /**
         * Returns formats suitable for the given requirements.
         *
         * @param requiresCharts True if charts are required
         * @param requiresFormatting True if formatting is required
         * @param requiresInteractivity True if interactivity is required
         * @param requiresValidation True if validation is required
         * @return List of suitable formats
         */
        fun findSuitable(
            requiresCharts: Boolean = false,
            requiresFormatting: Boolean = false,
            requiresInteractivity: Boolean = false,
            requiresValidation: Boolean = false,
        ): List<ExportFormat> =
            entries.filter {
                it.isSuitableFor(
                    requiresCharts = requiresCharts,
                    requiresFormatting = requiresFormatting,
                    requiresInteractivity = requiresInteractivity,
                    requiresValidation = requiresValidation,
                )
            }
    }
}
