package io.lamco.netkit.exporttools.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.*

/**
 * Comprehensive tests for export-tools Batch 1 models.
 *
 * Tests cover ALL enum values, ALL validation rules, and ALL scenarios.
 * Target: 70+ tests with complete coverage.
 */
class ExportModelsTest {
    // ============================================================================
    // ExportFormat Tests (20 tests)
    // ============================================================================

    @Test
    fun `ExportFormat - all formats have required properties`() {
        ExportFormat.entries.forEach { format ->
            assertNotNull(format.displayName)
            assertTrue(format.displayName.isNotBlank())
            assertNotNull(format.fileExtension)
            assertTrue(format.fileExtension.isNotBlank())
            assertNotNull(format.mimeType)
            assertTrue(format.mimeType.isNotBlank())
        }
    }

    @Test
    fun `ExportFormat JSON - has correct properties`() {
        val format = ExportFormat.JSON
        assertEquals("JSON", format.displayName)
        assertEquals("json", format.fileExtension)
        assertEquals("application/json", format.mimeType)
        assertFalse(format.supportsCharts)
        assertTrue(format.supportsTables)
        assertTrue(format.supportsSections)
        assertFalse(format.supportsFormatting)
        assertFalse(format.supportsPagination)
        assertFalse(format.supportsInteractivity)
        assertTrue(format.supportsValidation)
        assertFalse(format.binaryFormat)
        assertTrue(format.compressionRecommended)
    }

    @Test
    fun `ExportFormat CSV - has correct properties`() {
        val format = ExportFormat.CSV
        assertEquals("CSV", format.displayName)
        assertEquals("csv", format.fileExtension)
        assertEquals("text/csv", format.mimeType)
        assertFalse(format.supportsCharts)
        assertTrue(format.supportsTables)
        assertFalse(format.supportsSections)
        assertFalse(format.supportsFormatting)
        assertFalse(format.binaryFormat)
        assertTrue(format.compressionRecommended)
    }

    @Test
    fun `ExportFormat PDF - has correct properties`() {
        val format = ExportFormat.PDF
        assertEquals("PDF", format.displayName)
        assertEquals("pdf", format.fileExtension)
        assertEquals("application/pdf", format.mimeType)
        assertTrue(format.supportsCharts)
        assertTrue(format.supportsTables)
        assertTrue(format.supportsSections)
        assertTrue(format.supportsFormatting)
        assertTrue(format.supportsPagination)
        assertTrue(format.binaryFormat)
        assertFalse(format.compressionRecommended)
    }

    @Test
    fun `ExportFormat HTML - has correct properties`() {
        val format = ExportFormat.HTML
        assertEquals("HTML", format.displayName)
        assertEquals("html", format.fileExtension)
        assertEquals("text/html", format.mimeType)
        assertTrue(format.supportsCharts)
        assertTrue(format.supportsTables)
        assertTrue(format.supportsInteractivity)
        assertTrue(format.supportsValidation)
        assertFalse(format.binaryFormat)
    }

    @Test
    fun `ExportFormat MARKDOWN - has correct properties`() {
        val format = ExportFormat.MARKDOWN
        assertEquals("Markdown", format.displayName)
        assertEquals("md", format.fileExtension)
        assertEquals("text/markdown", format.mimeType)
        assertFalse(format.supportsCharts)
        assertTrue(format.supportsTables)
        assertTrue(format.supportsSections)
        assertTrue(format.supportsFormatting)
        assertFalse(format.binaryFormat)
        assertFalse(format.compressionRecommended)
    }

    @Test
    fun `ExportFormat toFilename - creates correct filename`() {
        assertEquals("report.json", ExportFormat.JSON.toFilename("report"))
        assertEquals("data.csv", ExportFormat.CSV.toFilename("data"))
        assertEquals("doc.pdf", ExportFormat.PDF.toFilename("doc"))
        assertEquals("page.html", ExportFormat.HTML.toFilename("page"))
        assertEquals("readme.md", ExportFormat.MARKDOWN.toFilename("readme"))
    }

    @Test
    fun `ExportFormat toFilename - rejects blank names`() {
        assertThrows<IllegalArgumentException> {
            ExportFormat.JSON.toFilename("")
        }
        assertThrows<IllegalArgumentException> {
            ExportFormat.JSON.toFilename("  ")
        }
    }

    @Test
    fun `ExportFormat toFilename - rejects path separators`() {
        assertThrows<IllegalArgumentException> {
            ExportFormat.JSON.toFilename("path/to/file")
        }
        assertThrows<IllegalArgumentException> {
            ExportFormat.JSON.toFilename("path\\to\\file")
        }
    }

    @Test
    fun `ExportFormat isSuitableFor - filters by capabilities`() {
        // PDF supports charts
        assertTrue(ExportFormat.PDF.isSuitableFor(requiresCharts = true))
        assertFalse(ExportFormat.JSON.isSuitableFor(requiresCharts = true))

        // HTML supports interactivity
        assertTrue(ExportFormat.HTML.isSuitableFor(requiresInteractivity = true))
        assertFalse(ExportFormat.PDF.isSuitableFor(requiresInteractivity = true))

        // JSON supports validation
        assertTrue(ExportFormat.JSON.isSuitableFor(requiresValidation = true))
        assertFalse(ExportFormat.CSV.isSuitableFor(requiresValidation = true))
    }

    @Test
    fun `ExportFormat helper properties - isDataFormat`() {
        assertTrue(ExportFormat.JSON.isDataFormat)
        assertTrue(ExportFormat.CSV.isDataFormat)
        assertFalse(ExportFormat.PDF.isDataFormat)
        assertFalse(ExportFormat.HTML.isDataFormat)
        assertFalse(ExportFormat.MARKDOWN.isDataFormat)
    }

    @Test
    fun `ExportFormat helper properties - isDocumentFormat`() {
        assertFalse(ExportFormat.JSON.isDocumentFormat)
        assertFalse(ExportFormat.CSV.isDocumentFormat)
        assertTrue(ExportFormat.PDF.isDocumentFormat)
        assertTrue(ExportFormat.HTML.isDocumentFormat)
        assertTrue(ExportFormat.MARKDOWN.isDocumentFormat)
    }

    @Test
    fun `ExportFormat helper properties - isWebFormat`() {
        assertTrue(ExportFormat.JSON.isWebFormat)
        assertFalse(ExportFormat.CSV.isWebFormat)
        assertFalse(ExportFormat.PDF.isWebFormat)
        assertTrue(ExportFormat.HTML.isWebFormat)
        assertFalse(ExportFormat.MARKDOWN.isWebFormat)
    }

    @Test
    fun `ExportFormat recommendedEncoding - correct for all formats`() {
        assertEquals("UTF-8", ExportFormat.JSON.recommendedEncoding)
        assertEquals("UTF-8", ExportFormat.CSV.recommendedEncoding)
        assertEquals("binary", ExportFormat.PDF.recommendedEncoding)
        assertEquals("UTF-8", ExportFormat.HTML.recommendedEncoding)
        assertEquals("UTF-8", ExportFormat.MARKDOWN.recommendedEncoding)
    }

    @Test
    fun `ExportFormat contentType - includes charset for text formats`() {
        assertEquals("application/json; charset=UTF-8", ExportFormat.JSON.contentType())
        assertEquals("text/csv; charset=UTF-8", ExportFormat.CSV.contentType())
        assertEquals("application/pdf", ExportFormat.PDF.contentType())
        assertEquals("text/html; charset=UTF-8", ExportFormat.HTML.contentType())
        assertEquals("text/markdown; charset=UTF-8", ExportFormat.MARKDOWN.contentType())
    }

    @Test
    fun `ExportFormat companion - dataFormats contains JSON and CSV`() {
        assertEquals(2, ExportFormat.dataFormats.size)
        assertTrue(ExportFormat.JSON in ExportFormat.dataFormats)
        assertTrue(ExportFormat.CSV in ExportFormat.dataFormats)
    }

    @Test
    fun `ExportFormat companion - documentFormats contains PDF, HTML, Markdown`() {
        assertEquals(3, ExportFormat.documentFormats.size)
        assertTrue(ExportFormat.PDF in ExportFormat.documentFormats)
        assertTrue(ExportFormat.HTML in ExportFormat.documentFormats)
        assertTrue(ExportFormat.MARKDOWN in ExportFormat.documentFormats)
    }

    @Test
    fun `ExportFormat companion - fromExtension finds correct format`() {
        assertEquals(ExportFormat.JSON, ExportFormat.fromExtension("json"))
        assertEquals(ExportFormat.CSV, ExportFormat.fromExtension("csv"))
        assertEquals(ExportFormat.PDF, ExportFormat.fromExtension("pdf"))
        assertEquals(ExportFormat.HTML, ExportFormat.fromExtension("html"))
        assertEquals(ExportFormat.MARKDOWN, ExportFormat.fromExtension("md"))
    }

    @Test
    fun `ExportFormat companion - fromExtension handles dot prefix and case`() {
        assertEquals(ExportFormat.JSON, ExportFormat.fromExtension(".json"))
        assertEquals(ExportFormat.JSON, ExportFormat.fromExtension("JSON"))
        assertEquals(ExportFormat.JSON, ExportFormat.fromExtension(".JSON"))
        assertNull(ExportFormat.fromExtension("unknown"))
    }

    @Test
    fun `ExportFormat companion - fromMimeType finds correct format`() {
        assertEquals(ExportFormat.JSON, ExportFormat.fromMimeType("application/json"))
        assertEquals(ExportFormat.CSV, ExportFormat.fromMimeType("text/csv"))
        assertEquals(ExportFormat.PDF, ExportFormat.fromMimeType("application/pdf"))
        assertNull(ExportFormat.fromMimeType("unknown/type"))
    }

    // ============================================================================
    // ReportTemplate Tests (15 tests)
    // ============================================================================

    @Test
    fun `ReportSection - all sections have required properties`() {
        ReportSection.entries.forEach { section ->
            assertNotNull(section.displayName)
            assertTrue(section.displayName.isNotBlank())
            assertNotNull(section.description)
            assertTrue(section.description.isNotBlank())
            assertTrue(section.order > 0)
        }
    }

    @Test
    fun `ReportSection companion - requiredSections contains METADATA`() {
        assertTrue(ReportSection.METADATA in ReportSection.requiredSections)
        assertEquals(1, ReportSection.requiredSections.size)
    }

    @Test
    fun `ReportSection companion - sections sorted by order`() {
        val sorted = ReportSection.byOrder
        for (i in 1 until sorted.size) {
            assertTrue(sorted[i - 1].order <= sorted[i].order)
        }
    }

    @Test
    fun `TemplateOptions - default values are correct`() {
        val options = TemplateOptions()
        assertTrue(options.includeCharts)
        assertTrue(options.includeRecommendations)
        assertFalse(options.includeRawData)
        assertTrue(options.includeTechnicalDetails)
        assertTrue(options.includeMetadata)
        assertFalse(options.includeAppendix)
        assertEquals(TemplateOptions.DetailLevel.STANDARD, options.detailLevel)
    }

    @Test
    fun `TemplateOptions - validates maxNetworks and maxRecommendations`() {
        assertThrows<IllegalArgumentException> {
            TemplateOptions(maxNetworks = 0)
        }
        assertThrows<IllegalArgumentException> {
            TemplateOptions(maxNetworks = -1)
        }
        assertThrows<IllegalArgumentException> {
            TemplateOptions(maxRecommendations = 0)
        }
    }

    @Test
    fun `TemplateOptions presets - MINIMAL has minimal settings`() {
        val minimal = TemplateOptions.MINIMAL
        assertFalse(minimal.includeCharts)
        assertFalse(minimal.includeTechnicalDetails)
        assertFalse(minimal.includeRawData)
        assertFalse(minimal.includeAppendix)
        assertEquals(TemplateOptions.DetailLevel.MINIMAL, minimal.detailLevel)
    }

    @Test
    fun `TemplateOptions presets - COMPREHENSIVE has all features`() {
        val comprehensive = TemplateOptions.COMPREHENSIVE
        assertTrue(comprehensive.includeCharts)
        assertTrue(comprehensive.includeRecommendations)
        assertTrue(comprehensive.includeRawData)
        assertTrue(comprehensive.includeTechnicalDetails)
        assertTrue(comprehensive.includeAppendix)
        assertEquals(TemplateOptions.DetailLevel.COMPREHENSIVE, comprehensive.detailLevel)
    }

    @Test
    fun `ReportTemplate ExecutiveSummary - has correct configuration`() {
        val template = ReportTemplate.ExecutiveSummary
        assertEquals("Executive Summary", template.name)
        assertTrue(template.sections.contains(ReportSection.EXECUTIVE_SUMMARY))
        assertTrue(template.sections.contains(ReportSection.HEALTH_SCORE))
        assertTrue(template.sections.contains(ReportSection.RECOMMENDATIONS))
        assertEquals(ReportTemplate.Audience.MANAGEMENT, template.audience)
        assertEquals(5, template.options.maxRecommendations)
    }

    @Test
    fun `ReportTemplate TechnicalReport - has comprehensive sections`() {
        val template = ReportTemplate.TechnicalReport
        assertEquals("Technical Report", template.name)
        assertTrue(template.sections.size >= 10)
        assertTrue(template.sections.contains(ReportSection.TECHNICAL_DETAILS))
        assertEquals(ReportTemplate.Audience.TECHNICAL, template.audience)
    }

    @Test
    fun `ReportTemplate SecurityAudit - focuses on security`() {
        val template = ReportTemplate.SecurityAudit
        assertEquals("Security Audit", template.name)
        assertTrue(template.sections.contains(ReportSection.SECURITY_ANALYSIS))
        assertEquals(ReportTemplate.Audience.SECURITY, template.audience)
        assertEquals(TemplateOptions.GroupBy.BY_SECURITY, template.options.groupBy)
    }

    @Test
    fun `ReportTemplate Custom - validates configuration`() {
        val template =
            ReportTemplate.Custom(
                name = "Test Template",
                description = "Test",
                sections = listOf(ReportSection.METADATA, ReportSection.HEALTH_SCORE),
            )
        assertEquals("Test Template", template.name)
        assertTrue(template.validate())
    }

    @Test
    fun `ReportTemplate Custom - rejects blank name`() {
        assertThrows<IllegalArgumentException> {
            ReportTemplate.Custom(
                name = "",
                description = "Test",
                sections = listOf(ReportSection.METADATA),
            )
        }
    }

    @Test
    fun `ReportTemplate Custom - requires at least one section`() {
        assertThrows<IllegalArgumentException> {
            ReportTemplate.Custom(
                name = "Test",
                description = "Test",
                sections = emptyList(),
            )
        }
    }

    @Test
    fun `ReportTemplate estimatedSize - categorizes correctly`() {
        assertEquals(
            ReportTemplate.SizeCategory.SMALL,
            ReportTemplate.QuickScan.estimatedSize,
        )
        assertTrue(
            ReportTemplate.TechnicalReport.estimatedSize in
                listOf(
                    ReportTemplate.SizeCategory.LARGE,
                    ReportTemplate.SizeCategory.VERY_LARGE,
                ),
        )
    }

    @Test
    fun `ReportTemplate companion - fromName finds templates`() {
        assertNotNull(ReportTemplate.fromName("Executive Summary"))
        assertNotNull(ReportTemplate.fromName("Technical Report"))
        assertNull(ReportTemplate.fromName("Unknown Template"))
    }

    // ============================================================================
    // ExportConfiguration Tests (15 tests)
    // ============================================================================

    @Test
    fun `ExportConfiguration - default values work correctly`() {
        val config =
            ExportConfiguration(
                format = ExportFormat.JSON,
                template = ReportTemplate.TechnicalReport,
            )
        assertEquals(ExportFormat.JSON, config.format)
        assertTrue(config.prettify)
        assertFalse(config.compress)
        assertTrue(config.validate)
        assertEquals("UTF-8", config.encoding)
        assertTrue(config.includeTimestamp)
        assertTrue(config.includeMetadata)
    }

    @Test
    fun `ExportConfiguration - validates encoding`() {
        assertThrows<IllegalArgumentException> {
            ExportConfiguration(
                format = ExportFormat.JSON,
                template = ReportTemplate.TechnicalReport,
                encoding = "",
            )
        }
        assertThrows<IllegalArgumentException> {
            ExportConfiguration(
                format = ExportFormat.JSON,
                template = ReportTemplate.TechnicalReport,
                encoding = "INVALID-ENCODING",
            )
        }
    }

    @Test
    fun `ExportConfiguration - validates timeout`() {
        assertThrows<IllegalArgumentException> {
            ExportConfiguration(
                format = ExportFormat.JSON,
                template = ReportTemplate.TechnicalReport,
                timeoutMillis = -1,
            )
        }
        assertThrows<IllegalArgumentException> {
            ExportConfiguration(
                format = ExportFormat.JSON,
                template = ReportTemplate.TechnicalReport,
                timeoutMillis = 0,
            )
        }
    }

    @Test
    fun `ExportConfiguration - validates max file size`() {
        assertThrows<IllegalArgumentException> {
            ExportConfiguration(
                format = ExportFormat.JSON,
                template = ReportTemplate.TechnicalReport,
                maxFileSizeBytes = -1,
            )
        }
    }

    @Test
    fun `ExportConfiguration - validates title length`() {
        assertThrows<IllegalArgumentException> {
            ExportConfiguration(
                format = ExportFormat.JSON,
                template = ReportTemplate.TechnicalReport,
                title = "a".repeat(ExportConfiguration.MAX_TITLE_LENGTH + 1),
            )
        }
    }

    @Test
    fun `ExportConfiguration - effectivePrettify ignores for binary formats`() {
        val config =
            ExportConfiguration(
                format = ExportFormat.PDF,
                template = ReportTemplate.TechnicalReport,
                prettify = true,
            )
        assertFalse(config.effectivePrettify) // Binary format ignores prettify
    }

    @Test
    fun `ExportConfiguration - effectiveCompress uses recommendation`() {
        // When user explicitly sets compress=false, that choice is respected
        val configExplicitOff =
            ExportConfiguration(
                format = ExportFormat.JSON,
                template = ReportTemplate.TechnicalReport,
                compress = false,
            )
        // User explicitly disabled compression, so it stays off
        assertFalse(configExplicitOff.effectiveCompress)

        // When user explicitly sets compress=true, compression is enabled
        val configExplicitOn =
            ExportConfiguration(
                format = ExportFormat.JSON,
                template = ReportTemplate.TechnicalReport,
                compress = true,
            )
        assertTrue(configExplicitOn.effectiveCompress)
    }

    @Test
    fun `ExportConfiguration - toFilename includes compression suffix`() {
        val config =
            ExportConfiguration(
                format = ExportFormat.JSON,
                template = ReportTemplate.TechnicalReport,
                compress = true,
            )
        assertEquals("report.json.gz", config.toFilename("report"))
    }

    @Test
    fun `ExportConfiguration - contentType includes charset`() {
        val config =
            ExportConfiguration(
                format = ExportFormat.JSON,
                template = ReportTemplate.TechnicalReport,
            )
        assertEquals("application/json; charset=UTF-8", config.contentType())
    }

    @Test
    fun `ExportConfiguration - contentEncoding returns gzip when compressed`() {
        val config =
            ExportConfiguration(
                format = ExportFormat.JSON,
                template = ReportTemplate.TechnicalReport,
                compress = true,
            )
        assertEquals("gzip", config.contentEncoding())
    }

    @Test
    fun `ExportConfiguration Builder - builds correct configuration`() {
        val config =
            ExportConfiguration
                .Builder()
                .format(ExportFormat.PDF)
                .template(ReportTemplate.SecurityAudit)
                .prettify(false)
                .compress(true)
                .title("Security Report")
                .priority(ExportConfiguration.Priority.HIGH)
                .build()

        assertEquals(ExportFormat.PDF, config.format)
        assertEquals(ReportTemplate.SecurityAudit, config.template)
        assertEquals("Security Report", config.title)
        assertEquals(ExportConfiguration.Priority.HIGH, config.priority)
    }

    @Test
    fun `ExportConfiguration factory - json() has correct defaults`() {
        val config = ExportConfiguration.json()
        assertEquals(ExportFormat.JSON, config.format)
        assertTrue(config.prettify)
        assertTrue(config.validate)
    }

    @Test
    fun `ExportConfiguration factory - csv() enables compression`() {
        val config = ExportConfiguration.csv()
        assertEquals(ExportFormat.CSV, config.format)
        assertTrue(config.compress)
    }

    @Test
    fun `ExportConfiguration factory - quick() has high priority`() {
        val config = ExportConfiguration.quick()
        assertEquals(ExportConfiguration.Priority.HIGH, config.priority)
        assertEquals(ReportTemplate.QuickScan, config.template)
    }

    @Test
    fun `ExportConfiguration factory - compliance() has critical priority`() {
        val config = ExportConfiguration.compliance()
        assertEquals(ExportConfiguration.Priority.CRITICAL, config.priority)
        assertEquals(ReportTemplate.ComplianceReport, config.template)
        assertTrue(config.includeRawData)
    }

    // ============================================================================
    // DataFilter Tests (20 tests)
    // ============================================================================

    @Test
    fun `DataFilter NoFilter - is empty`() {
        val filter = DataFilter.NoFilter
        assertTrue(filter.isEmpty())
        assertEquals(0, filter.criteriaCount())
        assertEquals("No filtering (all data)", filter.describe())
    }

    @Test
    fun `DataFilter Criteria - validates RSSI range`() {
        assertThrows<IllegalArgumentException> {
            DataFilter.Criteria(minRssi = -200) // Below minimum
        }
        assertThrows<IllegalArgumentException> {
            DataFilter.Criteria(maxRssi = 10) // Above maximum
        }
        assertThrows<IllegalArgumentException> {
            DataFilter.Criteria(minRssi = -50, maxRssi = -60) // Min > Max
        }
    }

    @Test
    fun `DataFilter Criteria - validates SNR`() {
        assertThrows<IllegalArgumentException> {
            DataFilter.Criteria(minSnr = -5.0) // Negative
        }
    }

    @Test
    fun `DataFilter Criteria - validates bandwidth`() {
        assertThrows<IllegalArgumentException> {
            DataFilter.Criteria(minBandwidth = 0.0)
        }
        assertThrows<IllegalArgumentException> {
            DataFilter.Criteria(minBandwidth = -10.0)
        }
    }

    @Test
    fun `DataFilter Criteria - validates packet loss`() {
        assertThrows<IllegalArgumentException> {
            DataFilter.Criteria(maxPacketLoss = -1.0)
        }
        assertThrows<IllegalArgumentException> {
            DataFilter.Criteria(maxPacketLoss = 101.0)
        }
    }

    @Test
    fun `DataFilter Criteria - validates limit`() {
        assertThrows<IllegalArgumentException> {
            DataFilter.Criteria(limit = 0)
        }
        assertThrows<IllegalArgumentException> {
            DataFilter.Criteria(limit = -1)
        }
    }

    @Test
    fun `DataFilter Criteria - validates non-empty lists`() {
        assertThrows<IllegalArgumentException> {
            DataFilter.Criteria(networks = emptyList())
        }
        assertThrows<IllegalArgumentException> {
            DataFilter.Criteria(bssids = emptyList())
        }
    }

    @Test
    fun `DataFilter Criteria - counts criteria correctly`() {
        val filter =
            DataFilter.Criteria(
                minRssi = -70,
                networks = listOf("WiFi1", "WiFi2"),
                excludeHidden = true,
                limit = 10,
            )
        assertEquals(4, filter.criteriaCount())
    }

    @Test
    fun `DataFilter Criteria - describe() generates readable description`() {
        val filter =
            DataFilter.Criteria(
                minRssi = -70,
                networks = listOf("WiFi1"),
            )
        val description = filter.describe()
        assertTrue(description.contains("RSSI >= -70"))
        assertTrue(description.contains("WiFi1"))
    }

    @Test
    fun `DataFilter TimeRange - validates start before end`() {
        val now = Instant.now()
        val earlier = now.minusSeconds(3600)

        assertThrows<IllegalArgumentException> {
            DataFilter.TimeRange(now, earlier) // End before start
        }

        // Valid range
        val range = DataFilter.TimeRange(earlier, now)
        assertEquals(3600, range.duration.seconds)
    }

    @Test
    fun `DataFilter TimeRange - lastHours creates correct range`() {
        val range = DataFilter.TimeRange.lastHours(24)
        assertTrue(range.duration.toHours() in 23..24) // Allow for rounding
    }

    @Test
    fun `DataFilter TimeRange - lastDays creates correct range`() {
        val range = DataFilter.TimeRange.lastDays(7)
        assertTrue(range.duration.toDays() in 6..7)
    }

    @Test
    fun `DataFilter TimeRange - today creates range from midnight`() {
        val range = DataFilter.TimeRange.today()
        assertTrue(range.duration.toHours() <= 24)
    }

    @Test
    fun `DataFilter Builder - builds correct filter`() {
        val filter =
            DataFilter
                .Builder()
                .networks(listOf("WiFi1", "WiFi2"))
                .minRssi(-70)
                .excludeHidden(true)
                .limit(10)
                .build() as DataFilter.Criteria

        assertEquals(listOf("WiFi1", "WiFi2"), filter.networks)
        assertEquals(-70, filter.minRssi)
        assertTrue(filter.excludeHidden)
        assertEquals(10, filter.limit)
    }

    @Test
    fun `DataFilter Builder - supports time range helpers`() {
        val filter =
            DataFilter
                .Builder()
                .lastHours(24)
                .build() as DataFilter.Criteria

        assertNotNull(filter.timeRange)
    }

    @Test
    fun `DataFilter And - requires at least 2 filters`() {
        assertThrows<IllegalArgumentException> {
            DataFilter.And(emptyList())
        }
        assertThrows<IllegalArgumentException> {
            DataFilter.And(listOf(DataFilter.NoFilter))
        }
    }

    @Test
    fun `DataFilter Or - requires at least 2 filters`() {
        assertThrows<IllegalArgumentException> {
            DataFilter.Or(emptyList())
        }
    }

    @Test
    fun `DataFilter companion - and() combines filters`() {
        val filter1 = DataFilter.Builder().minRssi(-70).build()
        val filter2 = DataFilter.Builder().networks(listOf("WiFi1")).build()
        val combined = DataFilter.and(filter1, filter2)

        assertTrue(combined is DataFilter.And)
        assertEquals(2, combined.filters.size)
    }

    @Test
    fun `DataFilter companion - recent() creates time-based filter`() {
        val filter = DataFilter.recent(12) as DataFilter.Criteria
        assertNotNull(filter.timeRange)
    }

    @Test
    fun `DataFilter companion - highQuality() has correct thresholds`() {
        val filter = DataFilter.highQuality() as DataFilter.Criteria
        assertEquals(-60, filter.minRssi)
        assertEquals(20.0, filter.minSnr)
        assertTrue(filter.excludeOpen)
    }

    // ============================================================================
    // ValidationResult Tests (15 tests)
    // ============================================================================

    @Test
    fun `ValidationResult success - creates valid result`() {
        val result = ValidationResult.success()
        assertTrue(result.isValid)
        assertFalse(result.isInvalid)
        assertTrue(result.errors.isEmpty())
        assertEquals(0, result.totalIssues)
    }

    @Test
    fun `ValidationResult success - can include warnings`() {
        val warning =
            ValidationIssue.warning(
                ValidationCategory.CONTENT,
                "Test warning",
            )
        val result = ValidationResult.success(warnings = listOf(warning))
        assertTrue(result.isValid)
        assertTrue(result.hasWarnings)
        assertEquals(1, result.totalIssues)
    }

    @Test
    fun `ValidationResult failure - creates invalid result`() {
        val error =
            ValidationIssue.error(
                ValidationCategory.SCHEMA,
                "Test error",
            )
        val result = ValidationResult.failure(listOf(error))
        assertFalse(result.isValid)
        assertTrue(result.isInvalid)
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `ValidationResult failure - requires at least one error`() {
        assertThrows<IllegalArgumentException> {
            ValidationResult.failure(emptyList())
        }
    }

    @Test
    fun `ValidationResult - validates isValid matches errors`() {
        assertThrows<IllegalArgumentException> {
            ValidationResult(
                isValid = true,
                errors = listOf(ValidationIssue.schema("error")),
            )
        }
    }

    @Test
    fun `ValidationResult summary - generates readable summary`() {
        val error = ValidationIssue.schema("Missing field")
        val result = ValidationResult.failure(listOf(error))
        val summary = result.summary()

        assertTrue(summary.contains("Validation failed"))
        assertTrue(summary.contains("Errors: 1"))
        assertTrue(summary.contains("Missing field"))
    }

    @Test
    fun `ValidationResult throwIfInvalid - throws on failure`() {
        val result =
            ValidationResult.failure(
                ValidationIssue.schema("Error"),
            )
        assertThrows<ValidationException> {
            result.throwIfInvalid()
        }
    }

    @Test
    fun `ValidationResult throwIfInvalid - succeeds on valid`() {
        val result = ValidationResult.success()
        result.throwIfInvalid() // Should not throw
    }

    @Test
    fun `ValidationResult combine - aggregates all issues`() {
        val result1 =
            ValidationResult.failure(
                ValidationIssue.schema("Error 1"),
            )
        val result2 =
            ValidationResult.success(
                warnings =
                    listOf(
                        ValidationIssue.warning(
                            ValidationCategory.CONTENT,
                            "Warning 1",
                        ),
                    ),
            )

        val combined = ValidationResult.combine(result1, result2)
        assertFalse(combined.isValid)
        assertEquals(1, combined.errors.size)
        assertEquals(1, combined.warnings.size)
    }

    @Test
    fun `ValidationResult combine - empty list returns success`() {
        val combined = ValidationResult.combine(emptyList())
        assertTrue(combined.isValid)
    }

    @Test
    fun `ValidationIssue - validates message not blank`() {
        assertThrows<IllegalArgumentException> {
            ValidationIssue(
                severity = ValidationIssue.Severity.ERROR,
                category = ValidationCategory.SCHEMA,
                message = "",
            )
        }
    }

    @Test
    fun `ValidationIssue factories - create correct severity`() {
        val error = ValidationIssue.error(ValidationCategory.SCHEMA, "Test")
        assertEquals(ValidationIssue.Severity.ERROR, error.severity)

        val warning = ValidationIssue.warning(ValidationCategory.CONTENT, "Test")
        assertEquals(ValidationIssue.Severity.WARNING, warning.severity)

        val info = ValidationIssue.info(ValidationCategory.GENERAL, "Test")
        assertEquals(ValidationIssue.Severity.INFO, info.severity)
    }

    @Test
    fun `ValidationIssue convenience factories - schema, format, content`() {
        val schema = ValidationIssue.schema("Schema error")
        assertEquals(ValidationCategory.SCHEMA, schema.category)

        val format = ValidationIssue.format("Format error")
        assertEquals(ValidationCategory.FORMAT, format.category)

        val content = ValidationIssue.content("Content error")
        assertEquals(ValidationCategory.CONTENT, content.category)
    }

    @Test
    fun `ValidationException - cannot create for valid result`() {
        val result = ValidationResult.success()
        assertThrows<IllegalArgumentException> {
            ValidationException(result)
        }
    }

    @Test
    fun `ValidationCategory - all categories have properties`() {
        ValidationCategory.entries.forEach { category ->
            assertNotNull(category.displayName)
            assertTrue(category.displayName.isNotBlank())
            assertNotNull(category.description)
            assertTrue(category.description.isNotBlank())
        }
    }
}
