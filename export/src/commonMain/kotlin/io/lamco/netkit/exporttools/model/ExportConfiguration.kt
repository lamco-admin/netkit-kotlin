package io.lamco.netkit.exporttools.model

/**
 * Comprehensive configuration for export operations.
 *
 * Specifies all export parameters including format, template, filtering, formatting options,
 * validation rules, and metadata. Configurations are immutable and validated upon construction.
 *
 * ## Configuration Categories
 * - **Format & Template**: Output format and report structure
 * - **Filtering**: Data filtering and selection criteria
 * - **Formatting**: Pretty-print, compression, encoding
 * - **Validation**: Schema validation and quality checks
 * - **Metadata**: Report metadata and attribution
 * - **Processing**: Async, batching, queue priority
 *
 * ## Usage Examples
 *
 * ### Basic Configuration
 * ```kotlin
 * val config = ExportConfiguration(
 *     format = ExportFormat.JSON,
 *     template = ReportTemplate.TechnicalReport
 * )
 * ```
 *
 * ### Advanced Configuration with Filtering
 * ```kotlin
 * val config = ExportConfiguration.Builder()
 *     .format(ExportFormat.PDF)
 *     .template(ReportTemplate.SecurityAudit)
 *     .dataFilter(myFilter)
 *     .prettify(true)
 *     .compress(true)
 *     .validate(true)
 *     .build()
 * ```
 *
 * @property format Output format for export
 * @property template Report template defining structure and content
 * @property dataFilter Optional filter to select/exclude data (null = no filtering)
 * @property prettify Format output for human readability (true) or compact (false)
 * @property compress Apply compression (gzip) for formats that recommend it
 * @property validate Perform schema/structure validation before export
 * @property encoding Character encoding for text formats (default: UTF-8)
 * @property includeTimestamp Include generation timestamp in report
 * @property includeMetadata Include metadata section in report
 * @property includeRawData Include raw unprocessed data
 * @property title Custom report title (null = auto-generated)
 * @property author Report author/generator attribution
 * @property priority Export queue priority (for background processing)
 * @property timeoutMillis Maximum time allowed for export operation (null = no timeout)
 * @property maxFileSizeBytes Maximum allowed output file size (null = unlimited)
 * @property customMetadata Custom key-value metadata to include
 *
 * @since 1.0.0
 */
data class ExportConfiguration(
    val format: ExportFormat,
    val template: ReportTemplate,
    val dataFilter: DataFilter? = null,
    val prettify: Boolean = true,
    val compress: Boolean = false,
    val validate: Boolean = true,
    val encoding: String = "UTF-8",
    val includeTimestamp: Boolean = true,
    val includeMetadata: Boolean = true,
    val includeRawData: Boolean = false,
    val title: String? = null,
    val author: String = "WiFi Intelligence",
    val priority: Priority = Priority.NORMAL,
    val timeoutMillis: Long? = null,
    val maxFileSizeBytes: Long? = null,
    val customMetadata: Map<String, String> = emptyMap(),
) {
    init {
        // Validate encoding
        require(encoding.isNotBlank()) { "Encoding cannot be blank" }
        require(encoding in supportedEncodings) {
            "Unsupported encoding: $encoding. Supported: ${supportedEncodings.joinToString()}"
        }

        // Validate timeout
        timeoutMillis?.let {
            require(it > 0) { "Timeout must be positive, got: $it ms" }
        }

        // Validate max file size
        maxFileSizeBytes?.let {
            require(it > 0) { "Max file size must be positive, got: $it bytes" }
        }

        // Validate title length
        title?.let {
            require(it.isNotBlank()) { "Title cannot be blank if provided" }
            require(it.length <= MAX_TITLE_LENGTH) {
                "Title too long (max $MAX_TITLE_LENGTH chars): ${it.length}"
            }
        }

        // Validate author
        require(author.isNotBlank()) { "Author cannot be blank" }

        // Validate template
        require(template.validate()) { "Template configuration is invalid" }

        // Warn about format-specific settings
        if (prettify && format.binaryFormat) {
            // Prettify is ignored for binary formats
        }

        if (compress && !format.compressionRecommended && format != ExportFormat.CSV) {
            // Compression not typically used for this format
        }

        if (validate && !format.supportsValidation) {
            // Validation not available for this format
        }
    }

    /**
     * Export queue priority levels.
     *
     * Priority determines processing order when multiple exports are queued.
     * Higher priority exports are processed before lower priority ones.
     */
    enum class Priority(
        val value: Int,
    ) {
        /** Lowest priority - background exports, batch processing */
        LOW(1),

        /** Normal priority - user-initiated exports */
        NORMAL(5),

        /** High priority - urgent exports, user waiting */
        HIGH(10),

        /** Critical priority - system exports, compliance reports */
        CRITICAL(20),
    }

    /**
     * Returns effective prettify setting based on format.
     *
     * Binary formats ignore prettify setting.
     */
    val effectivePrettify: Boolean
        get() = prettify && !format.binaryFormat

    /**
     * Returns effective compress setting based on format recommendation.
     *
     * Compression is applied if:
     * - User requested compression, OR
     * - Format recommends compression and user didn't explicitly disable it
     */
    val effectiveCompress: Boolean
        get() = compress || (format.compressionRecommended && !userDisabledCompression)

    /**
     * Returns effective validate setting based on format capability.
     *
     * Validation is only performed if format supports it.
     */
    val effectiveValidate: Boolean
        get() = validate && format.supportsValidation

    /**
     * Whether user explicitly disabled compression.
     *
     * This is a heuristic: if compress=false and format recommends compression,
     * assume user explicitly disabled it.
     */
    private val userDisabledCompression: Boolean
        get() = !compress && format.compressionRecommended

    /**
     * Returns the complete filename with extension and compression suffix.
     *
     * @param baseName Base filename (e.g., "wifi-report")
     * @return Full filename (e.g., "wifi-report.json.gz")
     */
    fun toFilename(baseName: String): String {
        val baseFilename = format.toFilename(baseName)
        return if (effectiveCompress) {
            "$baseFilename.gz"
        } else {
            baseFilename
        }
    }

    /**
     * Returns Content-Type header for HTTP responses.
     *
     * @return Content-Type with charset for text formats
     */
    fun contentType(): String = format.contentType(encoding)

    /**
     * Returns Content-Encoding header if compression is enabled.
     *
     * @return "gzip" if compressed, null otherwise
     */
    fun contentEncoding(): String? = if (effectiveCompress) "gzip" else null

    /**
     * Returns estimated export duration based on template size and format.
     *
     * This is a rough estimate and actual time may vary based on data volume.
     *
     * @return Estimated duration category
     */
    fun estimatedDuration(): DurationCategory {
        val baseEstimate =
            when (template.estimatedSize) {
                ReportTemplate.SizeCategory.SMALL -> DurationCategory.FAST
                ReportTemplate.SizeCategory.MEDIUM -> DurationCategory.MODERATE
                ReportTemplate.SizeCategory.LARGE -> DurationCategory.SLOW
                ReportTemplate.SizeCategory.VERY_LARGE -> DurationCategory.VERY_SLOW
            }

        // PDF generation is slower than other formats
        val formatMultiplier = if (format == ExportFormat.PDF) 1 else 0

        return when (baseEstimate.ordinal + formatMultiplier) {
            0 -> DurationCategory.FAST
            1 -> DurationCategory.MODERATE
            2 -> DurationCategory.SLOW
            else -> DurationCategory.VERY_SLOW
        }
    }

    /**
     * Export duration categories.
     */
    enum class DurationCategory {
        /** Fast export (<1 second) */
        FAST,

        /** Moderate duration (1-5 seconds) */
        MODERATE,

        /** Slow export (5-30 seconds) */
        SLOW,

        /** Very slow export (>30 seconds) */
        VERY_SLOW,
    }

    /**
     * Returns whether this configuration is suitable for background processing.
     *
     * Background processing is suitable if:
     * - Priority is not HIGH or CRITICAL
     * - Estimated duration is SLOW or VERY_SLOW
     * - No immediate timeout requirement
     */
    val suitableForBackground: Boolean
        get() {
            val lowPriority = priority in listOf(Priority.LOW, Priority.NORMAL)
            val slowOperation =
                estimatedDuration() in
                    listOf(
                        DurationCategory.SLOW,
                        DurationCategory.VERY_SLOW,
                    )
            val noUrgentTimeout = timeoutMillis == null || timeoutMillis > 10_000
            return lowPriority && slowOperation && noUrgentTimeout
        }

    /**
     * Builder for ExportConfiguration with fluent API.
     */
    class Builder {
        private var format: ExportFormat = ExportFormat.JSON
        private var template: ReportTemplate = ReportTemplate.TechnicalReport
        private var dataFilter: DataFilter? = null
        private var prettify: Boolean = true
        private var compress: Boolean = false
        private var validate: Boolean = true
        private var encoding: String = "UTF-8"
        private var includeTimestamp: Boolean = true
        private var includeMetadata: Boolean = true
        private var includeRawData: Boolean = false
        private var title: String? = null
        private var author: String = "WiFi Intelligence"
        private var priority: Priority = Priority.NORMAL
        private var timeoutMillis: Long? = null
        private var maxFileSizeBytes: Long? = null
        private val customMetadata: MutableMap<String, String> = mutableMapOf()

        fun format(format: ExportFormat) = apply { this.format = format }

        fun template(template: ReportTemplate) = apply { this.template = template }

        fun dataFilter(filter: DataFilter?) = apply { this.dataFilter = filter }

        fun prettify(prettify: Boolean) = apply { this.prettify = prettify }

        fun compress(compress: Boolean) = apply { this.compress = compress }

        fun validate(validate: Boolean) = apply { this.validate = validate }

        fun encoding(encoding: String) = apply { this.encoding = encoding }

        fun includeTimestamp(include: Boolean) = apply { this.includeTimestamp = include }

        fun includeMetadata(include: Boolean) = apply { this.includeMetadata = include }

        fun includeRawData(include: Boolean) = apply { this.includeRawData = include }

        fun title(title: String?) = apply { this.title = title }

        fun author(author: String) = apply { this.author = author }

        fun priority(priority: Priority) = apply { this.priority = priority }

        fun timeoutMillis(timeout: Long?) = apply { this.timeoutMillis = timeout }

        fun maxFileSizeBytes(maxSize: Long?) = apply { this.maxFileSizeBytes = maxSize }

        fun addMetadata(
            key: String,
            value: String,
        ) = apply {
            customMetadata[key] = value
        }

        fun metadata(metadata: Map<String, String>) =
            apply {
                customMetadata.clear()
                customMetadata.putAll(metadata)
            }

        fun build() =
            ExportConfiguration(
                format = format,
                template = template,
                dataFilter = dataFilter,
                prettify = prettify,
                compress = compress,
                validate = validate,
                encoding = encoding,
                includeTimestamp = includeTimestamp,
                includeMetadata = includeMetadata,
                includeRawData = includeRawData,
                title = title,
                author = author,
                priority = priority,
                timeoutMillis = timeoutMillis,
                maxFileSizeBytes = maxFileSizeBytes,
                customMetadata = customMetadata.toMap(),
            )
    }

    companion object {
        /**
         * Maximum allowed title length.
         */
        const val MAX_TITLE_LENGTH = 200

        /**
         * Default timeout for export operations (5 minutes).
         */
        const val DEFAULT_TIMEOUT_MILLIS = 5 * 60 * 1000L

        /**
         * Default maximum file size (100 MB).
         */
        const val DEFAULT_MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024

        /**
         * Supported character encodings.
         */
        val supportedEncodings =
            setOf(
                "UTF-8",
                "UTF-16",
                "UTF-16BE",
                "UTF-16LE",
                "ISO-8859-1",
                "US-ASCII",
            )

        /**
         * Default configuration for JSON export.
         */
        fun json(template: ReportTemplate = ReportTemplate.TechnicalReport) =
            ExportConfiguration(
                format = ExportFormat.JSON,
                template = template,
                prettify = true,
                compress = false,
                validate = true,
            )

        /**
         * Default configuration for CSV export.
         */
        fun csv(template: ReportTemplate = ReportTemplate.TechnicalReport) =
            ExportConfiguration(
                format = ExportFormat.CSV,
                template = template,
                prettify = false, // CSV doesn't have pretty-print
                compress = true, // CSV benefits from compression
                validate = false, // CSV doesn't have schema validation
            )

        /**
         * Default configuration for PDF export.
         */
        fun pdf(template: ReportTemplate = ReportTemplate.TechnicalReport) =
            ExportConfiguration(
                format = ExportFormat.PDF,
                template = template,
                prettify = false, // Binary format
                compress = false, // PDF has internal compression
                validate = false,
            )

        /**
         * Default configuration for HTML export.
         */
        fun html(template: ReportTemplate = ReportTemplate.TechnicalReport) =
            ExportConfiguration(
                format = ExportFormat.HTML,
                template = template,
                prettify = true,
                compress = true, // HTML benefits from compression
                validate = true,
            )

        /**
         * Default configuration for Markdown export.
         */
        fun markdown(template: ReportTemplate = ReportTemplate.TechnicalReport) =
            ExportConfiguration(
                format = ExportFormat.MARKDOWN,
                template = template,
                prettify = true,
                compress = false, // Markdown files are typically small
                validate = false,
            )

        /**
         * Quick export configuration (fast, minimal detail).
         */
        fun quick() =
            ExportConfiguration(
                format = ExportFormat.JSON,
                template = ReportTemplate.QuickScan,
                prettify = false,
                compress = false,
                validate = false,
                includeRawData = false,
                priority = Priority.HIGH,
            )

        /**
         * Compliance export configuration (comprehensive, validated).
         */
        fun compliance() =
            ExportConfiguration(
                format = ExportFormat.PDF,
                template = ReportTemplate.ComplianceReport,
                prettify = false,
                compress = false,
                validate = true,
                includeMetadata = true,
                includeRawData = true,
                priority = Priority.CRITICAL,
                author = "WiFi Intelligence Compliance Module",
            )

        /**
         * Archive export configuration (comprehensive, compressed).
         */
        fun archive() =
            ExportConfiguration(
                format = ExportFormat.JSON,
                template = ReportTemplate.TechnicalReport,
                prettify = false, // Compact for archival
                compress = true,
                validate = true,
                includeRawData = true,
                includeMetadata = true,
                priority = Priority.LOW,
            )
    }
}
