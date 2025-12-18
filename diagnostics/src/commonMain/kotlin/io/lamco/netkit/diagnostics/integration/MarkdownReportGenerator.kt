package io.lamco.netkit.diagnostics.integration

import io.lamco.netkit.diagnostics.model.*

/**
 * Markdown report generator for WiFi Intelligence diagnostics.
 *
 * Generates GitHub-Flavored Markdown reports with proper formatting,
 * tables, and structure for comprehensive network analysis results.
 *
 * @since 1.0.0
 */
internal class MarkdownReportGenerator(
    private val analysis: IntegratedAnalysis,
    private val sections: Set<ReportSection>,
    private val title: String?,
    private val includeTimestamp: Boolean,
    private val includeMetadata: Boolean,
) {
    /**
     * Generate complete Markdown report.
     *
     * @return GitHub-Flavored Markdown string
     */
    fun generate(): String =
        buildString {
            renderTitle()
            renderTimestamp()
            renderExecutiveSummary()
            renderHealthScore()
            renderActiveDiagnostics()
            renderPassiveAnalysis()
            renderCorrelations()
            renderRecommendations()
            renderTechnicalDetails()
            renderMetadata()
        }

    private fun StringBuilder.renderTitle() {
        if (title != null) {
            appendLine("# $title")
            appendLine()
        }
    }

    private fun StringBuilder.renderTimestamp() {
        if (includeTimestamp) {
            appendLine("**Generated:** ${ReportFormatters.formatTimestamp(analysis.timestamp)}")
            appendLine()
        }
    }

    private fun StringBuilder.renderExecutiveSummary() {
        if (ReportSection.EXECUTIVE_SUMMARY !in sections) return

        appendLine("## Executive Summary")
        appendLine()
        analysis.location?.let { appendLine("**Location:** $it  ") }
        appendLine(
            "**Overall Health:** ${analysis.healthScore.overallHealth.emoji} ${analysis.healthScore.overallHealth} (${analysis.healthScore.overallScore.toInt()}/100)  ",
        )
        appendLine()

        if (analysis.correlations.isNotEmpty()) {
            appendLine("### Key Findings")
            appendLine()
            analysis.correlations.forEach {
                appendLine("- ${it.finding}")
            }
            appendLine()
        }

        if (analysis.recommendations.hasCriticalIssues) {
            appendLine("### Critical Issues")
            appendLine()
            appendLine(
                "⚠️  **${analysis.recommendations.critical.size} CRITICAL issue(s) require immediate attention**",
            )
            appendLine()
        }

        if (analysis.recommendations.quickWins.isNotEmpty()) {
            appendLine("### Quick Wins")
            appendLine()
            appendLine(
                "💡 **${analysis.recommendations.quickWins.size} quick win(s) available** (high impact, low effort)",
            )
            appendLine()
        }
    }

    private fun StringBuilder.renderHealthScore() {
        if (ReportSection.HEALTH_SCORE !in sections) return

        appendLine("## Network Health Score")
        appendLine()
        appendLine("| Dimension | Score | Status |")
        appendLine("|-----------|-------|--------|")

        val scores = analysis.healthScore.dimensionScores()
        scores.forEach { (dimension, score) ->
            val status =
                when {
                    score >= 85.0 -> "✅ Excellent"
                    score >= 75.0 -> "🟢 Good"
                    score >= 60.0 -> "🟡 Fair"
                    score >= 40.0 -> "🟠 Poor"
                    else -> "🔴 Critical"
                }
            appendLine("| $dimension | ${score.toInt()}/100 | $status |")
        }
        appendLine()

        if (analysis.healthScore.strengths.isNotEmpty()) {
            appendLine("**Strengths:**")
            analysis.healthScore.strengths.forEach {
                appendLine("- ✓ ${it.description}")
            }
            appendLine()
        }

        if (analysis.healthScore.weaknesses.isNotEmpty()) {
            appendLine("**Weaknesses:**")
            analysis.healthScore.weaknesses.forEach {
                appendLine("- ✗ ${it.description}")
            }
            appendLine()
        }

        analysis.healthScore.primaryConcern?.let {
            appendLine("**Primary Concern:** $it")
            appendLine()
        }
    }

    private fun StringBuilder.renderActiveDiagnostics() {
        if (ReportSection.ACTIVE_DIAGNOSTICS !in sections) return

        appendLine("## Active Diagnostics")
        appendLine()

        val diag = analysis.activeDiagnostics

        appendLine("| Metric | Quality |")
        appendLine("|--------|---------|")
        appendLine("| Connectivity | ${diag.connectivityQuality} |")
        appendLine("| Throughput | ${diag.throughputQuality} |")
        appendLine("| Routing | ${diag.routingQuality} |")
        appendLine("| DNS | ${diag.dnsQuality} |")
        appendLine()

        renderPingTests(diag)
        renderTracerouteTests(diag)
        renderBandwidthTests(diag)
        renderDnsTests(diag)
    }

    private fun StringBuilder.renderPingTests(diag: ActiveDiagnostics) {
        if (diag.pingTests.isEmpty()) return

        appendLine("### Ping Tests")
        appendLine()
        appendLine("| Target | Quality | Packet Loss | Avg RTT |")
        appendLine("|--------|---------|-------------|---------|")
        diag.pingTests.forEach { ping ->
            val rtt = ping.avgRtt?.let { ReportFormatters.formatDuration(it) } ?: "N/A"
            appendLine("| ${ping.targetHost} | ${ping.overallQuality} | ${ping.packetLossPercent}% | $rtt |")
        }
        appendLine()
    }

    private fun StringBuilder.renderTracerouteTests(diag: ActiveDiagnostics) {
        if (diag.tracerouteResults.isEmpty()) return

        appendLine("### Traceroute Tests")
        appendLine()
        appendLine("| Target | Quality | Hops | Bottleneck |")
        appendLine("|--------|---------|------|------------|")
        diag.tracerouteResults.forEach { trace ->
            val bottlenecks = trace.findBottlenecks()
            val bottleneck = if (bottlenecks.isNotEmpty()) "Hops ${bottlenecks.joinToString(", ")}" else "None"
            appendLine("| ${trace.targetHost} | ${trace.routeQuality} | ${trace.hops.size} | $bottleneck |")
        }
        appendLine()
    }

    private fun StringBuilder.renderBandwidthTests(diag: ActiveDiagnostics) {
        if (diag.bandwidthTests.isEmpty()) return

        appendLine("### Bandwidth Tests")
        appendLine()
        appendLine("| Server | Quality | Download | Upload |")
        appendLine("|--------|---------|----------|--------|")
        diag.bandwidthTests.forEach { bw ->
            val dl = bw.downloadMbps?.let { "${"%.1f".format(it)} Mbps" } ?: "N/A"
            val ul = bw.uploadMbps?.let { "${"%.1f".format(it)} Mbps" } ?: "N/A"
            appendLine("| ${bw.serverHost} | ${bw.overallQuality} | $dl | $ul |")
        }
        appendLine()
    }

    private fun StringBuilder.renderDnsTests(diag: ActiveDiagnostics) {
        if (diag.dnsTests.isEmpty()) return

        appendLine("### DNS Tests")
        appendLine()
        appendLine("| Hostname | Quality | Resolution Time | Addresses |")
        appendLine("|----------|---------|-----------------|-----------|")
        diag.dnsTests.forEach { dns ->
            appendLine(
                "| ${dns.hostname} | ${dns.resolutionQuality} | ${
                    ReportFormatters.formatDuration(
                        dns.resolutionTime,
                    )
                } | ${dns.resolvedAddresses.size} |",
            )
        }
        appendLine()
    }

    private fun StringBuilder.renderPassiveAnalysis() {
        if (ReportSection.PASSIVE_ANALYSIS !in sections) return

        appendLine("## Passive Analysis")
        appendLine()

        val hasData =
            analysis.rfQualityScore != null ||
                analysis.topologyQualityScore != null ||
                analysis.securityScore != null

        if (hasData) {
            appendLine("| Metric | Score |")
            appendLine("|--------|-------|")

            analysis.rfQualityScore?.let {
                appendLine("| RF Quality | ${it.toInt()}/100 |")
            }

            analysis.topologyQualityScore?.let {
                appendLine("| Topology Quality | ${it.toInt()}/100 |")
            }

            analysis.securityScore?.let {
                appendLine("| Security | ${it.toInt()}/100 |")
            }
            appendLine()
        } else {
            appendLine("_No passive analysis data available_")
            appendLine()
        }
    }

    private fun StringBuilder.renderCorrelations() {
        if (ReportSection.CORRELATIONS !in sections || analysis.correlations.isEmpty()) return

        appendLine("## Root Cause Analysis")
        appendLine()

        analysis.correlations.forEach { correlation ->
            appendLine("### ${correlation.finding}")
            appendLine()
            appendLine("**Root Cause:** ${correlation.rootCause}")
            appendLine()
            appendLine("**Recommended Action:** ${correlation.recommendation}")
            appendLine()
        }
    }

    private fun StringBuilder.renderRecommendations() {
        if (ReportSection.RECOMMENDATIONS !in sections) return

        appendLine("## Recommendations")
        appendLine()

        val recs = analysis.recommendations

        if (recs.critical.isNotEmpty()) {
            appendLine("### 🚨 Critical (Immediate Action Required)")
            appendLine()
            recs.critical.forEach {
                appendLine("#### ${it.issue}")
                appendLine()
                appendLine(it.action)
                appendLine()
                appendLine("- **Impact:** ${it.impact}")
                appendLine("- **Effort:** ${it.effort}")
                appendLine()
            }
        }

        if (recs.quickWins.isNotEmpty()) {
            appendLine("### 💡 Quick Wins (High Impact, Low Effort)")
            appendLine()
            recs.quickWins.forEach {
                appendLine("#### ${it.issue}")
                appendLine()
                appendLine(it.action)
                appendLine()
            }
        }

        if (recs.performance.isNotEmpty()) {
            appendLine("### ⚡ Performance Improvements")
            appendLine()
            recs.performance.forEach {
                appendLine("- **${it.issue}** (Impact: ${it.impact}, Effort: ${it.effort})")
                appendLine("  ${it.action}")
                appendLine()
            }
        }

        if (recs.configuration.isNotEmpty()) {
            appendLine("### ⚙️ Configuration Adjustments")
            appendLine()
            recs.configuration.forEach {
                appendLine("- **${it.issue}**")
                appendLine("  ${it.action}")
                appendLine()
            }
        }

        if (recs.security.isNotEmpty()) {
            appendLine("### 🔒 Security Enhancements")
            appendLine()
            recs.security.forEach {
                appendLine("- **${it.issue}**")
                appendLine("  ${it.action}")
                appendLine()
            }
        }

        if (recs.monitoring.isNotEmpty()) {
            appendLine("### 📊 Monitoring & Maintenance")
            appendLine()
            recs.monitoring.forEach {
                appendLine("- **${it.issue}**")
                appendLine("  ${it.action}")
                appendLine()
            }
        }
    }

    private fun StringBuilder.renderTechnicalDetails() {
        if (ReportSection.TECHNICAL_DETAILS !in sections) return

        appendLine("## Technical Details")
        appendLine()
        appendLine("```")
        appendLine(analysis.activeDiagnostics.detailedReport())
        appendLine("```")
        appendLine()
    }

    private fun StringBuilder.renderMetadata() {
        if (!includeMetadata) return

        appendLine("---")
        appendLine()
        appendLine("_Generated by WiFi Intelligence Diagnostics v1.0.0_  ")
        appendLine("_Timestamp: ${ReportFormatters.formatTimestamp(analysis.timestamp, includeMillis = true)}_")
    }
}
