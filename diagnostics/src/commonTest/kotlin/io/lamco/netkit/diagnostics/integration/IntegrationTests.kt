package io.lamco.netkit.diagnostics.integration

import io.lamco.netkit.diagnostics.model.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive tests for Phase 4 Batch 3 - Integration & Reporting.
 *
 * Test Coverage:
 * - NetworkHealthScore: Health scoring, dimension analysis, categorization
 * - DiagnosticAdvisor: Recommendation generation, categorization, prioritization
 * - SiteSurveyIntegrator: Correlation analysis, anomaly detection, integration
 * - ComprehensiveReport: Report generation, multiple formats, sections
 *
 * Target: 80+ tests
 */
class IntegrationTests {
    // ========================================================================
    // NetworkHealthScore Tests (~25 tests)
    // ========================================================================

    @Nested
    inner class NetworkHealthScoreTests {
        @Test
        fun `calculate with all dimensions returns correct weighted score`() {
            val score =
                NetworkHealthScore.calculate(
                    rfQuality = 90.0,
                    connectivityScore = 85.0,
                    throughputScore = 80.0,
                    routingScore = 75.0,
                    dnsScore = 70.0,
                    securityScore = 65.0,
                )

            // Weighted: RF(25%) + Conn(25%) + TP(20%) + Route(10%) + DNS(10%) + Sec(10%)
            // = 90*0.25 + 85*0.25 + 80*0.20 + 75*0.10 + 70*0.10 + 65*0.10
            // = 22.5 + 21.25 + 16 + 7.5 + 7 + 6.5 = 80.75
            assertTrue(score.overallScore in 80.0..81.0, "Expected ~80.75, got ${score.overallScore}")
        }

        @Test
        fun `calculate with missing dimensions adjusts weights correctly`() {
            val score =
                NetworkHealthScore.calculate(
                    rfQuality = null, // 25% missing
                    connectivityScore = 80.0,
                    throughputScore = 80.0,
                    routingScore = null, // 10% missing
                    dnsScore = null, // 10% missing
                    securityScore = null, // 10% missing
                )

            // Only connectivity (25%) and throughput (20%) available = 45% total weight
            // Normalized: 80*0.25 + 80*0.20 / 0.45 = 36/0.45 = 80
            assertEquals(80.0, score.overallScore, 0.1)
        }

        @Test
        fun `health category EXCELLENT for score 90+`() {
            val score =
                NetworkHealthScore.calculate(
                    rfQuality = 95.0,
                    connectivityScore = 95.0,
                    throughputScore = 95.0,
                )

            assertEquals(NetworkHealth.EXCELLENT, score.overallHealth)
            assertTrue(score.isHealthy)
            assertFalse(score.requiresImmediateAction)
        }

        @Test
        fun `health category GOOD for score 75-89`() {
            val score =
                NetworkHealthScore.calculate(
                    connectivityScore = 80.0,
                    throughputScore = 75.0,
                )

            assertEquals(NetworkHealth.GOOD, score.overallHealth)
            assertTrue(score.isHealthy)
        }

        @Test
        fun `health category FAIR for score 60-74`() {
            val score =
                NetworkHealthScore.calculate(
                    connectivityScore = 65.0,
                    throughputScore = 60.0,
                )

            assertEquals(NetworkHealth.FAIR, score.overallHealth)
            assertTrue(score.isHealthy) // Still healthy at 60+
        }

        @Test
        fun `health category POOR for score 40-59`() {
            val score =
                NetworkHealthScore.calculate(
                    connectivityScore = 50.0,
                    throughputScore = 45.0,
                )

            assertEquals(NetworkHealth.POOR, score.overallHealth)
            assertFalse(score.isHealthy)
        }

        @Test
        fun `health category CRITICAL for score below 40`() {
            val score =
                NetworkHealthScore.calculate(
                    connectivityScore = 30.0,
                    throughputScore = 20.0,
                )

            assertEquals(NetworkHealth.CRITICAL, score.overallHealth)
            assertFalse(score.isHealthy)
            assertTrue(score.requiresImmediateAction)
        }

        @Test
        fun `identifies strengths correctly for high scores`() {
            val score =
                NetworkHealthScore.calculate(
                    rfQuality = 90.0, // Strength (>= 85)
                    connectivityScore = 88.0, // Strength
                    throughputScore = 70.0, // Not strength
                    routingScore = 86.0, // Strength
                    dnsScore = 50.0, // Not strength
                )

            assertEquals(3, score.strengths.size)
            assertTrue(HealthDimension.RF_QUALITY in score.strengths)
            assertTrue(HealthDimension.CONNECTIVITY in score.strengths)
            assertTrue(HealthDimension.ROUTING in score.strengths)
        }

        @Test
        fun `identifies weaknesses correctly for low scores`() {
            val score =
                NetworkHealthScore.calculate(
                    rfQuality = 55.0, // Weakness (< 60)
                    connectivityScore = 45.0, // Weakness
                    throughputScore = 70.0, // Not weakness
                    routingScore = 50.0, // Weakness
                    dnsScore = 65.0, // Not weakness
                )

            assertEquals(3, score.weaknesses.size)
            assertTrue(HealthDimension.RF_QUALITY in score.weaknesses)
            assertTrue(HealthDimension.CONNECTIVITY in score.weaknesses)
            assertTrue(HealthDimension.ROUTING in score.weaknesses)
        }

        @Test
        fun `primary concern identifies lowest scoring dimension`() {
            val score =
                NetworkHealthScore.calculate(
                    rfQuality = 70.0,
                    connectivityScore = 50.0,
                    throughputScore = 30.0, // Lowest
                    routingScore = 60.0,
                )

            assertNotNull(score.primaryConcern)
            assertContains(score.primaryConcern!!, "Throughput")
            assertContains(score.primaryConcern!!, "30")
        }

        @Test
        fun `primary concern is null when no weaknesses`() {
            val score =
                NetworkHealthScore.calculate(
                    rfQuality = 90.0,
                    connectivityScore = 85.0,
                    throughputScore = 95.0,
                )

            assertNull(score.primaryConcern)
        }

        @Test
        fun `dimensionScores returns all dimension scores`() {
            val score =
                NetworkHealthScore.calculate(
                    rfQuality = 90.0,
                    connectivityScore = 85.0,
                    throughputScore = 80.0,
                    routingScore = 75.0,
                    dnsScore = 70.0,
                    securityScore = 65.0,
                )

            val dimensions = score.dimensionScores()
            assertEquals(6, dimensions.size)
            assertEquals(90.0, dimensions["RF Quality"])
            assertEquals(85.0, dimensions["Connectivity"])
            assertEquals(80.0, dimensions["Throughput"])
            assertEquals(75.0, dimensions["Routing"])
            assertEquals(70.0, dimensions["DNS"])
            assertEquals(65.0, dimensions["Security"])
        }

        @Test
        fun `weakestDimension identifies minimum score`() {
            val score =
                NetworkHealthScore.calculate(
                    rfQuality = 90.0,
                    connectivityScore = 40.0, // Weakest
                    throughputScore = 80.0,
                )

            val (dimension, value) = score.weakestDimension()
            assertEquals("Connectivity", dimension)
            assertEquals(40.0, value)
        }

        @Test
        fun `strongestDimension identifies maximum score`() {
            val score =
                NetworkHealthScore.calculate(
                    rfQuality = 95.0, // Strongest
                    connectivityScore = 80.0,
                    throughputScore = 75.0,
                )

            val (dimension, value) = score.strongestDimension()
            assertEquals("RF Quality", dimension)
            assertEquals(95.0, value)
        }

        @Test
        fun `summary generates readable report`() {
            val score =
                NetworkHealthScore.calculate(
                    rfQuality = 85.0,
                    connectivityScore = 80.0,
                    throughputScore = 75.0,
                )

            val summary = score.summary()
            assertContains(summary, "Network Health Assessment")
            assertContains(summary, "Overall Score")
            assertContains(summary, "RF Quality")
            assertContains(summary, "Connectivity")
            assertContains(summary, "Throughput")
        }

        @Test
        fun `fromActiveDiagnostics creates score from diagnostics`() {
            val diagnostics =
                ActiveDiagnostics(
                    pingTests =
                        listOf(
                            PingTest(
                                targetHost = "8.8.8.8",
                                packetsTransmitted = 10,
                                packetsReceived = 10,
                                packetLossPercent = 0.0,
                                minRtt = 10.milliseconds,
                                avgRtt = 15.milliseconds,
                                maxRtt = 20.milliseconds,
                                stdDevRtt = 2.milliseconds,
                            ),
                        ),
                    testDuration = 30.seconds,
                )

            val score = NetworkHealthScore.fromActiveDiagnostics(diagnostics)
            assertNotNull(score)
            assertTrue(score.connectivityScore > 0)
        }

        @Test
        fun `validation rejects invalid overall score`() {
            assertThrows<IllegalArgumentException> {
                NetworkHealthScore(
                    overallScore = 150.0, // Invalid
                    overallHealth = NetworkHealth.EXCELLENT,
                    rfQualityScore = 90.0,
                    connectivityScore = 90.0,
                    throughputScore = 90.0,
                    routingScore = 90.0,
                    dnsScore = 90.0,
                    securityScore = 90.0,
                )
            }
        }

        @Test
        fun `validation rejects negative scores`() {
            assertThrows<IllegalArgumentException> {
                NetworkHealthScore(
                    overallScore = 80.0,
                    overallHealth = NetworkHealth.GOOD,
                    rfQualityScore = -10.0, // Invalid
                    connectivityScore = 90.0,
                    throughputScore = 90.0,
                    routingScore = 90.0,
                    dnsScore = 90.0,
                    securityScore = 90.0,
                )
            }
        }

        @Test
        fun `NetworkHealth enum has correct descriptions`() {
            assertEquals("Excellent network health - all systems optimal", NetworkHealth.EXCELLENT.description)
            assertEquals("Critical network issues - immediate attention required", NetworkHealth.CRITICAL.description)
        }

        @Test
        fun `NetworkHealth enum has correct emojis`() {
            assertEquals("🟢", NetworkHealth.EXCELLENT.emoji)
            assertEquals("⛔", NetworkHealth.CRITICAL.emoji)
        }

        @Test
        fun `HealthDimension has correct descriptions`() {
            assertEquals("Strong RF signal quality", HealthDimension.RF_QUALITY.description)
            assertEquals("Fast DNS resolution", HealthDimension.DNS.description)
        }

        @Test
        fun `zero scores are handled correctly`() {
            val score =
                NetworkHealthScore.calculate(
                    connectivityScore = 0.0,
                    throughputScore = 0.0,
                )

            assertEquals(0.0, score.overallScore)
            assertEquals(NetworkHealth.CRITICAL, score.overallHealth)
        }

        @Test
        fun `perfect scores result in excellent health`() {
            val score =
                NetworkHealthScore.calculate(
                    rfQuality = 100.0,
                    connectivityScore = 100.0,
                    throughputScore = 100.0,
                    routingScore = 100.0,
                    dnsScore = 100.0,
                    securityScore = 100.0,
                )

            assertEquals(100.0, score.overallScore)
            assertEquals(NetworkHealth.EXCELLENT, score.overallHealth)
            assertEquals(6, score.strengths.size)
            assertEquals(0, score.weaknesses.size)
        }

        @Test
        fun `edge case score at 60 is healthy`() {
            val score =
                NetworkHealthScore.calculate(
                    connectivityScore = 60.0,
                    throughputScore = 60.0,
                )

            assertEquals(60.0, score.overallScore)
            assertEquals(NetworkHealth.FAIR, score.overallHealth)
            assertTrue(score.isHealthy)
        }

        @Test
        fun `edge case score at 59 is not healthy`() {
            val score =
                NetworkHealthScore.calculate(
                    connectivityScore = 59.0,
                    throughputScore = 59.0,
                )

            assertEquals(59.0, score.overallScore)
            assertEquals(NetworkHealth.POOR, score.overallHealth)
            assertFalse(score.isHealthy)
        }
    }

    // ========================================================================
    // SiteSurveyIntegrator Tests (~30 tests)
    // ========================================================================

    @Nested
    inner class SiteSurveyIntegratorTests {
        private fun createGoodDiagnostics() =
            ActiveDiagnostics(
                pingTests =
                    listOf(
                        PingTest(
                            targetHost = "8.8.8.8",
                            packetsTransmitted = 10,
                            packetsReceived = 10,
                            packetLossPercent = 0.0,
                            minRtt = 10.milliseconds,
                            avgRtt = 15.milliseconds,
                            maxRtt = 20.milliseconds,
                            stdDevRtt = 2.milliseconds,
                        ),
                    ),
                bandwidthTests =
                    listOf(
                        BandwidthTest(
                            downloadMbps = 150.0,
                            uploadMbps = 75.0,
                            downloadBytes = 187_500_000,
                            uploadBytes = 93_750_000,
                            testDuration = 10.seconds,
                            serverHost = "speedtest.net",
                        ),
                    ),
                testDuration = 30.seconds,
            )

        private fun createPoorDiagnostics() =
            ActiveDiagnostics(
                pingTests =
                    listOf(
                        PingTest(
                            targetHost = "8.8.8.8",
                            packetsTransmitted = 10,
                            packetsReceived = 5,
                            packetLossPercent = 50.0,
                            minRtt = 100.milliseconds,
                            avgRtt = 200.milliseconds,
                            maxRtt = 300.milliseconds,
                            stdDevRtt = 50.milliseconds,
                        ),
                    ),
                bandwidthTests =
                    listOf(
                        BandwidthTest(
                            downloadMbps = 10.0,
                            uploadMbps = 2.0,
                            downloadBytes = 12_500_000,
                            uploadBytes = 2_500_000,
                            testDuration = 10.seconds,
                            serverHost = "speedtest.net",
                        ),
                    ),
                testDuration = 30.seconds,
            )

        @Test
        fun `integrate combines all data sources correctly`() {
            val integrator = SiteSurveyIntegrator()
            val diagnostics = createGoodDiagnostics()

            val result =
                integrator.integrate(
                    activeDiagnostics = diagnostics,
                    rfQuality = 85.0,
                    topologyQuality = 90.0,
                    securityScore = 80.0,
                    location = "Building A",
                )

            assertNotNull(result.healthScore)
            assertNotNull(result.activeDiagnostics)
            assertNotNull(result.recommendations)
            assertEquals("Building A", result.location)
            assertEquals(85.0, result.rfQualityScore)
            assertEquals(90.0, result.topologyQualityScore)
            assertEquals(80.0, result.securityScore)
        }

        @Test
        fun `RF correlation - low RF and high packet loss detected`() {
            val integrator = SiteSurveyIntegrator(correlationThreshold = 0.5)
            val diagnostics = createPoorDiagnostics()

            val result =
                integrator.integrate(
                    activeDiagnostics = diagnostics,
                    rfQuality = 45.0, // Low RF
                )

            // Should detect RF-packet loss correlation
            val rfCorrelations =
                result.correlations.filter {
                    it.category == CorrelationCategory.RF_CONNECTIVITY &&
                        it.finding.contains("Packet loss")
                }
            assertTrue(rfCorrelations.isNotEmpty(), "Expected RF-packet loss correlation")
        }

        @Test
        fun `RF correlation - good RF but poor throughput indicates ISP issue`() {
            val integrator = SiteSurveyIntegrator(correlationThreshold = 0.5)
            val poorBandwidth =
                ActiveDiagnostics(
                    bandwidthTests =
                        listOf(
                            BandwidthTest(
                                downloadMbps = 15.0, // Low
                                uploadMbps = 3.0,
                                downloadBytes = 18_750_000,
                                uploadBytes = 3_750_000,
                                testDuration = 10.seconds,
                                serverHost = "speedtest.net",
                            ),
                        ),
                    testDuration = 30.seconds,
                )

            val result =
                integrator.integrate(
                    activeDiagnostics = poorBandwidth,
                    rfQuality = 85.0, // Good RF
                )

            val ispCorrelations =
                result.correlations.filter {
                    it.category == CorrelationCategory.BANDWIDTH_ISP &&
                        it.finding.contains("Low bandwidth")
                }
            assertTrue(ispCorrelations.isNotEmpty(), "Expected ISP bandwidth correlation")
        }

        @Test
        fun `Security correlation - poor security with good performance`() {
            val integrator = SiteSurveyIntegrator(correlationThreshold = 0.5)
            val diagnostics = createGoodDiagnostics()

            val result =
                integrator.integrate(
                    activeDiagnostics = diagnostics,
                    securityScore = 45.0, // Poor security
                )

            val securityCorrelations =
                result.correlations.filter {
                    it.category == CorrelationCategory.SECURITY_CONFIGURATION &&
                        it.type == CorrelationType.CONFLICTING
                }
            assertTrue(securityCorrelations.isNotEmpty(), "Expected security-performance conflict")
        }

        @Test
        fun `Latency correlation - high latency with high jitter`() {
            val integrator = SiteSurveyIntegrator(correlationThreshold = 0.5)
            val diagnostics =
                ActiveDiagnostics(
                    pingTests =
                        listOf(
                            PingTest(
                                targetHost = "8.8.8.8",
                                packetsTransmitted = 10,
                                packetsReceived = 10,
                                packetLossPercent = 0.0,
                                minRtt = 50.milliseconds,
                                avgRtt = 80.milliseconds, // High
                                maxRtt = 120.milliseconds,
                                stdDevRtt = 20.milliseconds, // High jitter
                            ),
                        ),
                    testDuration = 30.seconds,
                )

            val result = integrator.integrate(activeDiagnostics = diagnostics)

            val latencyCorrelations =
                result.correlations.filter {
                    it.category == CorrelationCategory.LATENCY_ROUTING &&
                        it.finding.contains("latency")
                }
            assertTrue(latencyCorrelations.isNotEmpty())
        }

        @Test
        fun `DNS correlation - slow DNS with good connectivity`() {
            val integrator = SiteSurveyIntegrator(correlationThreshold = 0.5)
            val diagnostics =
                ActiveDiagnostics(
                    pingTests =
                        listOf(
                            PingTest(
                                targetHost = "8.8.8.8",
                                packetsTransmitted = 10,
                                packetsReceived = 10,
                                packetLossPercent = 0.0,
                                minRtt = 10.milliseconds,
                                avgRtt = 15.milliseconds, // Good
                                maxRtt = 20.milliseconds,
                                stdDevRtt = 2.milliseconds,
                            ),
                        ),
                    dnsTests =
                        listOf(
                            DnsTest(
                                hostname = "example.com",
                                recordType = DnsRecordType.A,
                                resolvedAddresses = listOf("93.184.216.34"),
                                resolutionTime = 100.milliseconds, // Slow
                                dnsServer = "8.8.8.8",
                            ),
                        ),
                    testDuration = 30.seconds,
                )

            val result = integrator.integrate(activeDiagnostics = diagnostics)

            val dnsCorrelations =
                result.correlations.filter {
                    it.category == CorrelationCategory.DNS_SERVER &&
                        it.finding.contains("Slow DNS")
                }
            assertTrue(dnsCorrelations.isNotEmpty())
        }

        @Test
        fun `Routing correlation - bottleneck detected`() {
            val integrator = SiteSurveyIntegrator(correlationThreshold = 0.5)
            val diagnostics =
                ActiveDiagnostics(
                    tracerouteResults =
                        listOf(
                            TracerouteResult(
                                targetHost = "example.com",
                                hops =
                                    listOf(
                                        TracerouteHop(1, "192.168.1.1", rtt = 1.milliseconds),
                                        TracerouteHop(2, "10.0.0.1", rtt = 150.milliseconds), // Bottleneck
                                        TracerouteHop(3, "8.8.8.8", rtt = 160.milliseconds),
                                    ),
                                completed = true,
                            ),
                        ),
                    testDuration = 30.seconds,
                )

            val result = integrator.integrate(activeDiagnostics = diagnostics)

            val routingCorrelations =
                result.correlations.filter {
                    it.category == CorrelationCategory.LATENCY_ROUTING &&
                        it.finding.contains("bottleneck")
                }
            assertTrue(routingCorrelations.isNotEmpty())
        }

        @Test
        fun `Topology correlation - poor topology with good RF`() {
            val integrator = SiteSurveyIntegrator(correlationThreshold = 0.5)
            val diagnostics = createGoodDiagnostics()

            val result =
                integrator.integrate(
                    activeDiagnostics = diagnostics,
                    rfQuality = 85.0,
                    topologyQuality = 50.0, // Poor topology
                )

            val topoCorrelations =
                result.correlations.filter {
                    it.category == CorrelationCategory.TOPOLOGY_ROAMING
                }
            assertTrue(topoCorrelations.isNotEmpty())
        }

        @Test
        fun `Multi-factor correlation - poor everything indicates WiFi breakdown`() {
            val integrator =
                SiteSurveyIntegrator(
                    correlationThreshold = 0.5,
                    enableMultiFactorAnalysis = true,
                )
            val diagnostics = createPoorDiagnostics()

            val result =
                integrator.integrate(
                    activeDiagnostics = diagnostics,
                    rfQuality = 40.0, // Poor RF
                )

            val criticalCorrelations =
                result.correlations.filter {
                    it.severity == CorrelationSeverity.CRITICAL
                }
            assertTrue(criticalCorrelations.isNotEmpty())
        }

        @Test
        fun `Anomaly detection - packet loss without latency`() {
            val integrator = SiteSurveyIntegrator(enableAnomalyDetection = true)
            val diagnostics =
                ActiveDiagnostics(
                    pingTests =
                        listOf(
                            PingTest(
                                targetHost = "8.8.8.8",
                                packetsTransmitted = 10,
                                packetsReceived = 8,
                                packetLossPercent = 20.0, // High packet loss
                                minRtt = 10.milliseconds,
                                avgRtt = 15.milliseconds, // But low latency!
                                maxRtt = 20.milliseconds,
                                stdDevRtt = 2.milliseconds,
                            ),
                        ),
                    testDuration = 30.seconds,
                )

            val result = integrator.integrate(activeDiagnostics = diagnostics)

            val packetLossAnomalies =
                result.anomalies.filter {
                    it.metric == "Packet Loss"
                }
            assertTrue(packetLossAnomalies.isNotEmpty(), "Expected packet loss anomaly")
        }

        @Test
        fun `Anomaly detection - extreme bandwidth asymmetry`() {
            val integrator = SiteSurveyIntegrator(enableAnomalyDetection = true)
            val diagnostics =
                ActiveDiagnostics(
                    bandwidthTests =
                        listOf(
                            BandwidthTest(
                                downloadMbps = 100.0,
                                uploadMbps = 0.5, // Extremely low
                                downloadBytes = 125_000_000,
                                uploadBytes = 625_000,
                                testDuration = 10.seconds,
                                serverHost = "speedtest.net",
                            ),
                        ),
                    testDuration = 30.seconds,
                )

            val result = integrator.integrate(activeDiagnostics = diagnostics)

            val uploadAnomalies =
                result.anomalies.filter {
                    it.metric == "Upload Bandwidth"
                }
            assertTrue(uploadAnomalies.isNotEmpty())
        }

        @Test
        fun `Anomaly detection - high jitter with low latency`() {
            val integrator = SiteSurveyIntegrator(enableAnomalyDetection = true)
            val diagnostics =
                ActiveDiagnostics(
                    pingTests =
                        listOf(
                            PingTest(
                                targetHost = "8.8.8.8",
                                packetsTransmitted = 10,
                                packetsReceived = 10,
                                packetLossPercent = 0.0,
                                minRtt = 10.milliseconds,
                                avgRtt = 20.milliseconds, // Low average
                                maxRtt = 50.milliseconds,
                                stdDevRtt = 25.milliseconds, // High jitter
                            ),
                        ),
                    testDuration = 30.seconds,
                )

            val result = integrator.integrate(activeDiagnostics = diagnostics)

            val jitterAnomalies =
                result.anomalies.filter {
                    it.metric == "Jitter"
                }
            assertTrue(jitterAnomalies.isNotEmpty())
        }

        @Test
        fun `Anomaly detection disabled when flag is false`() {
            val integrator = SiteSurveyIntegrator(enableAnomalyDetection = false)
            val diagnostics = createPoorDiagnostics()

            val result =
                integrator.integrate(
                    activeDiagnostics = diagnostics,
                    rfQuality = 40.0,
                )

            assertTrue(result.anomalies.isEmpty())
        }

        @Test
        fun `Correlation confidence filtering works`() {
            val integrator = SiteSurveyIntegrator(correlationThreshold = 0.95) // Very high
            val diagnostics = createGoodDiagnostics()

            val result =
                integrator.integrate(
                    activeDiagnostics = diagnostics,
                    rfQuality = 85.0,
                )

            // With high threshold, fewer correlations should pass
            val allCorrelations = result.correlations
            assertTrue(allCorrelations.all { it.confidence >= 0.95 })
        }

        @Test
        fun `IntegratedAnalysis identifies healthy network`() {
            val integrator = SiteSurveyIntegrator()
            val diagnostics = createGoodDiagnostics()

            val result =
                integrator.integrate(
                    activeDiagnostics = diagnostics,
                    rfQuality = 90.0,
                    securityScore = 85.0,
                )

            assertTrue(result.isHealthy)
            assertFalse(result.requiresImmediateAction)
        }

        @Test
        fun `IntegratedAnalysis filters high-confidence correlations`() {
            val integrator = SiteSurveyIntegrator(correlationThreshold = 0.5)
            val diagnostics = createGoodDiagnostics()

            val result =
                integrator.integrate(
                    activeDiagnostics = diagnostics,
                    rfQuality = 90.0,
                )

            val highConfidence = result.highConfidenceCorrelations
            assertTrue(highConfidence.all { it.confidence >= 0.8 })
        }

        @Test
        fun `IntegratedAnalysis filters critical correlations`() {
            val integrator =
                SiteSurveyIntegrator(
                    correlationThreshold = 0.5,
                    enableMultiFactorAnalysis = true,
                )
            val diagnostics = createPoorDiagnostics()

            val result =
                integrator.integrate(
                    activeDiagnostics = diagnostics,
                    rfQuality = 30.0,
                    securityScore = 40.0,
                )

            val critical = result.criticalCorrelations
            assertTrue(critical.all { it.severity == CorrelationSeverity.CRITICAL })
        }

        @Test
        fun `Executive summary includes all key information`() {
            val integrator = SiteSurveyIntegrator()
            val diagnostics = createGoodDiagnostics()

            val result =
                integrator.integrate(
                    activeDiagnostics = diagnostics,
                    rfQuality = 85.0,
                    location = "Test Location",
                )

            val summary = result.executiveSummary()
            assertContains(summary, "Network Analysis Summary")
            assertContains(summary, "Test Location")
            assertContains(summary, "Overall Health")
        }

        @Test
        fun `Correlation types are set correctly`() {
            val integrator = SiteSurveyIntegrator(correlationThreshold = 0.5)
            val diagnostics = createGoodDiagnostics()

            val result =
                integrator.integrate(
                    activeDiagnostics = diagnostics,
                    rfQuality = 90.0,
                    securityScore = 45.0, // Triggers CONFLICTING type
                )

            val conflicting = result.correlations.filter { it.type == CorrelationType.CONFLICTING }
            val related = result.correlations.filter { it.type == CorrelationType.RELATED }

            assertTrue(conflicting.isNotEmpty() || related.isNotEmpty())
        }

        @Test
        fun `Correlation confidence is within valid range`() {
            val correlation =
                Correlation(
                    finding = "Test finding",
                    rootCause = "Test cause",
                    recommendation = "Test recommendation",
                    confidence = 0.85,
                )

            assertEquals(0.85, correlation.confidence)
        }

        @Test
        fun `Correlation validation rejects invalid confidence`() {
            assertThrows<IllegalArgumentException> {
                Correlation(
                    finding = "Test",
                    rootCause = "Test",
                    recommendation = "Test",
                    confidence = 1.5, // Invalid
                )
            }
        }

        @Test
        fun `SiteSurveyIntegrator validation rejects invalid threshold`() {
            assertThrows<IllegalArgumentException> {
                SiteSurveyIntegrator(correlationThreshold = 1.5)
            }
        }

        @Test
        fun `Anomaly contains all required information`() {
            val anomaly =
                Anomaly(
                    metric = "Test Metric",
                    expectedRange = "0-10",
                    actualValue = "25",
                    description = "Test anomaly",
                    possibleCauses = listOf("Cause 1", "Cause 2"),
                    severity = AnomalySeverity.HIGH,
                )

            assertEquals("Test Metric", anomaly.metric)
            assertEquals(2, anomaly.possibleCauses.size)
            assertEquals(AnomalySeverity.HIGH, anomaly.severity)
        }

        @Test
        fun `Multi-factor analysis can be disabled`() {
            val integrator = SiteSurveyIntegrator(enableMultiFactorAnalysis = false)
            val diagnostics = createPoorDiagnostics()

            val result =
                integrator.integrate(
                    activeDiagnostics = diagnostics,
                    rfQuality = 40.0,
                )

            // Multi-factor correlations should still exist from other analyzers,
            // but the specific multi-factor analyzer won't run
            assertNotNull(result.correlations)
        }

        @Test
        fun `Correlation categories cover all domains`() {
            val categories = CorrelationCategory.values()
            assertTrue(categories.contains(CorrelationCategory.RF_CONNECTIVITY))
            assertTrue(categories.contains(CorrelationCategory.BANDWIDTH_ISP))
            assertTrue(categories.contains(CorrelationCategory.DNS_SERVER))
            assertTrue(categories.contains(CorrelationCategory.SECURITY_CONFIGURATION))
        }

        @Test
        fun `Bandwidth asymmetry correlation detects ratio anomalies`() {
            val integrator = SiteSurveyIntegrator(correlationThreshold = 0.5)
            val diagnostics =
                ActiveDiagnostics(
                    bandwidthTests =
                        listOf(
                            BandwidthTest(
                                downloadMbps = 500.0,
                                uploadMbps = 25.0, // Ratio = 20:1
                                downloadBytes = 625_000_000,
                                uploadBytes = 31_250_000,
                                testDuration = 10.seconds,
                                serverHost = "speedtest.net",
                            ),
                        ),
                    testDuration = 30.seconds,
                )

            val result = integrator.integrate(activeDiagnostics = diagnostics)

            val asymmetricCorrelations =
                result.correlations.filter {
                    it.finding.contains("asymmetric")
                }
            assertTrue(asymmetricCorrelations.isNotEmpty())
        }

        @Test
        fun `Optimal network produces informational correlations`() {
            val integrator =
                SiteSurveyIntegrator(
                    correlationThreshold = 0.5,
                    enableMultiFactorAnalysis = true,
                )
            val diagnostics = createGoodDiagnostics()

            val result =
                integrator.integrate(
                    activeDiagnostics = diagnostics,
                    rfQuality = 95.0,
                    securityScore = 90.0,
                )

            val informational =
                result.correlations.filter {
                    it.severity == CorrelationSeverity.INFORMATIONAL
                }
            assertTrue(informational.isNotEmpty(), "Expected informational correlations for optimal network")
        }
    }

    // ========================================================================
    // ComprehensiveReport Tests (~15 tests)
    // ========================================================================

    @Nested
    inner class ComprehensiveReportTests {
        private fun createTestAnalysis(): IntegratedAnalysis {
            val diagnostics =
                ActiveDiagnostics(
                    pingTests =
                        listOf(
                            PingTest(
                                targetHost = "8.8.8.8",
                                packetsTransmitted = 10,
                                packetsReceived = 10,
                                packetLossPercent = 0.0,
                                minRtt = 10.milliseconds,
                                avgRtt = 15.milliseconds,
                                maxRtt = 20.milliseconds,
                                stdDevRtt = 2.milliseconds,
                            ),
                        ),
                    testDuration = 30.seconds,
                )

            val healthScore =
                NetworkHealthScore.calculate(
                    rfQuality = 85.0,
                    connectivityScore = 90.0,
                    throughputScore = 80.0,
                )

            return IntegratedAnalysis(
                healthScore = healthScore,
                activeDiagnostics = diagnostics,
                recommendations =
                    RecommendationSet(
                        critical = emptyList(),
                        performance = emptyList(),
                        configuration = emptyList(),
                        security = emptyList(),
                        monitoring = emptyList(),
                        quickWins = emptyList(),
                    ),
                correlations =
                    listOf(
                        Correlation(
                            finding = "Test finding",
                            rootCause = "Test cause",
                            recommendation = "Test recommendation",
                        ),
                    ),
                location = "Test Site",
                timestamp = System.currentTimeMillis(),
                rfQualityScore = 85.0,
                topologyQualityScore = null,
                securityScore = null,
            )
        }

        @Test
        fun `builder creates report correctly`() {
            val analysis = createTestAnalysis()

            val report =
                ComprehensiveReport
                    .builder()
                    .withIntegratedAnalysis(analysis)
                    .withFormat(ReportFormat.TEXT)
                    .withTitle("Test Report")
                    .build()

            assertEquals(analysis, report.analysis)
            assertEquals(ReportFormat.TEXT, report.format)
            assertEquals("Test Report", report.title)
        }

        @Test
        fun `text format generates readable report`() {
            val analysis = createTestAnalysis()
            val report = ComprehensiveReport(analysis, format = ReportFormat.TEXT)

            val text = report.generateText()
            assertContains(text, "Network Analysis Summary")
            assertContains(text, "Test Site")
        }

        @Test
        fun `markdown format generates valid markdown`() {
            val analysis = createTestAnalysis()
            val report = ComprehensiveReport(analysis, format = ReportFormat.MARKDOWN)

            val markdown = report.generateMarkdown()
            assertContains(markdown, "## ") // Section headers
            assertContains(markdown, "| ") // Tables
        }

        @Test
        fun `JSON format generates valid JSON structure`() {
            val analysis = createTestAnalysis()
            val report = ComprehensiveReport(analysis, format = ReportFormat.JSON)

            val json = report.generateJson()
            assertContains(json, "{")
            assertContains(json, "}")
            assertContains(json, "healthScore")
        }

        @Test
        fun `section filtering works correctly`() {
            val analysis = createTestAnalysis()
            val report =
                ComprehensiveReport(
                    analysis = analysis,
                    sections = setOf(ReportSection.EXECUTIVE_SUMMARY),
                )

            val text = report.generateText()
            assertContains(text, "Network Analysis Summary")
        }

        @Test
        fun `all sections included when specified`() {
            val analysis = createTestAnalysis()
            val report =
                ComprehensiveReport(
                    analysis = analysis,
                    sections = ReportSection.ALL_SECTIONS,
                )

            val text = report.generateText()
            assertContains(text, "Network Analysis Summary") // Executive
            // Other sections may or may not appear depending on data availability
        }

        @Test
        fun `timestamp can be excluded`() {
            val analysis = createTestAnalysis()
            val report =
                ComprehensiveReport(
                    analysis = analysis,
                    includeTimestamp = false,
                )

            val text = report.generateText()
            assertFalse(text.contains("Generated:"))
        }

        @Test
        fun `metadata can be excluded`() {
            val analysis = createTestAnalysis()
            val report =
                ComprehensiveReport(
                    analysis = analysis,
                    includeMetadata = false,
                )

            val text = report.generateText()
            assertFalse(text.contains("WiFi Intelligence Diagnostics"))
        }

        @Test
        fun `report format has correct file extensions`() {
            assertEquals("txt", ReportFormat.TEXT.fileExtension)
            assertEquals("md", ReportFormat.MARKDOWN.fileExtension)
            assertEquals("json", ReportFormat.JSON.fileExtension)
            assertEquals("html", ReportFormat.HTML.fileExtension)
        }

        @Test
        fun `report format has correct MIME types`() {
            assertEquals("text/plain", ReportFormat.TEXT.mimeType)
            assertEquals("text/markdown", ReportFormat.MARKDOWN.mimeType)
            assertEquals("application/json", ReportFormat.JSON.mimeType)
            assertEquals("text/html", ReportFormat.HTML.mimeType)
        }

        @Test
        fun `default sections are reasonable`() {
            val defaults = ReportSection.DEFAULT_SECTIONS
            assertTrue(ReportSection.EXECUTIVE_SUMMARY in defaults)
            assertTrue(ReportSection.HEALTH_SCORE in defaults)
            assertTrue(ReportSection.RECOMMENDATIONS in defaults)
        }

        @Test
        fun `builder requires analysis`() {
            val builder = ComprehensiveReport.builder()
            assertThrows<IllegalArgumentException> {
                builder.build() // Without analysis
            }
        }

        @Test
        fun `builder with all sections works`() {
            val analysis = createTestAnalysis()
            val report =
                ComprehensiveReport
                    .builder()
                    .withIntegratedAnalysis(analysis)
                    .withAllSections()
                    .build()

            assertEquals(ReportSection.ALL_SECTIONS, report.sections)
        }

        @Test
        fun `markdown includes tables for diagnostics`() {
            val analysis = createTestAnalysis()
            val report =
                ComprehensiveReport(
                    analysis = analysis,
                    sections = setOf(ReportSection.ACTIVE_DIAGNOSTICS),
                    format = ReportFormat.MARKDOWN,
                )

            val markdown = report.generateMarkdown()
            // Should have table headers if diagnostics exist
            if (analysis.activeDiagnostics.pingTests.isNotEmpty()) {
                assertContains(markdown, "|")
            }
        }

        @Test
        fun `JSON includes all data structures`() {
            val analysis = createTestAnalysis()
            val report =
                ComprehensiveReport(
                    analysis = analysis,
                    sections = ReportSection.ALL_SECTIONS,
                    format = ReportFormat.JSON,
                )

            val json = report.generateJson()
            assertContains(json, "healthScore")
            assertContains(json, "overallScore")
        }
    }

    // ========================================================================
    // Helper Assertion Tests
    // ========================================================================

    @Test
    fun `PingQuality to score conversion is correct`() {
        // These are private extensions, test indirectly through diagnostics
        val excellent =
            ActiveDiagnostics(
                pingTests =
                    listOf(
                        PingTest(
                            targetHost = "8.8.8.8",
                            packetsTransmitted = 10,
                            packetsReceived = 10,
                            packetLossPercent = 0.0,
                            minRtt = 5.milliseconds,
                            avgRtt = 8.milliseconds,
                            maxRtt = 10.milliseconds,
                            stdDevRtt = 1.milliseconds,
                        ),
                    ),
                testDuration = 30.seconds,
            )

        assertTrue(
            excellent.connectivityQuality == PingQuality.EXCELLENT ||
                excellent.connectivityQuality == PingQuality.GOOD,
        )
    }
}
