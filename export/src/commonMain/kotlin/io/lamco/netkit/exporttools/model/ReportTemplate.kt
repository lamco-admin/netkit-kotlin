package io.lamco.netkit.exporttools.model

/**
 * Report sections that can be included in exports.
 *
 * Sections are composable building blocks for creating custom report templates.
 * Each section represents a logical grouping of information with specific content and formatting.
 *
 * ## Section Categories
 * - **Summary Sections**: Executive summary, overview
 * - **Analysis Sections**: Security, performance, topology, diagnostics
 * - **Detail Sections**: Technical details, raw data, metadata
 * - **Supplemental Sections**: Recommendations, appendix, glossary
 *
 * @property displayName Human-readable section name
 * @property description Brief description of section content
 * @property order Default ordering priority (lower = earlier in report)
 * @property required Whether this section is required in all reports
 *
 * @since 1.0.0
 */
enum class ReportSection(
    val displayName: String,
    val description: String,
    val order: Int,
    val required: Boolean = false,
) {
    /**
     * Executive summary for management and non-technical audiences.
     *
     * **Contains**:
     * - Overall network health score
     * - Key findings (top 3-5 items)
     * - Critical issues and recommendations
     * - High-level metrics summary
     */
    EXECUTIVE_SUMMARY(
        displayName = "Executive Summary",
        description = "High-level overview for management",
        order = 10,
        required = false,
    ),

    /**
     * Overall network health assessment.
     *
     * **Contains**:
     * - Comprehensive health score
     * - Dimensional breakdown (RF, security, performance, etc.)
     * - Trend analysis (improving/degrading)
     * - Health history (if available)
     */
    HEALTH_SCORE(
        displayName = "Network Health Score",
        description = "Overall health assessment and scoring",
        order = 20,
        required = false,
    ),

    /**
     * Security analysis and vulnerabilities.
     *
     * **Contains**:
     * - Security scoring by network
     * - Identified vulnerabilities
     * - Encryption analysis (WPA2, WPA3, etc.)
     * - Attack surface assessment
     * - Compliance status (PCI-DSS, NIST)
     */
    SECURITY_ANALYSIS(
        displayName = "Security Analysis",
        description = "Security vulnerabilities and recommendations",
        order = 30,
        required = false,
    ),

    /**
     * Performance metrics and analysis.
     *
     * **Contains**:
     * - Throughput measurements
     * - Latency and jitter analysis
     * - Channel utilization
     * - Capacity estimates
     * - Performance trends
     */
    PERFORMANCE_METRICS(
        displayName = "Performance Metrics",
        description = "Network performance and throughput",
        order = 40,
        required = false,
    ),

    /**
     * Active diagnostic test results.
     *
     * **Contains**:
     * - Ping test results (latency, packet loss, jitter)
     * - Traceroute analysis (routing, hops, bottlenecks)
     * - Bandwidth tests (download/upload speeds)
     * - DNS resolution tests
     * - Connectivity health
     */
    ACTIVE_DIAGNOSTICS(
        displayName = "Active Diagnostics",
        description = "Active network testing results",
        order = 50,
        required = false,
    ),

    /**
     * Passive RF analysis (signal strength, interference, etc.).
     *
     * **Contains**:
     * - RSSI and signal strength
     * - SNR and noise floor
     * - Channel interference
     * - Spectrum utilization
     * - RF quality scoring
     */
    PASSIVE_ANALYSIS(
        displayName = "Passive RF Analysis",
        description = "Passive wireless signal analysis",
        order = 60,
        required = false,
    ),

    /**
     * Multi-AP topology and roaming analysis.
     *
     * **Contains**:
     * - Detected access points
     * - AP relationships and ESS grouping
     * - Coverage overlap analysis
     * - Roaming candidate identification
     * - Handoff zones
     */
    TOPOLOGY_ANALYSIS(
        displayName = "Topology Analysis",
        description = "Multi-AP topology and roaming",
        order = 70,
        required = false,
    ),

    /**
     * Correlation analysis between passive and active data.
     *
     * **Contains**:
     * - RF vs connectivity correlations
     * - Performance bottleneck identification
     * - Root cause analysis
     * - Anomaly detection
     * - Multi-dimensional insights
     */
    CORRELATIONS(
        displayName = "Correlations",
        description = "Cross-analysis and correlations",
        order = 80,
        required = false,
    ),

    /**
     * Actionable recommendations.
     *
     * **Contains**:
     * - Prioritized recommendation list
     * - Quick wins (high impact, low effort)
     * - Long-term improvements
     * - Configuration changes
     * - Security hardening steps
     */
    RECOMMENDATIONS(
        displayName = "Recommendations",
        description = "Actionable improvement recommendations",
        order = 90,
        required = false,
    ),

    /**
     * Detailed technical information.
     *
     * **Contains**:
     * - Complete metric details
     * - Device capabilities (HT, VHT, HE, EHT)
     * - Information Elements
     * - Advanced settings
     * - Technical specifications
     */
    TECHNICAL_DETAILS(
        displayName = "Technical Details",
        description = "Detailed technical information",
        order = 100,
        required = false,
    ),

    /**
     * Raw data export (JSON, CSV arrays, etc.).
     *
     * **Contains**:
     * - Unprocessed scan results
     * - Raw measurements
     * - Diagnostic test logs
     * - Time-series data
     * - Complete data dump
     */
    RAW_DATA(
        displayName = "Raw Data",
        description = "Unprocessed raw data",
        order = 110,
        required = false,
    ),

    /**
     * Metadata about the report itself.
     *
     * **Contains**:
     * - Generation timestamp
     * - Data collection period
     * - Device information (model, OS, app version)
     * - Location/site information
     * - Report version
     */
    METADATA(
        displayName = "Metadata",
        description = "Report metadata and generation info",
        order = 120,
        required = true,
    ),

    /**
     * Appendix with supplemental information.
     *
     * **Contains**:
     * - Glossary of terms
     * - Methodology notes
     * - Standards references (IEEE 802.11, RFCs)
     * - Calibration data
     * - Known limitations
     */
    APPENDIX(
        displayName = "Appendix",
        description = "Supplemental information and glossary",
        order = 130,
        required = false,
    ),
    ;

    companion object {
        /**
         * Required sections that must appear in all reports.
         */
        val requiredSections: List<ReportSection> = entries.filter { it.required }

        /**
         * Optional sections that can be included/excluded.
         */
        val optionalSections: List<ReportSection> = entries.filter { !it.required }

        /**
         * Sections sorted by default order.
         */
        val byOrder: List<ReportSection> = entries.sortedBy { it.order }

        /**
         * Summary sections (executive, health, recommendations).
         */
        val summarySections: List<ReportSection> =
            listOf(
                EXECUTIVE_SUMMARY,
                HEALTH_SCORE,
                RECOMMENDATIONS,
            )

        /**
         * Analysis sections (security, performance, diagnostics, etc.).
         */
        val analysisSections: List<ReportSection> =
            listOf(
                SECURITY_ANALYSIS,
                PERFORMANCE_METRICS,
                ACTIVE_DIAGNOSTICS,
                PASSIVE_ANALYSIS,
                TOPOLOGY_ANALYSIS,
                CORRELATIONS,
            )

        /**
         * Detail sections (technical, raw data, metadata, appendix).
         */
        val detailSections: List<ReportSection> =
            listOf(
                TECHNICAL_DETAILS,
                RAW_DATA,
                METADATA,
                APPENDIX,
            )
    }
}

/**
 * Customization options for report templates.
 *
 * Options control the level of detail, included content, and formatting preferences
 * for generated reports.
 *
 * @property includeCharts Include charts and visualizations (if format supports)
 * @property includeRecommendations Include actionable recommendations
 * @property includeRawData Include raw unprocessed data
 * @property includeTechnicalDetails Include detailed technical information
 * @property includeMetadata Include report generation metadata
 * @property includeAppendix Include appendix with glossary and references
 * @property detailLevel Level of detail (MINIMAL, STANDARD, COMPREHENSIVE)
 * @property sortBy How to sort data (CHRONOLOGICAL, PRIORITY, ALPHABETICAL, SCORE)
 * @property groupBy How to group data (NONE, BY_NETWORK, BY_SECURITY, BY_BAND)
 * @property maxNetworks Maximum number of networks to include (null = unlimited)
 * @property maxRecommendations Maximum recommendations to include (null = unlimited)
 *
 * @since 1.0.0
 */
data class TemplateOptions(
    val includeCharts: Boolean = true,
    val includeRecommendations: Boolean = true,
    val includeRawData: Boolean = false,
    val includeTechnicalDetails: Boolean = true,
    val includeMetadata: Boolean = true,
    val includeAppendix: Boolean = false,
    val detailLevel: DetailLevel = DetailLevel.STANDARD,
    val sortBy: SortOrder = SortOrder.PRIORITY,
    val groupBy: GroupBy = GroupBy.NONE,
    val maxNetworks: Int? = null,
    val maxRecommendations: Int? = null,
) {
    init {
        maxNetworks?.let {
            require(it > 0) { "maxNetworks must be positive, got: $it" }
        }
        maxRecommendations?.let {
            require(it > 0) { "maxRecommendations must be positive, got: $it" }
        }
    }

    /**
     * Detail level for report content.
     */
    enum class DetailLevel {
        /** Minimal detail - only critical information */
        MINIMAL,

        /** Standard detail - balanced information */
        STANDARD,

        /** Comprehensive detail - all available information */
        COMPREHENSIVE,
    }

    /**
     * Sort order for data presentation.
     */
    enum class SortOrder {
        /** Chronological order (newest first) */
        CHRONOLOGICAL,

        /** Priority order (most important first) */
        PRIORITY,

        /** Alphabetical order (A-Z) */
        ALPHABETICAL,

        /** Score order (highest/best first) */
        SCORE,
    }

    /**
     * Grouping strategy for data.
     */
    enum class GroupBy {
        /** No grouping */
        NONE,

        /** Group by network (SSID) */
        BY_NETWORK,

        /** Group by security type */
        BY_SECURITY,

        /** Group by frequency band */
        BY_BAND,

        /** Group by location/site */
        BY_LOCATION,
    }

    companion object {
        /**
         * Minimal template options - only essential information.
         */
        val MINIMAL =
            TemplateOptions(
                includeCharts = false,
                includeRecommendations = true,
                includeRawData = false,
                includeTechnicalDetails = false,
                includeMetadata = true,
                includeAppendix = false,
                detailLevel = DetailLevel.MINIMAL,
            )

        /**
         * Standard template options - balanced detail.
         */
        val STANDARD =
            TemplateOptions(
                includeCharts = true,
                includeRecommendations = true,
                includeRawData = false,
                includeTechnicalDetails = true,
                includeMetadata = true,
                includeAppendix = false,
                detailLevel = DetailLevel.STANDARD,
            )

        /**
         * Comprehensive template options - all available information.
         */
        val COMPREHENSIVE =
            TemplateOptions(
                includeCharts = true,
                includeRecommendations = true,
                includeRawData = true,
                includeTechnicalDetails = true,
                includeMetadata = true,
                includeAppendix = true,
                detailLevel = DetailLevel.COMPREHENSIVE,
            )
    }
}

/**
 * Report template defining structure and content.
 *
 * Templates are predefined configurations that specify which sections to include,
 * how to order them, and what level of detail to provide. Templates can be standard
 * (predefined) or custom (user-defined).
 *
 * ## Standard Templates
 * - **EXECUTIVE_SUMMARY**: High-level overview for management
 * - **TECHNICAL_REPORT**: Comprehensive technical analysis
 * - **SECURITY_AUDIT**: Security-focused assessment
 * - **COMPLIANCE_REPORT**: PCI-DSS/NIST compliance documentation
 * - **SITE_SURVEY**: Site survey results with coverage analysis
 * - **QUICK_SCAN**: Fast scan with minimal detail
 *
 * ## Custom Templates
 * Custom templates allow specifying exact sections and options.
 *
 * @since 1.0.0
 */
sealed class ReportTemplate {
    /**
     * Template name.
     */
    abstract val name: String

    /**
     * Template description.
     */
    abstract val description: String

    /**
     * Sections included in this template.
     */
    abstract val sections: List<ReportSection>

    /**
     * Template customization options.
     */
    abstract val options: TemplateOptions

    /**
     * Target audience for this template.
     */
    abstract val audience: Audience

    /**
     * Estimated report size category.
     */
    val estimatedSize: SizeCategory
        get() =
            when {
                sections.size <= 3 -> SizeCategory.SMALL
                sections.size <= 7 -> SizeCategory.MEDIUM
                sections.size <= 10 -> SizeCategory.LARGE
                else -> SizeCategory.VERY_LARGE
            }

    /**
     * Whether this template includes charts.
     */
    val includesCharts: Boolean
        get() = options.includeCharts

    /**
     * Whether this template includes raw data.
     */
    val includesRawData: Boolean
        get() = options.includeRawData

    /**
     * Returns sections sorted by order.
     */
    fun sortedSections(): List<ReportSection> = sections.sortedBy { it.order }

    /**
     * Validates that template configuration is valid.
     *
     * @return ValidationResult indicating success or errors
     */
    fun validate(): Boolean {
        // Ensure all required sections are included
        val requiredSections = ReportSection.requiredSections
        val hasAllRequired = requiredSections.all { it in sections }
        if (!hasAllRequired) return false

        // Ensure no duplicate sections
        val hasDuplicates = sections.size != sections.toSet().size
        if (hasDuplicates) return false

        return true
    }

    /**
     * Target audience for report.
     */
    enum class Audience {
        /** Management and executives (non-technical) */
        MANAGEMENT,

        /** Technical staff and engineers */
        TECHNICAL,

        /** Security team and auditors */
        SECURITY,

        /** Compliance officers and auditors */
        COMPLIANCE,

        /** General/mixed audience */
        GENERAL,
    }

    /**
     * Report size category.
     */
    enum class SizeCategory {
        /** Small report (1-3 sections) */
        SMALL,

        /** Medium report (4-7 sections) */
        MEDIUM,

        /** Large report (8-10 sections) */
        LARGE,

        /** Very large report (11+ sections) */
        VERY_LARGE,
    }

    /**
     * Executive summary template.
     *
     * **Target**: Management and non-technical stakeholders
     * **Focus**: High-level overview, key findings, critical issues
     * **Sections**: Executive summary, health score, recommendations
     * **Detail Level**: Minimal
     */
    data object ExecutiveSummary : ReportTemplate() {
        override val name = "Executive Summary"
        override val description = "High-level overview for management and stakeholders"
        override val sections =
            listOf(
                ReportSection.EXECUTIVE_SUMMARY,
                ReportSection.HEALTH_SCORE,
                ReportSection.RECOMMENDATIONS,
                ReportSection.METADATA,
            )
        override val options =
            TemplateOptions.MINIMAL.copy(
                includeCharts = true,
                maxRecommendations = 5,
            )
        override val audience = Audience.MANAGEMENT
    }

    /**
     * Technical report template.
     *
     * **Target**: Network engineers and technical staff
     * **Focus**: Comprehensive analysis with all details
     * **Sections**: All analysis sections plus technical details
     * **Detail Level**: Comprehensive
     */
    data object TechnicalReport : ReportTemplate() {
        override val name = "Technical Report"
        override val description = "Comprehensive technical analysis for engineers"
        override val sections =
            listOf(
                ReportSection.HEALTH_SCORE,
                ReportSection.SECURITY_ANALYSIS,
                ReportSection.PERFORMANCE_METRICS,
                ReportSection.ACTIVE_DIAGNOSTICS,
                ReportSection.PASSIVE_ANALYSIS,
                ReportSection.TOPOLOGY_ANALYSIS,
                ReportSection.CORRELATIONS,
                ReportSection.RECOMMENDATIONS,
                ReportSection.TECHNICAL_DETAILS,
                ReportSection.METADATA,
                ReportSection.APPENDIX,
            )
        override val options = TemplateOptions.COMPREHENSIVE
        override val audience = Audience.TECHNICAL
    }

    /**
     * Security audit template.
     *
     * **Target**: Security team and auditors
     * **Focus**: Security vulnerabilities, compliance, recommendations
     * **Sections**: Security analysis, compliance, recommendations
     * **Detail Level**: Standard
     */
    data object SecurityAudit : ReportTemplate() {
        override val name = "Security Audit"
        override val description = "Security-focused assessment and recommendations"
        override val sections =
            listOf(
                ReportSection.EXECUTIVE_SUMMARY,
                ReportSection.SECURITY_ANALYSIS,
                ReportSection.TOPOLOGY_ANALYSIS,
                ReportSection.RECOMMENDATIONS,
                ReportSection.TECHNICAL_DETAILS,
                ReportSection.METADATA,
            )
        override val options =
            TemplateOptions.STANDARD.copy(
                sortBy = TemplateOptions.SortOrder.PRIORITY,
                groupBy = TemplateOptions.GroupBy.BY_SECURITY,
            )
        override val audience = Audience.SECURITY
    }

    /**
     * Compliance report template.
     *
     * **Target**: Compliance officers and auditors
     * **Focus**: PCI-DSS, NIST compliance documentation
     * **Sections**: Security, compliance checks, audit trail
     * **Detail Level**: Comprehensive
     */
    data object ComplianceReport : ReportTemplate() {
        override val name = "Compliance Report"
        override val description = "PCI-DSS and NIST compliance documentation"
        override val sections =
            listOf(
                ReportSection.EXECUTIVE_SUMMARY,
                ReportSection.SECURITY_ANALYSIS,
                ReportSection.TECHNICAL_DETAILS,
                ReportSection.RECOMMENDATIONS,
                ReportSection.METADATA,
                ReportSection.APPENDIX,
            )
        override val options =
            TemplateOptions.COMPREHENSIVE.copy(
                includeCharts = false, // Compliance docs typically text-only
                sortBy = TemplateOptions.SortOrder.PRIORITY,
            )
        override val audience = Audience.COMPLIANCE
    }

    /**
     * Site survey template.
     *
     * **Target**: Network planners and engineers
     * **Focus**: Coverage analysis, AP placement, topology
     * **Sections**: Topology, passive analysis, recommendations
     * **Detail Level**: Standard
     */
    data object SiteSurvey : ReportTemplate() {
        override val name = "Site Survey"
        override val description = "Site survey results with coverage analysis"
        override val sections =
            listOf(
                ReportSection.HEALTH_SCORE,
                ReportSection.PASSIVE_ANALYSIS,
                ReportSection.TOPOLOGY_ANALYSIS,
                ReportSection.PERFORMANCE_METRICS,
                ReportSection.CORRELATIONS,
                ReportSection.RECOMMENDATIONS,
                ReportSection.METADATA,
            )
        override val options =
            TemplateOptions.STANDARD.copy(
                includeCharts = true,
                groupBy = TemplateOptions.GroupBy.BY_NETWORK,
            )
        override val audience = Audience.TECHNICAL
    }

    /**
     * Quick scan template.
     *
     * **Target**: General users
     * **Focus**: Fast overview with minimal detail
     * **Sections**: Health score, basic recommendations
     * **Detail Level**: Minimal
     */
    data object QuickScan : ReportTemplate() {
        override val name = "Quick Scan"
        override val description = "Fast scan with minimal detail"
        override val sections =
            listOf(
                ReportSection.HEALTH_SCORE,
                ReportSection.RECOMMENDATIONS,
                ReportSection.METADATA,
            )
        override val options =
            TemplateOptions.MINIMAL.copy(
                maxNetworks = 10,
                maxRecommendations = 3,
            )
        override val audience = Audience.GENERAL
    }

    /**
     * Custom template with user-defined sections and options.
     *
     * @property name Template name
     * @property description Template description
     * @property sections Sections to include
     * @property options Customization options
     * @property audience Target audience
     */
    data class Custom(
        override val name: String,
        override val description: String,
        override val sections: List<ReportSection>,
        override val options: TemplateOptions = TemplateOptions.STANDARD,
        override val audience: Audience = Audience.GENERAL,
    ) : ReportTemplate() {
        init {
            require(name.isNotBlank()) { "Template name cannot be blank" }
            require(sections.isNotEmpty()) { "Template must include at least one section" }
            require(validate()) { "Template configuration is invalid" }
        }
    }

    companion object {
        /**
         * All predefined templates.
         * Uses getter to avoid Kotlin object initialization order NPE.
         */
        val predefined: List<ReportTemplate>
            get() =
                listOf(
                    ExecutiveSummary,
                    TechnicalReport,
                    SecurityAudit,
                    ComplianceReport,
                    SiteSurvey,
                    QuickScan,
                )

        /**
         * Finds predefined template by name (case-insensitive).
         *
         * @param name Template name
         * @return Matching template or null
         */
        fun fromName(name: String): ReportTemplate? = predefined.find { it.name.equals(name, ignoreCase = true) }
    }
}
