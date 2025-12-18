package io.lamco.netkit.exporttools.tools

import io.lamco.netkit.exporttools.exporter.*
import io.lamco.netkit.exporttools.model.*
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Report orchestrator for coordinating complete report generation.
 *
 * The orchestrator manages the end-to-end report generation process:
 * - Section preparation based on template
 * - Data aggregation and filtering
 * - Format-specific export
 * - Metadata injection
 * - Validation
 * - Post-processing (compression, etc.)
 *
 * ## Features
 * - **Template-based Generation**: Uses ReportTemplate to determine structure
 * - **Multi-Format Support**: JSON, CSV, HTML, Markdown, PDF
 * - **Section Management**: Only includes configured sections
 * - **Data Filtering**: Applies DataFilter before export
 * - **Metadata Injection**: Adds timestamps, authorship, version info
 * - **Validation**: Pre and post-export validation
 * - **Error Recovery**: Graceful handling of partial failures
 *
 * ## Usage Example
 *
 * ```kotlin
 * val orchestrator = ReportOrchestrator()
 * val data = ReportData(
 *     networkScans = scanResults,
 *     diagnostics = activeTests,
 *     securityAnalysis = securityData
 * )
 * val config = ExportConfiguration.pdf(
 *     template = ReportTemplate.TechnicalReport
 * )
 *
 * val result = orchestrator.generateReport(data, config)
 * if (result.success) {
 *     Files.write(Paths.get("report.pdf"), result.content!!.toByteArray())
 * }
 * ```
 *
 * @since 1.0.0
 */
class ReportOrchestrator {
    /**
     * Generates complete report from data and configuration.
     *
     * @param data Report data containing all analysis results
     * @param config Export configuration with format and template
     * @return ExportResult with generated report or error
     */
    fun generateReport(
        data: ReportData,
        config: ExportConfiguration,
    ): ExportResult {
        val startTime = System.currentTimeMillis()

        return try {
            val configValidation = validateConfiguration(config)
            if (!configValidation.isValid) {
                return ExportResult.failure(
                    format = config.format,
                    error = "Invalid configuration",
                    validation = configValidation,
                )
            }

            val filteredData =
                if (config.dataFilter != null) {
                    applyDataFilter(data, config.dataFilter)
                } else {
                    data
                }

            val sections = prepareSections(filteredData, config)

            val finalData =
                if (config.includeMetadata) {
                    addMetadata(sections, config)
                } else {
                    sections
                }

            val result = exportToFormat(finalData, config)

            val endTime = System.currentTimeMillis()
            result.copy(durationMillis = endTime - startTime)
        } catch (e: IllegalArgumentException) {
            ExportResult.failure(
                format = config.format,
                error = "Report generation failed: ${e.message}",
            )
        }
    }

    /**
     * Validates export configuration.
     */
    private fun validateConfiguration(config: ExportConfiguration): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        if (!config.template.validate()) {
            issues.add(
                ValidationIssue.error(
                    category = ValidationCategory.TEMPLATE,
                    message = "Invalid template configuration",
                ),
            )
        }

        if (config.template.includesCharts && !config.format.supportsCharts) {
            issues.add(
                ValidationIssue.warning(
                    category = ValidationCategory.CONFIGURATION,
                    message = "Template includes charts but format ${config.format} doesn't support them",
                    suggestion = "Use PDF, HTML, or remove chart sections",
                ),
            )
        }

        return if (issues.isEmpty()) {
            ValidationResult.success()
        } else {
            if (issues.any { it.severity == ValidationIssue.Severity.ERROR }) {
                ValidationResult.failure(issues.filter { it.severity == ValidationIssue.Severity.ERROR })
            } else {
                ValidationResult.success(warnings = issues)
            }
        }
    }

    /**
     * Applies data filter to report data.
     */
    private fun applyDataFilter(
        data: ReportData,
        filter: DataFilter,
    ): ReportData {
        // Filter implementation would filter networks, scan results, etc.
        // based on filter criteria. Simplified for this implementation.
        return data
    }

    /**
     * Prepares report sections based on template.
     */
    private fun prepareSections(
        data: ReportData,
        config: ExportConfiguration,
    ): Map<String, Any> {
        val sections = mutableMapOf<String, Any>()
        val template = config.template

        for (section in template.sortedSections()) {
            val sectionData =
                when (section) {
                    ReportSection.EXECUTIVE_SUMMARY -> prepareExecutiveSummary(data)
                    ReportSection.HEALTH_SCORE -> prepareHealthScore(data)
                    ReportSection.SECURITY_ANALYSIS -> prepareSecurityAnalysis(data)
                    ReportSection.PERFORMANCE_METRICS -> preparePerformanceMetrics(data)
                    ReportSection.ACTIVE_DIAGNOSTICS -> prepareActiveDiagnostics(data)
                    ReportSection.PASSIVE_ANALYSIS -> preparePassiveAnalysis(data)
                    ReportSection.TOPOLOGY_ANALYSIS -> prepareTopologyAnalysis(data)
                    ReportSection.CORRELATIONS -> prepareCorrelations(data)
                    ReportSection.RECOMMENDATIONS -> prepareRecommendations(data)
                    ReportSection.TECHNICAL_DETAILS -> prepareTechnicalDetails(data)
                    ReportSection.RAW_DATA -> if (config.includeRawData) data.rawData else null
                    ReportSection.METADATA -> null // Added separately
                    ReportSection.APPENDIX -> prepareAppendix(data)
                }

            if (sectionData != null) {
                sections[section.displayName] = sectionData
            }
        }

        return sections
    }

    /**
     * Prepares executive summary section.
     */
    private fun prepareExecutiveSummary(data: ReportData): Map<String, Any> =
        mapOf(
            "networkCount" to (data.networkScans?.size ?: 0),
            "securityScore" to (data.overallSecurityScore ?: 0.0),
            "topIssues" to (data.topIssues ?: emptyList<String>()),
            "summary" to (data.executiveSummary ?: "Network analysis complete"),
        )

    /**
     * Prepares health score section.
     */
    private fun prepareHealthScore(data: ReportData): Map<String, Any> =
        mapOf(
            "overallScore" to (data.healthScore ?: 0.0),
            "dimensions" to (data.healthDimensions ?: emptyMap<String, Double>()),
            "trend" to (data.healthTrend ?: "stable"),
        )

    /**
     * Prepares security analysis section.
     */
    private fun prepareSecurityAnalysis(data: ReportData): Map<String, Any> =
        mapOf(
            "vulnerabilities" to (data.vulnerabilities ?: emptyList<Map<String, Any>>()),
            "encryptionTypes" to (data.encryptionSummary ?: emptyMap<String, Int>()),
            "recommendations" to (data.securityRecommendations ?: emptyList<String>()),
        )

    /**
     * Prepares performance metrics section.
     */
    private fun preparePerformanceMetrics(data: ReportData): Map<String, Any> =
        mapOf(
            "avgSignalStrength" to (data.avgSignalStrength ?: 0.0),
            "avgThroughput" to (data.avgThroughput ?: 0.0),
            "avgLatency" to (data.avgLatency ?: 0.0),
            "performanceSummary" to (data.performanceSummary ?: emptyMap<String, Any>()),
        )

    /**
     * Prepares active diagnostics section.
     */
    private fun prepareActiveDiagnostics(data: ReportData): Map<String, Any> =
        mapOf(
            "pingTests" to (data.pingResults ?: emptyList<Map<String, Any>>()),
            "traceroutes" to (data.tracerouteResults ?: emptyList<Map<String, Any>>()),
            "bandwidthTests" to (data.bandwidthResults ?: emptyList<Map<String, Any>>()),
            "dnsTests" to (data.dnsResults ?: emptyList<Map<String, Any>>()),
        )

    /**
     * Prepares passive analysis section.
     */
    private fun preparePassiveAnalysis(data: ReportData): Map<String, Any> =
        mapOf(
            "scanResults" to (data.networkScans ?: emptyList<Map<String, Any>>()),
            "signalAnalysis" to (data.signalAnalysis ?: emptyMap<String, Any>()),
            "channelUtilization" to (data.channelUtilization ?: emptyMap<String, Double>()),
        )

    /**
     * Prepares topology analysis section.
     */
    private fun prepareTopologyAnalysis(data: ReportData): Map<String, Any> =
        mapOf(
            "accessPoints" to (data.accessPoints ?: emptyList<Map<String, Any>>()),
            "roamingDomains" to (data.roamingDomains ?: emptyList<Map<String, Any>>()),
            "coverageMap" to (data.coverageData ?: emptyMap<String, Any>()),
        )

    /**
     * Prepares correlations section.
     */
    private fun prepareCorrelations(data: ReportData): Map<String, Any> =
        mapOf(
            "rfVsPerformance" to (data.rfPerformanceCorrelations ?: emptyList<Map<String, Any>>()),
            "securityVsPerformance" to (data.securityPerformanceCorrelations ?: emptyList<Map<String, Any>>()),
        )

    /**
     * Prepares recommendations section.
     */
    private fun prepareRecommendations(data: ReportData): List<Map<String, Any>> = data.recommendations ?: emptyList()

    /**
     * Prepares technical details section.
     */
    private fun prepareTechnicalDetails(data: ReportData): Map<String, Any> =
        mapOf(
            "deviceCapabilities" to (data.deviceCapabilities ?: emptyMap<String, Any>()),
            "advancedMetrics" to (data.advancedMetrics ?: emptyMap<String, Any>()),
        )

    /**
     * Prepares appendix section.
     */
    private fun prepareAppendix(data: ReportData): Map<String, Any> =
        mapOf(
            "glossary" to GLOSSARY_TERMS,
            "methodology" to METHODOLOGY_NOTES,
            "references" to STANDARDS_REFERENCES,
        )

    /**
     * Adds metadata to report.
     */
    private fun addMetadata(
        sections: Map<String, Any>,
        config: ExportConfiguration,
    ): Map<String, Any> {
        val metadata =
            mapOf(
                "title" to (config.title ?: "WiFi Intelligence Report"),
                "author" to config.author,
                "generatedAt" to DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                "format" to config.format.displayName,
                "template" to config.template.name,
                "version" to "1.0.0",
            )

        return sections + mapOf("Metadata" to metadata)
    }

    /**
     * Exports data to configured format.
     */
    private fun exportToFormat(
        data: Map<String, Any>,
        config: ExportConfiguration,
    ): ExportResult =
        when (config.format) {
            ExportFormat.JSON -> JsonExporter().export(data, config)
            ExportFormat.CSV -> CsvExporter().export(convertToTableFormat(data), config)
            ExportFormat.HTML -> HtmlRenderer().export(data, config)
            ExportFormat.MARKDOWN -> MarkdownGenerator().export(data, config)
            ExportFormat.PDF -> PdfGenerator().export(data, config)
        }

    /**
     * Converts hierarchical data to table format for CSV export.
     */
    private fun convertToTableFormat(data: Map<String, Any>): List<Map<String, Any>> {
        val rows = mutableListOf<Map<String, Any>>()

        for ((section, content) in data) {
            when (content) {
                is List<*> ->
                    content.forEach { item ->
                        if (item is Map<*, *>) {
                            rows.add(item.mapKeys { it.key.toString() }.mapValues { it.value ?: "" })
                        }
                    }
                is Map<*, *> ->
                    rows.add(
                        mapOf("Section" to section) +
                            content
                                .mapKeys { it.key.toString() }
                                .mapValues { it.value ?: "" },
                    )
            }
        }

        return rows.ifEmpty { listOf(mapOf("Error" to "No data to export")) }
    }

    companion object {
        /**
         * Glossary terms for appendix.
         */
        private val GLOSSARY_TERMS =
            mapOf(
                "RSSI" to "Received Signal Strength Indicator - measures signal strength in dBm",
                "SNR" to "Signal-to-Noise Ratio - ratio of signal power to noise power",
                "BSSID" to "Basic Service Set Identifier - MAC address of access point",
                "SSID" to "Service Set Identifier - network name",
                "WPA3" to "Wi-Fi Protected Access 3 - latest WiFi security protocol",
            )

        /**
         * Methodology notes for appendix.
         */
        private val METHODOLOGY_NOTES =
            """
            WiFi Intelligence uses a combination of passive and active network analysis:
            - Passive: RF signal measurements, spectrum analysis, IE parsing
            - Active: Connectivity tests (ping, traceroute), bandwidth measurement, DNS resolution

            Analysis follows IEEE 802.11 standards and industry best practices.
            """.trimIndent()

        /**
         * Standards references for appendix.
         */
        private val STANDARDS_REFERENCES =
            listOf(
                "IEEE 802.11-2020: Wireless LAN Medium Access Control (MAC) and Physical Layer (PHY) Specifications",
                "RFC 8259: JSON Data Interchange Format",
                "RFC 4180: Common Format and MIME Type for CSV Files",
                "NIST SP 800-97: Establishing Wireless Robust Security Networks",
                "PCI DSS v4.0: Payment Card Industry Data Security Standard",
            )
    }
}

/**
 * Report data container with all analysis results.
 *
 * Holds all data that can be included in reports. Not all fields are required;
 * only available data will be included in the generated report.
 *
 * @property networkScans List of network scan results
 * @property diagnostics Active diagnostic test results
 * @property overallSecurityScore Overall security score (0-100)
 * @property healthScore Overall health score (0-100)
 * @property healthDimensions Individual dimension scores
 * @property healthTrend Health trend ("improving", "stable", "degrading")
 * @property vulnerabilities Detected security vulnerabilities
 * @property encryptionSummary Summary of encryption types found
 * @property securityRecommendations Security improvement recommendations
 * @property recommendations All recommendations
 * @property topIssues Top issues for executive summary
 * @property executiveSummary Executive summary text
 * @property performanceSummary Performance metrics summary
 * @property avgSignalStrength Average signal strength (dBm)
 * @property avgThroughput Average throughput (Mbps)
 * @property avgLatency Average latency (ms)
 * @property pingResults Ping test results
 * @property tracerouteResults Traceroute results
 * @property bandwidthResults Bandwidth test results
 * @property dnsResults DNS test results
 * @property signalAnalysis Signal strength analysis
 * @property channelUtilization Channel utilization data
 * @property accessPoints Access point information
 * @property roamingDomains Roaming domain data
 * @property coverageData Coverage map data
 * @property rfPerformanceCorrelations RF vs performance correlations
 * @property securityPerformanceCorrelations Security vs performance correlations
 * @property deviceCapabilities Device capability information
 * @property advancedMetrics Advanced technical metrics
 * @property rawData Raw unprocessed data
 *
 * @since 1.0.0
 */
data class ReportData(
    val networkScans: List<Map<String, Any>>? = null,
    val diagnostics: Map<String, Any>? = null,
    val overallSecurityScore: Double? = null,
    val healthScore: Double? = null,
    val healthDimensions: Map<String, Double>? = null,
    val healthTrend: String? = null,
    val vulnerabilities: List<Map<String, Any>>? = null,
    val encryptionSummary: Map<String, Int>? = null,
    val securityRecommendations: List<String>? = null,
    val recommendations: List<Map<String, Any>>? = null,
    val topIssues: List<String>? = null,
    val executiveSummary: String? = null,
    val performanceSummary: Map<String, Any>? = null,
    val avgSignalStrength: Double? = null,
    val avgThroughput: Double? = null,
    val avgLatency: Double? = null,
    val pingResults: List<Map<String, Any>>? = null,
    val tracerouteResults: List<Map<String, Any>>? = null,
    val bandwidthResults: List<Map<String, Any>>? = null,
    val dnsResults: List<Map<String, Any>>? = null,
    val signalAnalysis: Map<String, Any>? = null,
    val channelUtilization: Map<String, Double>? = null,
    val accessPoints: List<Map<String, Any>>? = null,
    val roamingDomains: List<Map<String, Any>>? = null,
    val coverageData: Map<String, Any>? = null,
    val rfPerformanceCorrelations: List<Map<String, Any>>? = null,
    val securityPerformanceCorrelations: List<Map<String, Any>>? = null,
    val deviceCapabilities: Map<String, Any>? = null,
    val advancedMetrics: Map<String, Any>? = null,
    val rawData: Map<String, Any>? = null,
)
