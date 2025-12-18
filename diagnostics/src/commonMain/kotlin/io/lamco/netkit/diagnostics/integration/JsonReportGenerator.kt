package io.lamco.netkit.diagnostics.integration

import io.lamco.netkit.diagnostics.model.*

/**
 * JSON report generator for WiFi Intelligence diagnostics.
 *
 * Generates structured JSON reports suitable for programmatic consumption,
 * API responses, and data integration.
 *
 * @since 1.0.0
 */
internal class JsonReportGenerator(
    private val analysis: IntegratedAnalysis,
    private val sections: Set<ReportSection>,
    private val title: String?,
) {
    /**
     * Generate complete JSON report.
     *
     * @return JSON string
     */
    fun generate(): String {
        val json = JsonBuilder()
        json.startObject()

        if (title != null) {
            json.addString("title", title)
        }

        json.addString("generatedAt", ReportFormatters.formatTimestamp(analysis.timestamp, includeMillis = true))
        analysis.location?.let { json.addString("location", it) }

        addHealthScore(json)
        addActiveDiagnostics(json)
        addPassiveAnalysis(json)
        addCorrelations(json)
        addRecommendations(json)

        json.endObject()
        return json.toString()
    }

    private fun addHealthScore(json: JsonBuilder) {
        if (ReportSection.HEALTH_SCORE !in sections) return

        json.addObject("healthScore") {
            addNumber("overallScore", analysis.healthScore.overallScore)
            addString("overallHealth", analysis.healthScore.overallHealth.name)
            addObject("dimensions") {
                addNumber("rfQuality", analysis.healthScore.rfQualityScore)
                addNumber("connectivity", analysis.healthScore.connectivityScore)
                addNumber("throughput", analysis.healthScore.throughputScore)
                addNumber("routing", analysis.healthScore.routingScore)
                addNumber("dns", analysis.healthScore.dnsScore)
                addNumber("security", analysis.healthScore.securityScore)
            }
            if (analysis.healthScore.strengths.isNotEmpty()) {
                addArray("strengths") {
                    analysis.healthScore.strengths.forEach { addString(it.name) }
                }
            }
            if (analysis.healthScore.weaknesses.isNotEmpty()) {
                addArray("weaknesses") {
                    analysis.healthScore.weaknesses.forEach { addString(it.name) }
                }
            }
            analysis.healthScore.primaryConcern?.let { addString("primaryConcern", it) }
        }
    }

    private fun addActiveDiagnostics(json: JsonBuilder) {
        if (ReportSection.ACTIVE_DIAGNOSTICS !in sections) return

        json.addObject("activeDiagnostics") {
            addActiveDiagnosticsJson(analysis.activeDiagnostics)
        }
    }

    private fun JsonBuilder.addActiveDiagnosticsJson(diag: ActiveDiagnostics) {
        addString("connectivityQuality", diag.connectivityQuality.name)
        addString("throughputQuality", diag.throughputQuality.name)
        addString("routingQuality", diag.routingQuality.name)
        addString("dnsQuality", diag.dnsQuality.name)

        if (diag.pingTests.isNotEmpty()) {
            addArray("pingResults") {
                diag.pingTests.forEach { ping ->
                    addObject {
                        addString("targetHost", ping.targetHost)
                        addString("quality", ping.overallQuality.name)
                        addNumber("packetLossPercent", ping.packetLossPercent)
                        ping.avgRtt?.let { addNumber("avgRttMs", it.inWholeMilliseconds.toDouble()) }
                        ping.minRtt?.let { addNumber("minRttMs", it.inWholeMilliseconds.toDouble()) }
                        ping.maxRtt?.let { addNumber("maxRttMs", it.inWholeMilliseconds.toDouble()) }
                    }
                }
            }
        }

        if (diag.tracerouteResults.isNotEmpty()) {
            addArray("tracerouteResults") {
                diag.tracerouteResults.forEach { trace ->
                    addObject {
                        addString("targetHost", trace.targetHost)
                        addString("routeQuality", trace.routeQuality.name)
                        addNumber("hopCount", trace.hops.size)
                        val bottlenecks = trace.findBottlenecks()
                        if (bottlenecks.isNotEmpty()) {
                            addArray("bottleneckHops") {
                                bottlenecks.forEach { addNumber(it) }
                            }
                        }
                    }
                }
            }
        }

        if (diag.bandwidthTests.isNotEmpty()) {
            addArray("bandwidthResults") {
                diag.bandwidthTests.forEach { bw ->
                    addObject {
                        addString("serverHost", bw.serverHost)
                        addString("quality", bw.overallQuality.name)
                        bw.downloadMbps?.let { addNumber("downloadMbps", it) }
                        bw.uploadMbps?.let { addNumber("uploadMbps", it) }
                    }
                }
            }
        }

        if (diag.dnsTests.isNotEmpty()) {
            addArray("dnsResults") {
                diag.dnsTests.forEach { dns ->
                    addObject {
                        addString("hostname", dns.hostname)
                        addString("resolutionQuality", dns.resolutionQuality.name)
                        addNumber("resolutionTimeMs", dns.resolutionTime.inWholeMilliseconds.toDouble())
                        addArray("resolvedAddresses") {
                            dns.resolvedAddresses.forEach { addString(it) }
                        }
                    }
                }
            }
        }
    }

    private fun addPassiveAnalysis(json: JsonBuilder) {
        if (ReportSection.PASSIVE_ANALYSIS !in sections) return

        json.addObject("passiveAnalysis") {
            analysis.rfQualityScore?.let { addNumber("rfQuality", it) }
            analysis.topologyQualityScore?.let { addNumber("topologyQuality", it) }
            analysis.securityScore?.let { addNumber("security", it) }
        }
    }

    private fun addCorrelations(json: JsonBuilder) {
        if (ReportSection.CORRELATIONS !in sections || analysis.correlations.isEmpty()) return

        json.addArray("correlations") {
            analysis.correlations.forEach { correlation ->
                addObject {
                    addString("finding", correlation.finding)
                    addString("rootCause", correlation.rootCause)
                    addString("recommendation", correlation.recommendation)
                }
            }
        }
    }

    private fun addRecommendations(json: JsonBuilder) {
        if (ReportSection.RECOMMENDATIONS !in sections) return

        json.addObject("recommendations") {
            addBoolean("hasCriticalIssues", analysis.recommendations.hasCriticalIssues)

            if (analysis.recommendations.critical.isNotEmpty()) {
                addArray("critical") {
                    analysis.recommendations.critical.forEach { addRecommendationJson(it) }
                }
            }

            if (analysis.recommendations.performance.isNotEmpty()) {
                addArray("performance") {
                    analysis.recommendations.performance.forEach { addRecommendationJson(it) }
                }
            }

            if (analysis.recommendations.configuration.isNotEmpty()) {
                addArray("configuration") {
                    analysis.recommendations.configuration.forEach { addRecommendationJson(it) }
                }
            }

            if (analysis.recommendations.security.isNotEmpty()) {
                addArray("security") {
                    analysis.recommendations.security.forEach { addRecommendationJson(it) }
                }
            }

            if (analysis.recommendations.monitoring.isNotEmpty()) {
                addArray("monitoring") {
                    analysis.recommendations.monitoring.forEach { addRecommendationJson(it) }
                }
            }

            if (analysis.recommendations.quickWins.isNotEmpty()) {
                addArray("quickWins") {
                    analysis.recommendations.quickWins.forEach { addRecommendationJson(it) }
                }
            }
        }
    }

    private fun JsonArrayBuilder.addRecommendationJson(rec: Recommendation) {
        addObject {
            addString("issue", rec.issue)
            addString("action", rec.action)
            addString("category", rec.category.name)
            addString("impact", rec.impact.name)
            addString("effort", rec.effort.name)
        }
    }
}

/**
 * Simple JSON builder utility.
 *
 * Provides basic JSON construction without external dependencies.
 */
internal class JsonBuilder {
    private val builder = StringBuilder()
    internal var needsComma = false
    internal var indentLevel = 0

    fun startObject() {
        builder.append("{")
        indentLevel++
        needsComma = false
    }

    fun endObject() {
        indentLevel--
        builder.append("\n${indent()}}")
        needsComma = true
    }

    fun addString(
        key: String,
        value: String,
    ) {
        comma()
        builder.append("\n${indent()}\"$key\": \"${escape(value)}\"")
        needsComma = true
    }

    fun addNumber(
        key: String,
        value: Number,
    ) {
        comma()
        builder.append("\n${indent()}\"$key\": $value")
        needsComma = true
    }

    fun addBoolean(
        key: String,
        value: Boolean,
    ) {
        comma()
        builder.append("\n${indent()}\"$key\": $value")
        needsComma = true
    }

    fun addObject(
        key: String,
        block: JsonBuilder.() -> Unit,
    ) {
        comma()
        builder.append("\n${indent()}\"$key\": {")
        indentLevel++
        val oldComma = needsComma
        needsComma = false
        block()
        indentLevel--
        builder.append("\n${indent()}}")
        needsComma = true
    }

    fun addArray(
        key: String,
        block: JsonArrayBuilder.() -> Unit,
    ) {
        comma()
        builder.append("\n${indent()}\"$key\": [")
        indentLevel++
        val arrayBuilder = JsonArrayBuilder(builder, indentLevel)
        arrayBuilder.block()
        indentLevel--
        builder.append("\n${indent()}]")
        needsComma = true
    }

    private fun comma() {
        if (needsComma) {
            builder.append(",")
        }
    }

    private fun indent(): String = "  ".repeat(indentLevel)

    private fun escape(str: String): String =
        str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    override fun toString(): String = builder.toString()
}

/**
 * JSON array builder.
 */
internal class JsonArrayBuilder(
    private val builder: StringBuilder,
    private val indentLevel: Int,
) {
    private var needsComma = false

    fun addString(value: String) {
        comma()
        builder.append("\n${indent()}\"${escape(value)}\"")
        needsComma = true
    }

    fun addNumber(value: Number) {
        comma()
        builder.append("\n${indent()}$value")
        needsComma = true
    }

    fun addObject(block: JsonBuilder.() -> Unit) {
        comma()
        builder.append("\n${indent()}{")
        val jsonBuilder = JsonBuilder()
        jsonBuilder.indentLevel = indentLevel + 1
        jsonBuilder.needsComma = false
        jsonBuilder.block()
        builder.append("\n${indent()}}")
        needsComma = true
    }

    private fun comma() {
        if (needsComma) {
            builder.append(",")
        }
    }

    private fun indent(): String = "  ".repeat(indentLevel)

    private fun escape(str: String): String =
        str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
