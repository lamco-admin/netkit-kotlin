package io.lamco.netkit.diagnostics.integration

import io.lamco.netkit.diagnostics.model.*

/**
 * Comprehensive network analysis report generator.
 *
 * ComprehensiveReport generates detailed network analysis reports in multiple formats:
 * - **Text**: Human-readable plain text for terminal/console
 * - **Markdown**: GitHub-flavored markdown for documentation
 * - **JSON**: Structured JSON for programmatic consumption
 * - **HTML**: Rich HTML for web display (future)
 *
 * ## Report Sections
 *
 * Reports can include any combination of:
 * 1. Executive Summary - High-level overview
 * 2. Network Health Score - Overall score and dimensions
 * 3. Active Diagnostics - Ping, traceroute, bandwidth, DNS results
 * 4. Passive Analysis - RF quality, topology, security scores
 * 5. Correlations - Root cause analysis
 * 6. Recommendations - Actionable advice
 * 7. Technical Details - Raw test data
 * 8. Appendix - Additional context
 *
 * ## Usage Example
 *
 * ```kotlin
 * val report = ComprehensiveReport.builder()
 *     .withIntegratedAnalysis(analysis)
 *     .withSections(
 *         ReportSection.EXECUTIVE_SUMMARY,
 *         ReportSection.HEALTH_SCORE,
 *         ReportSection.RECOMMENDATIONS
 *     )
 *     .withFormat(ReportFormat.MARKDOWN)
 *     .withTitle("Network Analysis - Site A")
 *     .build()
 *
 * val markdown = report.generateMarkdown()
 * println(markdown)
 * ```
 *
 * @property analysis The integrated analysis results
 * @property sections Sections to include in the report
 * @property format Output format
 * @property title Optional report title
 * @property includeTimestamp Whether to include timestamp
 * @property includeMetadata Whether to include metadata
 */
data class ComprehensiveReport(
    val analysis: IntegratedAnalysis,
    val sections: Set<ReportSection> = ReportSection.DEFAULT_SECTIONS,
    val format: ReportFormat = ReportFormat.MARKDOWN,
    val title: String? = null,
    val includeTimestamp: Boolean = true,
    val includeMetadata: Boolean = true,
) {
    /**
     * Generate report in text format.
     *
     * @return Plain text report
     */
    fun generateText(): String =
        buildString {
            if (title != null) {
                appendLine(title)
                appendLine("=".repeat(title.length))
                appendLine()
            }

            if (includeTimestamp) {
                appendLine("Generated: ${ReportFormatters.formatTimestamp(analysis.timestamp)}")
                appendLine()
            }

            if (ReportSection.EXECUTIVE_SUMMARY in sections) {
                appendLine(analysis.executiveSummary())
                appendLine()
            }

            if (ReportSection.HEALTH_SCORE in sections) {
                appendLine(analysis.healthScore.summary())
                appendLine()
            }

            if (ReportSection.ACTIVE_DIAGNOSTICS in sections) {
                appendLine(generateActiveDiagnosticsText())
                appendLine()
            }

            if (ReportSection.PASSIVE_ANALYSIS in sections) {
                appendLine(generatePassiveAnalysisText())
                appendLine()
            }

            if (ReportSection.CORRELATIONS in sections && analysis.correlations.isNotEmpty()) {
                appendLine(generateCorrelationsText())
                appendLine()
            }

            if (ReportSection.RECOMMENDATIONS in sections) {
                appendLine(generateRecommendationsText())
                appendLine()
            }

            if (ReportSection.TECHNICAL_DETAILS in sections) {
                appendLine(generateTechnicalDetailsText())
                appendLine()
            }

            if (includeMetadata) {
                appendLine(generateMetadataText())
            }
        }

    /**
     * Generate report in Markdown format.
     *
     * Delegates to MarkdownReportGenerator for format-specific generation.
     *
     * @return GitHub-flavored Markdown report
     */
    fun generateMarkdown(): String =
        MarkdownReportGenerator(
            analysis = analysis,
            sections = sections,
            title = title,
            includeTimestamp = includeTimestamp,
            includeMetadata = includeMetadata,
        ).generate()

    /**
     * Generate report in JSON format.
     *
     * Delegates to JsonReportGenerator for format-specific generation.
     *
     * @return JSON report structure
     */
    fun generateJson(): String =
        JsonReportGenerator(
            analysis = analysis,
            sections = sections,
            title = title,
        ).generate()

    // Text format helpers

    private fun generateActiveDiagnosticsText(): String =
        buildString {
            appendLine("Active Diagnostics")
            appendLine("=".repeat(50))

            val diag = analysis.activeDiagnostics

            appendLine("Overall Connectivity: ${diag.connectivityQuality}")
            appendLine("Overall Throughput:   ${diag.throughputQuality}")
            appendLine("Overall Routing:      ${diag.routingQuality}")
            appendLine("Overall DNS:          ${diag.dnsQuality}")
            appendLine()

            // Ping results
            if (diag.pingTests.isNotEmpty()) {
                appendLine("Ping Tests:")
                diag.pingTests.forEach { ping ->
                    appendLine("  ${ping.targetHost}:")
                    appendLine("    Quality: ${ping.overallQuality}")
                    appendLine("    Packet Loss: ${ping.packetLossPercent}%")
                    ping.avgRtt?.let { appendLine("    Avg RTT: ${ReportFormatters.formatDuration(it)}") }
                }
                appendLine()
            }

            // Traceroute results
            if (diag.tracerouteResults.isNotEmpty()) {
                appendLine("Traceroute Tests:")
                diag.tracerouteResults.forEach { trace ->
                    appendLine("  ${trace.targetHost}:")
                    appendLine("    Quality: ${trace.routeQuality}")
                    appendLine("    Hops: ${trace.hops.size}")
                    val bottlenecks = trace.findBottlenecks()
                    if (bottlenecks.isNotEmpty()) {
                        appendLine("    Bottlenecks: Hops ${bottlenecks.joinToString(", ")}")
                    }
                }
                appendLine()
            }

            // Bandwidth results
            if (diag.bandwidthTests.isNotEmpty()) {
                appendLine("Bandwidth Tests:")
                diag.bandwidthTests.forEach { bw ->
                    appendLine("  ${bw.serverHost}:")
                    appendLine("    Quality: ${bw.overallQuality}")
                    bw.downloadMbps?.let { appendLine("    Download: ${"%.1f".format(it)} Mbps") }
                    bw.uploadMbps?.let { appendLine("    Upload: ${"%.1f".format(it)} Mbps") }
                }
                appendLine()
            }

            // DNS results
            if (diag.dnsTests.isNotEmpty()) {
                appendLine("DNS Tests:")
                diag.dnsTests.forEach { dns ->
                    appendLine("  ${dns.hostname}:")
                    appendLine("    Quality: ${dns.resolutionQuality}")
                    appendLine("    Resolution Time: ${ReportFormatters.formatDuration(dns.resolutionTime)}")
                    appendLine("    Resolved: ${dns.resolvedAddresses.size} address(es)")
                }
            }
        }

    private fun generatePassiveAnalysisText(): String =
        buildString {
            appendLine("Passive Analysis")
            appendLine("=".repeat(50))

            analysis.rfQualityScore?.let {
                appendLine("RF Quality:       ${it.toInt()}/100")
            }

            analysis.topologyQualityScore?.let {
                appendLine("Topology Quality: ${it.toInt()}/100")
            }

            analysis.securityScore?.let {
                appendLine("Security Score:   ${it.toInt()}/100")
            }

            if (analysis.rfQualityScore == null &&
                analysis.topologyQualityScore == null &&
                analysis.securityScore == null
            ) {
                appendLine("(No passive analysis data available)")
            }
        }

    private fun generateCorrelationsText(): String =
        buildString {
            appendLine("Root Cause Analysis")
            appendLine("=".repeat(50))

            analysis.correlations.forEach { correlation ->
                appendLine("Finding: ${correlation.finding}")
                appendLine("  Root Cause: ${correlation.rootCause}")
                appendLine("  Action: ${correlation.recommendation}")
                appendLine()
            }
        }

    private fun generateRecommendationsText(): String =
        buildString {
            appendLine("Recommendations")
            appendLine("=".repeat(50))

            val recs = analysis.recommendations

            if (recs.critical.isNotEmpty()) {
                appendLine("CRITICAL (Immediate Action Required):")
                recs.critical.forEach {
                    appendLine("  • ${it.issue}")
                    appendLine("    ${it.action}")
                    appendLine()
                }
            }

            if (recs.performance.isNotEmpty()) {
                appendLine("PERFORMANCE:")
                recs.performance.forEach {
                    appendLine("  • ${it.issue}")
                    appendLine("    Impact: ${it.impact}, Effort: ${it.effort}")
                }
                appendLine()
            }

            if (recs.configuration.isNotEmpty()) {
                appendLine("CONFIGURATION:")
                recs.configuration.forEach {
                    appendLine("  • ${it.issue}")
                }
                appendLine()
            }

            if (recs.security.isNotEmpty()) {
                appendLine("SECURITY:")
                recs.security.forEach {
                    appendLine("  • ${it.issue}")
                }
                appendLine()
            }

            if (recs.monitoring.isNotEmpty()) {
                appendLine("MONITORING:")
                recs.monitoring.forEach {
                    appendLine("  • ${it.issue}")
                }
                appendLine()
            }

            if (recs.quickWins.isNotEmpty()) {
                appendLine("QUICK WINS (High Impact, Low Effort):")
                recs.quickWins.forEach {
                    appendLine("  💡 ${it.issue}")
                    appendLine("     ${it.action}")
                }
            }
        }

    private fun generateTechnicalDetailsText(): String =
        buildString {
            appendLine("Technical Details")
            appendLine("=".repeat(50))
            appendLine(analysis.activeDiagnostics.detailedReport())
        }

    private fun generateMetadataText(): String =
        buildString {
            appendLine("-".repeat(50))
            appendLine("Generated by WiFi Intelligence Diagnostics v1.0.0")
            appendLine("Timestamp: ${ReportFormatters.formatTimestamp(analysis.timestamp, includeMillis = true)}")
        }

    companion object {
        /**
         * Create a report builder.
         *
         * @return ComprehensiveReportBuilder
         */
        fun builder(): ComprehensiveReportBuilder = ComprehensiveReportBuilder()
    }
}

/**
 * Report sections that can be included.
 */
enum class ReportSection {
    /** High-level executive summary */
    EXECUTIVE_SUMMARY,

    /** Network health score and dimensions */
    HEALTH_SCORE,

    /** Active diagnostic test results */
    ACTIVE_DIAGNOSTICS,

    /** Passive analysis (RF, topology, security) */
    PASSIVE_ANALYSIS,

    /** Root cause correlations */
    CORRELATIONS,

    /** Actionable recommendations */
    RECOMMENDATIONS,

    /** Detailed technical data */
    TECHNICAL_DETAILS,

    /** Report metadata and appendix */
    APPENDIX,

    ;

    companion object {
        /**
         * Default sections for a standard report.
         */
        val DEFAULT_SECTIONS =
            setOf(
                EXECUTIVE_SUMMARY,
                HEALTH_SCORE,
                RECOMMENDATIONS,
            )

        /**
         * All sections for a comprehensive report.
         */
        val ALL_SECTIONS = values().toSet()

        /**
         * Essential sections only.
         */
        val ESSENTIAL_SECTIONS =
            setOf(
                EXECUTIVE_SUMMARY,
                HEALTH_SCORE,
            )

        /**
         * Technical sections for detailed analysis.
         */
        val TECHNICAL_SECTIONS =
            setOf(
                ACTIVE_DIAGNOSTICS,
                PASSIVE_ANALYSIS,
                CORRELATIONS,
                TECHNICAL_DETAILS,
            )
    }
}

/**
 * Report output formats.
 */
enum class ReportFormat {
    /** Plain text format */
    TEXT,

    /** GitHub-flavored Markdown */
    MARKDOWN,

    /** JSON format */
    JSON,

    /** HTML format (future) */
    HTML,

    ;

    /**
     * File extension for this format.
     */
    val fileExtension: String
        get() =
            when (this) {
                TEXT -> "txt"
                MARKDOWN -> "md"
                JSON -> "json"
                HTML -> "html"
            }

    /**
     * MIME type for this format.
     */
    val mimeType: String
        get() =
            when (this) {
                TEXT -> "text/plain"
                MARKDOWN -> "text/markdown"
                JSON -> "application/json"
                HTML -> "text/html"
            }
}

/**
 * Builder for constructing ComprehensiveReport instances.
 *
 * ## Usage Example
 *
 * ```kotlin
 * val report = ComprehensiveReport.builder()
 *     .withIntegratedAnalysis(analysis)
 *     .withSections(ReportSection.ALL_SECTIONS)
 *     .withFormat(ReportFormat.MARKDOWN)
 *     .withTitle("Network Analysis Report")
 *     .includeTimestamp(true)
 *     .includeMetadata(true)
 *     .build()
 * ```
 */
class ComprehensiveReportBuilder {
    private var analysis: IntegratedAnalysis? = null
    private var sections: Set<ReportSection> = ReportSection.DEFAULT_SECTIONS
    private var format: ReportFormat = ReportFormat.MARKDOWN
    private var title: String? = null
    private var includeTimestamp: Boolean = true
    private var includeMetadata: Boolean = true

    /**
     * Set the integrated analysis data.
     */
    fun withIntegratedAnalysis(analysis: IntegratedAnalysis): ComprehensiveReportBuilder {
        this.analysis = analysis
        return this
    }

    /**
     * Set report sections.
     */
    fun withSections(vararg sections: ReportSection): ComprehensiveReportBuilder {
        this.sections = sections.toSet()
        return this
    }

    /**
     * Set report sections from a set.
     */
    fun withSections(sections: Set<ReportSection>): ComprehensiveReportBuilder {
        this.sections = sections
        return this
    }

    /**
     * Include all sections.
     */
    fun withAllSections(): ComprehensiveReportBuilder {
        this.sections = ReportSection.ALL_SECTIONS
        return this
    }

    /**
     * Set output format.
     */
    fun withFormat(format: ReportFormat): ComprehensiveReportBuilder {
        this.format = format
        return this
    }

    /**
     * Set report title.
     */
    fun withTitle(title: String): ComprehensiveReportBuilder {
        this.title = title
        return this
    }

    /**
     * Whether to include timestamp.
     */
    fun includeTimestamp(include: Boolean): ComprehensiveReportBuilder {
        this.includeTimestamp = include
        return this
    }

    /**
     * Whether to include metadata.
     */
    fun includeMetadata(include: Boolean): ComprehensiveReportBuilder {
        this.includeMetadata = include
        return this
    }

    /**
     * Build the ComprehensiveReport.
     */
    fun build(): ComprehensiveReport {
        requireNotNull(analysis) { "IntegratedAnalysis is required" }

        return ComprehensiveReport(
            analysis = analysis!!,
            sections = sections,
            format = format,
            title = title,
            includeTimestamp = includeTimestamp,
            includeMetadata = includeMetadata,
        )
    }
}
