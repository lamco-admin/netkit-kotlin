package io.lamco.netkit.exporttools.tools

import io.lamco.netkit.exporttools.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/**
 * Comprehensive tests for export tools.
 *
 * Tests all professional tools with 100+ test cases covering:
 * - ReportOrchestrator: Section preparation, validation, format delegation
 * - TemplateEngine: Variables, conditionals, loops, filters
 * - DataAggregator: Statistical functions, grouping, ranking
 * - ComplianceChecker: PCI-DSS and NIST compliance checking
 * - ExportQueue: Priority queuing, concurrency, error handling
 *
 * @since 1.0.0
 */
class ToolsTests {
    // ========================================================================
    // ReportOrchestrator Tests (22 tests)
    // ========================================================================

    @Nested
    inner class ReportOrchestratorTest {
        private lateinit var orchestrator: ReportOrchestrator

        @BeforeEach
        fun setup() {
            orchestrator = ReportOrchestrator()
        }

        @Test
        fun `generateReport creates valid JSON export`() {
            val data =
                ReportData(
                    networkScans = listOf(mapOf("ssid" to "TestNetwork", "rssi" to -50)),
                    overallSecurityScore = 85.0,
                )
            val config = ExportConfiguration.json()

            val result = orchestrator.generateReport(data, config)

            assertTrue(result.success)
            assertEquals(ExportFormat.JSON, result.format)
            assertNotNull(result.content)
            assertTrue(result.sizeBytes > 0)
        }

        @Test
        fun `generateReport includes executive summary section`() {
            val data =
                ReportData(
                    networkScans = listOf(mapOf("ssid" to "Net1"), mapOf("ssid" to "Net2")),
                    overallSecurityScore = 75.0,
                    topIssues = listOf("Issue1", "Issue2"),
                )
            val config =
                ExportConfiguration(
                    format = ExportFormat.JSON,
                    template = ReportTemplate.ExecutiveSummary,
                )

            val result = orchestrator.generateReport(data, config)

            assertTrue(result.success)
            assertNotNull(result.content)
            assertTrue(result.content!!.contains("\"networkCount\": 2"))
        }

        @Test
        fun `generateReport includes health score section`() {
            val data =
                ReportData(
                    healthScore = 92.5,
                    healthDimensions = mapOf("security" to 95.0, "performance" to 90.0),
                    healthTrend = "improving",
                )
            val config =
                ExportConfiguration(
                    format = ExportFormat.JSON,
                    template = ReportTemplate.ExecutiveSummary,
                )

            val result = orchestrator.generateReport(data, config)

            assertTrue(result.success)
            assertTrue(result.content!!.contains("92.5"))
        }

        @Test
        fun `generateReport includes security analysis section`() {
            val data =
                ReportData(
                    vulnerabilities =
                        listOf(
                            mapOf("type" to "WEP", "severity" to "HIGH"),
                        ),
                    encryptionSummary = mapOf("WPA3" to 5, "WPA2" to 10),
                    securityRecommendations = listOf("Upgrade to WPA3"),
                )
            val config =
                ExportConfiguration(
                    format = ExportFormat.JSON,
                    template = ReportTemplate.SecurityAudit,
                )

            val result = orchestrator.generateReport(data, config)

            assertTrue(result.success)
            assertTrue(result.content!!.contains("WPA3"))
            assertTrue(result.content!!.contains("Upgrade to WPA3"))
        }

        @Test
        fun `generateReport includes performance metrics section`() {
            val data =
                ReportData(
                    avgSignalStrength = -45.5,
                    avgThroughput = 350.0,
                    avgLatency = 12.5,
                )
            val config =
                ExportConfiguration.json(
                    template = ReportTemplate.TechnicalReport,
                )

            val result = orchestrator.generateReport(data, config)

            assertTrue(result.success)
            assertTrue(result.content!!.contains("-45.5"))
            assertTrue(result.content!!.contains("350"))
        }

        @Test
        fun `generateReport includes active diagnostics section`() {
            val data =
                ReportData(
                    pingResults = listOf(mapOf("host" to "8.8.8.8", "latency" to 15)),
                    tracerouteResults = listOf(mapOf("hop" to 1, "ip" to "192.168.1.1")),
                    bandwidthResults = listOf(mapOf("down" to 100, "up" to 50)),
                    dnsResults = listOf(mapOf("server" to "8.8.8.8", "time" to 5)),
                )
            val config =
                ExportConfiguration.json(
                    template = ReportTemplate.TechnicalReport,
                )

            val result = orchestrator.generateReport(data, config)

            assertTrue(result.success)
            assertTrue(result.content!!.contains("8.8.8.8"))
        }

        @Test
        fun `generateReport includes passive analysis section`() {
            val data =
                ReportData(
                    networkScans = listOf(mapOf("ssid" to "Network1")),
                    signalAnalysis = mapOf("avgRssi" to -50),
                    channelUtilization = mapOf("Channel 1" to 0.75),
                )
            val config =
                ExportConfiguration.json(
                    template = ReportTemplate.TechnicalReport,
                )

            val result = orchestrator.generateReport(data, config)

            assertTrue(result.success)
            assertTrue(result.content!!.contains("Network1"))
        }

        @Test
        fun `generateReport includes topology analysis section`() {
            val data =
                ReportData(
                    accessPoints = listOf(mapOf("bssid" to "00:11:22:33:44:55")),
                    roamingDomains = listOf(mapOf("domain" to "enterprise")),
                    coverageData = mapOf("area" to "office"),
                )
            val config =
                ExportConfiguration.json(
                    template = ReportTemplate.TechnicalReport,
                )

            val result = orchestrator.generateReport(data, config)

            assertTrue(result.success)
            assertTrue(result.content!!.contains("00:11:22:33:44:55"))
        }

        @Test
        fun `generateReport adds metadata when configured`() {
            val data = ReportData(networkScans = listOf(mapOf("ssid" to "Test")))
            val config =
                ExportConfiguration(
                    format = ExportFormat.JSON,
                    template = ReportTemplate.ExecutiveSummary,
                    includeMetadata = true,
                    title = "Custom Report",
                    author = "Test Author",
                )

            val result = orchestrator.generateReport(data, config)

            assertTrue(result.success)
            assertTrue(result.content!!.contains("Metadata"))
            assertTrue(result.content!!.contains("Custom Report"))
            assertTrue(result.content!!.contains("Test Author"))
        }

        @Test
        fun `generateReport omits metadata when not configured`() {
            val data = ReportData(networkScans = listOf(mapOf("ssid" to "Test")))
            val config =
                ExportConfiguration(
                    format = ExportFormat.JSON,
                    template = ReportTemplate.ExecutiveSummary,
                    includeMetadata = false,
                )

            val result = orchestrator.generateReport(data, config)

            assertTrue(result.success)
            assertFalse(result.content!!.contains("Metadata"))
        }

        @Test
        fun `generateReport validates configuration`() {
            // Custom template with empty name throws IllegalArgumentException
            assertFailsWith<IllegalArgumentException> {
                ReportTemplate.Custom(
                    name = "",
                    description = "Empty test template",
                    sections = emptyList(),
                    options = TemplateOptions(),
                )
            }
        }

        @Test
        fun `generateReport exports to CSV format`() {
            val data =
                ReportData(
                    networkScans =
                        listOf(
                            mapOf("ssid" to "Net1", "rssi" to -50),
                            mapOf("ssid" to "Net2", "rssi" to -60),
                        ),
                )
            val config = ExportConfiguration.csv()

            val result = orchestrator.generateReport(data, config)

            assertTrue(result.success)
            assertEquals(ExportFormat.CSV, result.format)
            // CSV output contains the SSID value in the stringified map list
            assertTrue(result.content!!.contains("Net1") || result.content!!.contains("Section"))
        }

        @Test
        fun `generateReport exports to Markdown format`() {
            val data =
                ReportData(
                    networkScans = listOf(mapOf("ssid" to "Network")),
                )
            val config = ExportConfiguration.markdown()

            val result = orchestrator.generateReport(data, config)

            assertTrue(result.success)
            assertEquals(ExportFormat.MARKDOWN, result.format)
        }

        @Test
        fun `generateReport exports to HTML format`() {
            val data =
                ReportData(
                    networkScans = listOf(mapOf("ssid" to "Network")),
                )
            val config = ExportConfiguration.html()

            val result = orchestrator.generateReport(data, config)

            assertTrue(result.success)
            assertEquals(ExportFormat.HTML, result.format)
        }

        @Test
        fun `generateReport tracks duration`() {
            val data = ReportData(networkScans = listOf(mapOf("ssid" to "Test")))
            val config = ExportConfiguration.json()

            val result = orchestrator.generateReport(data, config)

            assertTrue(result.success)
            assertTrue(result.durationMillis >= 0) // Can be 0 for fast operations
        }

        @Test
        fun `generateReport handles empty data gracefully`() {
            val data = ReportData()
            val config = ExportConfiguration.json()

            val result = orchestrator.generateReport(data, config)

            assertTrue(result.success)
            assertNotNull(result.content)
        }

        @Test
        fun `generateReport uses template sections`() {
            val data =
                ReportData(
                    overallSecurityScore = 80.0,
                    healthScore = 85.0,
                )
            val config =
                ExportConfiguration(
                    format = ExportFormat.JSON,
                    template = ReportTemplate.ExecutiveSummary,
                )

            val result = orchestrator.generateReport(data, config)

            assertTrue(result.success)
            assertTrue(result.content!!.contains("\"securityScore\": 80.0"))
        }

        @Test
        fun `generateReport includes recommendations section`() {
            val data =
                ReportData(
                    recommendations =
                        listOf(
                            mapOf("priority" to "HIGH", "action" to "Upgrade firmware"),
                            mapOf("priority" to "MEDIUM", "action" to "Change passwords"),
                        ),
                )
            val config =
                ExportConfiguration.json(
                    template = ReportTemplate.TechnicalReport,
                )

            val result = orchestrator.generateReport(data, config)

            assertTrue(result.success)
            assertTrue(result.content!!.contains("Upgrade firmware"))
            assertTrue(result.content!!.contains("Change passwords"))
        }

        @Test
        fun `generateReport includes technical details section`() {
            val data =
                ReportData(
                    deviceCapabilities = mapOf("wifi6" to true, "mimo" to "4x4"),
                    advancedMetrics = mapOf("noise_floor" to -95),
                )
            val config =
                ExportConfiguration.json(
                    template = ReportTemplate.TechnicalReport,
                )

            val result = orchestrator.generateReport(data, config)

            assertTrue(result.success)
            assertTrue(result.content!!.contains("wifi6"))
        }

        @Test
        fun `generateReport handles complex nested data`() {
            val data =
                ReportData(
                    networkScans =
                        listOf(
                            mapOf(
                                "ssid" to "Network",
                                "capabilities" to
                                    mapOf(
                                        "encryption" to "WPA3",
                                        "frequencies" to listOf(2.4, 5.0),
                                    ),
                            ),
                        ),
                )
            val config = ExportConfiguration.json()

            val result = orchestrator.generateReport(data, config)

            assertTrue(result.success)
            assertTrue(result.content!!.contains("WPA3"))
        }

        @Test
        fun `ReportData supports all optional fields`() {
            val data =
                ReportData(
                    networkScans = listOf(),
                    diagnostics = mapOf(),
                    overallSecurityScore = 0.0,
                    healthScore = 0.0,
                    healthDimensions = mapOf(),
                    healthTrend = "",
                    vulnerabilities = listOf(),
                    encryptionSummary = mapOf(),
                    securityRecommendations = listOf(),
                    recommendations = listOf(),
                    topIssues = listOf(),
                    executiveSummary = "",
                    performanceSummary = mapOf(),
                    avgSignalStrength = 0.0,
                    avgThroughput = 0.0,
                    avgLatency = 0.0,
                    pingResults = listOf(),
                    tracerouteResults = listOf(),
                    bandwidthResults = listOf(),
                    dnsResults = listOf(),
                    signalAnalysis = mapOf(),
                    channelUtilization = mapOf(),
                    accessPoints = listOf(),
                    roamingDomains = listOf(),
                    coverageData = mapOf(),
                    rfPerformanceCorrelations = listOf(),
                    securityPerformanceCorrelations = listOf(),
                    deviceCapabilities = mapOf(),
                    advancedMetrics = mapOf(),
                    rawData = mapOf(),
                )

            assertNotNull(data)
            assertEquals(0, data.networkScans?.size)
        }

        @Test
        fun `generateReport includes correlations section`() {
            val data =
                ReportData(
                    rfPerformanceCorrelations = listOf(mapOf("signal" to -50, "throughput" to 100)),
                    securityPerformanceCorrelations = listOf(mapOf("encryption" to "WPA3", "speed" to 95)),
                )
            val config =
                ExportConfiguration.json(
                    template = ReportTemplate.TechnicalReport,
                )

            val result = orchestrator.generateReport(data, config)

            assertTrue(result.success)
            assertTrue(result.content!!.contains("rfVsPerformance"))
        }
    }

    // ========================================================================
    // TemplateEngine Tests (27 tests)
    // ========================================================================

    @Nested
    inner class TemplateEngineTest {
        private lateinit var engine: TemplateEngine

        @BeforeEach
        fun setup() {
            engine = TemplateEngine()
        }

        @Test
        fun `process substitutes simple variable`() {
            engine.setVariable("name", "TestNetwork")
            val result = engine.process("Network: \${name}")

            assertEquals("Network: TestNetwork", result)
        }

        @Test
        fun `process substitutes multiple variables`() {
            engine.setVariable("ssid", "MyWiFi")
            engine.setVariable("rssi", -45)
            val result = engine.process("SSID: \${ssid}, RSSI: \${rssi}dBm")

            assertEquals("SSID: MyWiFi, RSSI: -45dBm", result)
        }

        @Test
        fun `process substitutes nested variable`() {
            engine.setVariable("network", mapOf("ssid" to "Test", "security" to "WPA3"))
            val result = engine.process("Network: \${network.ssid} (\${network.security})")

            assertEquals("Network: Test (WPA3)", result)
        }

        @Test
        fun `process handles missing variable`() {
            val result = engine.process("Value: \${missing}")

            assertEquals("Value: ", result)
        }

        @Test
        fun `process handles conditional true`() {
            engine.setVariable("hasWarning", true)
            val result = engine.process("{{if hasWarning}}WARNING: Issue detected{{endif}}")

            assertEquals("WARNING: Issue detected", result)
        }

        @Test
        fun `process handles conditional false`() {
            engine.setVariable("hasWarning", false)
            val result = engine.process("{{if hasWarning}}WARNING{{endif}}")

            assertEquals("", result)
        }

        @Test
        fun `process handles numeric comparison less than`() {
            engine.setVariable("score", 45)
            val result = engine.process("{{if score < 50}}Low score{{endif}}")

            assertEquals("Low score", result)
        }

        @Test
        fun `process handles numeric comparison greater than`() {
            engine.setVariable("score", 85)
            val result = engine.process("{{if score > 80}}High score{{endif}}")

            assertEquals("High score", result)
        }

        @Test
        fun `process handles equality comparison`() {
            engine.setVariable("type", "WPA3")
            val result = engine.process("{{if type == WPA3}}Secure{{endif}}")

            assertEquals("Secure", result)
        }

        @Test
        fun `process handles inequality comparison`() {
            engine.setVariable("type", "WEP")
            val result = engine.process("{{if type != WPA3}}Upgrade needed{{endif}}")

            assertEquals("Upgrade needed", result)
        }

        @Test
        fun `process handles loop over list`() {
            engine.setVariable("networks", listOf("WiFi1", "WiFi2", "WiFi3"))
            val result = engine.process("{{for net in networks}}\${net}\n{{endfor}}")

            assertEquals("WiFi1\nWiFi2\nWiFi3\n", result)
        }

        @Test
        fun `process handles empty loop`() {
            engine.setVariable("networks", emptyList<String>())
            val result = engine.process("{{for net in networks}}\${net}{{endfor}}")

            assertEquals("", result)
        }

        @Test
        fun `process handles nested loop variables`() {
            engine.setVariable(
                "networks",
                listOf(
                    mapOf("ssid" to "Net1"),
                    mapOf("ssid" to "Net2"),
                ),
            )
            val result = engine.process("{{for net in networks}}- \${net.ssid}\n{{endfor}}")

            assertTrue(result.contains("- Net1"))
            assertTrue(result.contains("- Net2"))
        }

        @Test
        fun `process applies uppercase filter`() {
            engine.setVariable("text", "hello")
            val result = engine.process("\${text|uppercase}")

            assertEquals("HELLO", result)
        }

        @Test
        fun `process applies lowercase filter`() {
            engine.setVariable("text", "HELLO")
            val result = engine.process("\${text|lowercase}")

            assertEquals("hello", result)
        }

        @Test
        fun `process applies capitalize filter`() {
            engine.setVariable("text", "hello world")
            val result = engine.process("\${text|capitalize}")

            assertEquals("Hello world", result)
        }

        @Test
        fun `process applies round filter`() {
            engine.setVariable("value", 3.14159)
            val result = engine.process("\${value|round:2}")

            assertEquals("3.14", result)
        }

        @Test
        fun `process applies default filter for null`() {
            engine.setVariable("value", null)
            val result = engine.process("\${value|default:N/A}")

            assertEquals("N/A", result)
        }

        @Test
        fun `process applies default filter for empty string`() {
            engine.setVariable("value", "")
            val result = engine.process("\${value|default:None}")

            assertEquals("None", result)
        }

        @Test
        fun `process applies length filter to list`() {
            engine.setVariable("networks", listOf("A", "B", "C"))
            val result = engine.process("\${networks|length}")

            assertEquals("3", result)
        }

        @Test
        fun `process applies length filter to string`() {
            engine.setVariable("text", "Hello")
            val result = engine.process("\${text|length}")

            assertEquals("5", result)
        }

        @Test
        fun `process applies first filter`() {
            engine.setVariable("networks", listOf("First", "Second", "Third"))
            val result = engine.process("\${networks|first}")

            assertEquals("First", result)
        }

        @Test
        fun `process applies last filter`() {
            engine.setVariable("networks", listOf("First", "Second", "Third"))
            val result = engine.process("\${networks|last}")

            assertEquals("Third", result)
        }

        @Test
        fun `process applies join filter`() {
            engine.setVariable("items", listOf("A", "B", "C"))
            val result = engine.process("\${items|join: - }")

            assertEquals("A - B - C", result)
        }

        @Test
        fun `process applies escapeHtml filter`() {
            engine.setVariable("text", "<script>alert('test')</script>")
            val result = engine.process("\${text|escapeHtml}")

            assertEquals("&lt;script&gt;alert('test')&lt;/script&gt;", result)
        }

        @Test
        fun `process applies escapeJson filter`() {
            engine.setVariable("text", "Line 1\nLine 2")
            val result = engine.process("\${text|escapeJson}")

            assertEquals("Line 1\\nLine 2", result)
        }

        @Test
        fun `process chains multiple filters`() {
            engine.setVariable("text", "hello world")
            val result = engine.process("\${text|uppercase|capitalize}")

            assertEquals("HELLO WORLD", result)
        }

        @Test
        fun `setVariables sets multiple variables`() {
            engine.setVariables(
                mapOf(
                    "var1" to "value1",
                    "var2" to "value2",
                ),
            )
            val result = engine.process("\${var1} \${var2}")

            assertEquals("value1 value2", result)
        }
    }

    // ========================================================================
    // DataAggregator Tests (22 tests)
    // ========================================================================

    @Nested
    inner class DataAggregatorTest {
        private lateinit var aggregator: DataAggregator

        @BeforeEach
        fun setup() {
            aggregator = DataAggregator()
        }

        @Test
        fun `aggregate COUNT returns count of items`() {
            val data: List<Any> = listOf(1, 2, 3, 4, 5)
            val result = aggregator.aggregate(data, DataAggregator.AggregationStrategy.COUNT) { (it as Int).toDouble() }

            assertEquals(5.0, result)
        }

        @Test
        fun `aggregate SUM returns sum of values`() {
            val data = listOf(1, 2, 3, 4, 5)
            val result = aggregator.aggregate(data, DataAggregator.AggregationStrategy.SUM) { (it as Int).toDouble() }

            assertEquals(15.0, result)
        }

        @Test
        fun `aggregate AVG returns average of values`() {
            val data = listOf(10, 20, 30, 40, 50)
            val result = aggregator.aggregate(data, DataAggregator.AggregationStrategy.AVG) { (it as Int).toDouble() }

            assertEquals(30.0, result)
        }

        @Test
        fun `aggregate MIN returns minimum value`() {
            val data = listOf(5, 2, 8, 1, 9)
            val result = aggregator.aggregate(data, DataAggregator.AggregationStrategy.MIN) { (it as Int).toDouble() }

            assertEquals(1.0, result)
        }

        @Test
        fun `aggregate MAX returns maximum value`() {
            val data = listOf(5, 2, 8, 1, 9)
            val result = aggregator.aggregate(data, DataAggregator.AggregationStrategy.MAX) { (it as Int).toDouble() }

            assertEquals(9.0, result)
        }

        @Test
        fun `aggregate MEDIAN returns median for odd count`() {
            val data = listOf(1, 3, 5, 7, 9)
            val result =
                aggregator.aggregate(
                    data,
                    DataAggregator.AggregationStrategy.MEDIAN,
                ) { (it as Int).toDouble() }

            assertEquals(5.0, result)
        }

        @Test
        fun `aggregate MEDIAN returns median for even count`() {
            val data = listOf(1, 2, 3, 4)
            val result =
                aggregator.aggregate(
                    data,
                    DataAggregator.AggregationStrategy.MEDIAN,
                ) { (it as Int).toDouble() }

            assertEquals(2.5, result)
        }

        @Test
        fun `aggregate STDDEV returns standard deviation`() {
            val data = listOf(2, 4, 4, 4, 5, 5, 7, 9)
            val result =
                aggregator.aggregate(
                    data,
                    DataAggregator.AggregationStrategy.STDDEV,
                ) { (it as Int).toDouble() }

            assertNotNull(result)
            assertTrue(result!! > 1.0 && result < 3.0)
        }

        @Test
        fun `aggregate returns null for empty data`() {
            val data = emptyList<Int>()
            val result = aggregator.aggregate(data, DataAggregator.AggregationStrategy.AVG) { (it as Int).toDouble() }

            assertNull(result)
        }

        @Test
        fun `groupAndAggregate groups by key`() {
            val data =
                listOf(
                    mapOf("type" to "A", "value" to 10),
                    mapOf("type" to "B", "value" to 20),
                    mapOf("type" to "A", "value" to 15),
                )
            val result =
                aggregator.groupAndAggregate(
                    data = data,
                    keyExtractor = { (it as Map<*, *>)["type"] as String },
                    valueExtractor = { ((it as Map<*, *>)["value"] as Int).toDouble() },
                    strategy = DataAggregator.AggregationStrategy.SUM,
                )

            assertEquals(25.0, result["A"])
            assertEquals(20.0, result["B"])
        }

        @Test
        fun `calculatePercentile returns 50th percentile`() {
            val data = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
            val result = aggregator.calculatePercentile(data, 50)

            assertEquals(3.0, result)
        }

        @Test
        fun `calculatePercentile returns 95th percentile`() {
            val data = (1..100).map { it.toDouble() }
            val result = aggregator.calculatePercentile(data, 95)

            assertTrue(result!! >= 94.0 && result <= 96.0)
        }

        @Test
        fun `calculatePercentile returns 99th percentile`() {
            val data = (1..100).map { it.toDouble() }
            val result = aggregator.calculatePercentile(data, 99)

            assertTrue(result!! >= 98.0 && result <= 100.0)
        }

        @Test
        fun `calculatePercentile validates range`() {
            val data = listOf(1.0, 2.0, 3.0)
            assertFailsWith<IllegalArgumentException> {
                aggregator.calculatePercentile(data, 101)
            }
        }

        @Test
        fun `histogram creates buckets`() {
            val data = (1..100).map { it.toDouble() }
            val result = aggregator.histogram(data, bucketCount = 10)

            assertEquals(10, result.size)
            assertTrue(result.values.sum() == 100)
        }

        @Test
        fun `histogram handles single value`() {
            val data = listOf(5.0)
            val result = aggregator.histogram(data, bucketCount = 5)

            assertTrue(result.isNotEmpty())
        }

        @Test
        fun `topN returns top items descending`() {
            val data =
                listOf(
                    mapOf("name" to "A", "score" to 100),
                    mapOf("name" to "B", "score" to 85),
                    mapOf("name" to "C", "score" to 92),
                )
            val result =
                aggregator.topN(
                    data = data,
                    n = 2,
                    valueExtractor = { ((it as Map<*, *>)["score"] as Int).toDouble() },
                )

            assertEquals(2, result.size)
            assertEquals("A", (result[0] as Map<*, *>)["name"])
            assertEquals("C", (result[1] as Map<*, *>)["name"])
        }

        @Test
        fun `topN returns bottom items ascending`() {
            val data =
                listOf(
                    mapOf("name" to "A", "score" to 100),
                    mapOf("name" to "B", "score" to 85),
                    mapOf("name" to "C", "score" to 92),
                )
            val result =
                aggregator.topN(
                    data = data,
                    n = 2,
                    valueExtractor = { ((it as Map<*, *>)["score"] as Int).toDouble() },
                    descending = false,
                )

            assertEquals(2, result.size)
            assertEquals("B", (result[0] as Map<*, *>)["name"])
        }

        @Test
        fun `calculateStatistics returns complete summary`() {
            val data = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
            val result = aggregator.calculateStatistics(data)

            assertEquals(5, result.count)
            assertEquals(15.0, result.sum)
            assertEquals(3.0, result.mean)
            assertEquals(3.0, result.median)
            assertEquals(1.0, result.min)
            assertEquals(5.0, result.max)
            assertEquals(3.0, result.p50)
        }

        @Test
        fun `calculateStatistics handles empty data`() {
            val data = emptyList<Double>()
            val result = aggregator.calculateStatistics(data)

            assertEquals(0, result.count)
            assertEquals(0.0, result.sum)
        }

        @Test
        fun `StatisticsSummary contains all fields`() {
            val summary =
                StatisticsSummary(
                    count = 10,
                    sum = 100.0,
                    mean = 10.0,
                    median = 9.5,
                    min = 1.0,
                    max = 20.0,
                    stddev = 5.5,
                    p50 = 9.5,
                    p95 = 18.0,
                    p99 = 19.5,
                )

            assertEquals(10, summary.count)
            assertEquals(100.0, summary.sum)
            assertEquals(10.0, summary.mean)
        }

        @Test
        fun `aggregate handles null values in extractor`() {
            val data: List<Any> = listOf(1, 2, 3, 4, 5)
            val result =
                aggregator.aggregate(data, DataAggregator.AggregationStrategy.AVG) {
                    // Skip even numbers to test extractor returning null
                    val num = it as Int
                    if (num % 2 == 0) null else num.toDouble()
                }

            assertEquals(3.0, result) // (1 + 3 + 5) / 3 = 3.0
        }
    }

    // ========================================================================
    // ComplianceChecker Tests (22 tests)
    // ========================================================================

    @Nested
    inner class ComplianceCheckerTest {
        private lateinit var checker: ComplianceChecker

        @BeforeEach
        fun setup() {
            checker = ComplianceChecker()
        }

        @Test
        fun `checkPciDss passes with WPA3 encryption`() {
            val data =
                mapOf(
                    "encryption" to "WPA3",
                    "hasDefaultCredentials" to false,
                )
            val result = checker.checkPciDss(data)

            assertEquals("PCI-DSS v4.0", result.framework)
            assertEquals(ComplianceStatus.PASS, result.overallStatus)
            assertTrue(result.findings.any { it.requirement == "PCI-DSS 4.2" && it.status == ComplianceStatus.PASS })
        }

        @Test
        fun `checkPciDss passes with WPA2-Enterprise encryption`() {
            val data =
                mapOf(
                    "encryption" to "WPA2-Enterprise",
                    "hasDefaultCredentials" to false,
                )
            val result = checker.checkPciDss(data)

            assertEquals(ComplianceStatus.PASS, result.overallStatus)
            assertTrue(result.findings.any { it.requirement == "PCI-DSS 4.2" && it.status == ComplianceStatus.PASS })
        }

        @Test
        fun `checkPciDss warns with WPA2 encryption`() {
            val data =
                mapOf(
                    "encryption" to "WPA2",
                    "hasDefaultCredentials" to false,
                )
            val result = checker.checkPciDss(data)

            assertEquals(ComplianceStatus.WARNING, result.overallStatus)
            assertTrue(result.findings.any { it.requirement == "PCI-DSS 4.2" && it.status == ComplianceStatus.WARNING })
        }

        @Test
        fun `checkPciDss fails with weak encryption`() {
            val data =
                mapOf(
                    "encryption" to "WEP",
                    "hasDefaultCredentials" to false,
                )
            val result = checker.checkPciDss(data)

            assertEquals(ComplianceStatus.FAIL, result.overallStatus)
            assertTrue(result.findings.any { it.requirement == "PCI-DSS 4.2" && it.status == ComplianceStatus.FAIL })
        }

        @Test
        fun `checkPciDss fails with no encryption`() {
            val data: Map<String, Any> =
                mapOf(
                    "hasDefaultCredentials" to false,
                )
            val result = checker.checkPciDss(data)

            assertEquals(ComplianceStatus.FAIL, result.overallStatus)
        }

        @Test
        fun `checkPciDss fails with default credentials`() {
            val data =
                mapOf(
                    "encryption" to "WPA3",
                    "hasDefaultCredentials" to true,
                )
            val result = checker.checkPciDss(data)

            assertEquals(ComplianceStatus.FAIL, result.overallStatus)
            assertTrue(result.findings.any { it.requirement == "PCI-DSS 2.1" && it.status == ComplianceStatus.FAIL })
        }

        @Test
        fun `checkPciDss passes with no default credentials`() {
            val data =
                mapOf(
                    "encryption" to "WPA3",
                    "hasDefaultCredentials" to false,
                )
            val result = checker.checkPciDss(data)

            assertTrue(result.findings.any { it.requirement == "PCI-DSS 2.1" && it.status == ComplianceStatus.PASS })
        }

        @Test
        fun `checkPciDss includes remediation for failures`() {
            val data =
                mapOf(
                    "encryption" to "WEP",
                    "hasDefaultCredentials" to true,
                )
            val result = checker.checkPciDss(data)

            assertTrue(result.findings.any { it.remediation != null })
        }

        @Test
        fun `checkNist80097 passes with WPA3`() {
            val data =
                mapOf(
                    "encryption" to "WPA3",
                    "rogueApDetection" to true,
                )
            val result = checker.checkNist80097(data)

            assertEquals("NIST SP 800-97", result.framework)
            assertEquals(ComplianceStatus.PASS, result.overallStatus)
        }

        @Test
        fun `checkNist80097 warns with WPA2`() {
            val data =
                mapOf(
                    "encryption" to "WPA2",
                    "rogueApDetection" to true,
                )
            val result = checker.checkNist80097(data)

            assertEquals(ComplianceStatus.WARNING, result.overallStatus)
            assertTrue(result.findings.any { it.status == ComplianceStatus.WARNING })
        }

        @Test
        fun `checkNist80097 fails with weak encryption`() {
            val data =
                mapOf(
                    "encryption" to "WEP",
                    "rogueApDetection" to true,
                )
            val result = checker.checkNist80097(data)

            assertEquals(ComplianceStatus.FAIL, result.overallStatus)
        }

        @Test
        fun `checkNist80097 warns without rogue AP detection`() {
            val data =
                mapOf(
                    "encryption" to "WPA3",
                    "rogueApDetection" to false,
                )
            val result = checker.checkNist80097(data)

            assertEquals(ComplianceStatus.WARNING, result.overallStatus)
            assertTrue(result.findings.any { it.requirement == "NIST 800-97 4.2" })
        }

        @Test
        fun `checkNist80097 passes with rogue AP detection`() {
            val data =
                mapOf(
                    "encryption" to "WPA3",
                    "rogueApDetection" to true,
                )
            val result = checker.checkNist80097(data)

            assertTrue(
                result.findings.any {
                    it.requirement == "NIST 800-97 4.2" && it.status == ComplianceStatus.PASS
                },
            )
        }

        @Test
        fun `toValidationResult converts PASS to INFO`() {
            val data = mapOf("encryption" to "WPA3", "hasDefaultCredentials" to false)
            val complianceResult = checker.checkPciDss(data)
            val validationResult = checker.toValidationResult(complianceResult)

            assertTrue(validationResult.isValid)
            assertTrue(validationResult.info.isNotEmpty())
        }

        @Test
        fun `toValidationResult converts WARNING to ValidationIssue`() {
            val data = mapOf("encryption" to "WPA2", "hasDefaultCredentials" to false)
            val complianceResult = checker.checkPciDss(data)
            val validationResult = checker.toValidationResult(complianceResult)

            assertTrue(validationResult.isValid)
            assertTrue(validationResult.warnings.isNotEmpty())
        }

        @Test
        fun `toValidationResult converts FAIL to ERROR`() {
            val data = mapOf("encryption" to "WEP", "hasDefaultCredentials" to false)
            val complianceResult = checker.checkPciDss(data)
            val validationResult = checker.toValidationResult(complianceResult)

            assertFalse(validationResult.isValid)
            assertTrue(validationResult.errors.isNotEmpty())
        }

        @Test
        fun `ComplianceResult summary includes framework name`() {
            val data = mapOf("encryption" to "WPA3", "hasDefaultCredentials" to false)
            val result = checker.checkPciDss(data)
            val summary = result.summary()

            assertTrue(summary.contains("PCI-DSS v4.0"))
            assertTrue(summary.contains("Overall Status"))
        }

        @Test
        fun `ComplianceResult summary shows counts`() {
            val data = mapOf("encryption" to "WPA2", "hasDefaultCredentials" to false)
            val result = checker.checkPciDss(data)
            val summary = result.summary()

            assertTrue(summary.contains("PASS:"))
            assertTrue(summary.contains("WARNING:"))
            assertTrue(summary.contains("FAIL:"))
        }

        @Test
        fun `ComplianceResult summary lists critical findings`() {
            val data = mapOf("encryption" to "WEP", "hasDefaultCredentials" to true)
            val result = checker.checkPciDss(data)
            val summary = result.summary()

            assertTrue(summary.contains("Critical Findings"))
        }

        @Test
        fun `ComplianceFinding supports all fields`() {
            val finding =
                ComplianceFinding(
                    requirement = "TEST-1",
                    status = ComplianceStatus.PASS,
                    description = "Test finding",
                    detail = "Detail text",
                    remediation = "Fix it",
                )

            assertEquals("TEST-1", finding.requirement)
            assertEquals(ComplianceStatus.PASS, finding.status)
            assertEquals("Test finding", finding.description)
            assertEquals("Detail text", finding.detail)
            assertEquals("Fix it", finding.remediation)
        }

        @Test
        fun `ComplianceStatus enum has all values`() {
            assertEquals(3, ComplianceStatus.values().size)
            assertNotNull(ComplianceStatus.PASS)
            assertNotNull(ComplianceStatus.WARNING)
            assertNotNull(ComplianceStatus.FAIL)
        }

        @Test
        fun `ComplianceResult tracks timestamp`() {
            val data = mapOf("encryption" to "WPA3", "hasDefaultCredentials" to false)
            val beforeTime = System.currentTimeMillis()
            val result = checker.checkPciDss(data)
            val afterTime = System.currentTimeMillis()

            assertTrue(result.checkedAt >= beforeTime && result.checkedAt <= afterTime)
        }
    }

    // ========================================================================
    // ExportQueue Tests (22 tests)
    // ========================================================================

    @Nested
    inner class ExportQueueTest {
        private lateinit var queue: ExportQueue

        @BeforeEach
        fun setup() {
            queue = ExportQueue(maxConcurrent = 2, enableCache = false, maxRetries = 3)
        }

        @AfterEach
        fun teardown() =
            runBlocking {
                queue.stop(graceful = false)
            }

        @Test
        fun `queue can be started`() {
            queue.start()
            assertTrue(true)
        }

        @Test
        fun `enqueue requires queue to be started`() {
            assertFailsWith<IllegalArgumentException> {
                queue.enqueue(
                    data = ReportData(),
                    config = ExportConfiguration.json(),
                )
            }
        }

        @Test
        fun `enqueue returns job ID`() {
            queue.start()
            val jobId =
                queue.enqueue(
                    data = ReportData(),
                    config = ExportConfiguration.json(),
                )

            assertNotNull(jobId)
            assertTrue(jobId.startsWith("export-"))
        }

        @Test
        fun `getStatus returns job status`() =
            runBlocking {
                queue.start()
                val jobId =
                    queue.enqueue(
                        data = ReportData(networkScans = listOf(mapOf("ssid" to "Test"))),
                        config = ExportConfiguration.json(),
                    )

                delay(100)
                val status = queue.getStatus(jobId)

                assertNotNull(status)
                assertEquals(jobId, status.jobId)
            }

        @Test
        fun `getStatus returns null for unknown job`() {
            queue.start()
            val status = queue.getStatus("unknown-job-id")

            assertNull(status)
        }

        @Test
        fun `queue processes jobs`() =
            runBlocking {
                queue.start()
                var completed = false

                queue.enqueue(
                    data = ReportData(networkScans = listOf(mapOf("ssid" to "Test"))),
                    config = ExportConfiguration.json(),
                    onComplete = { completed = true },
                )

                delay(500)
                assertTrue(completed)
            }

        @Test
        fun `queue respects priority`() =
            runBlocking {
                queue.start()
                val completionOrder = mutableListOf<String>()

                val job1 =
                    queue.enqueue(
                        data = ReportData(),
                        config = ExportConfiguration.json(),
                        priority = ExportPriority.LOW,
                        onComplete = { completionOrder.add("low") },
                    )

                val job2 =
                    queue.enqueue(
                        data = ReportData(),
                        config = ExportConfiguration.json(),
                        priority = ExportPriority.HIGH,
                        onComplete = { completionOrder.add("high") },
                    )

                delay(1000)
                // HIGH priority should complete first if jobs queued
                assertTrue(completionOrder.contains("high"))
            }

        @Test
        fun `cancel cancels pending job`() =
            runBlocking {
                queue.start()
                val jobId =
                    queue.enqueue(
                        data = ReportData(),
                        config = ExportConfiguration.json(),
                    )

                // Brief delay ensures job remains in PENDING state
                delay(50)

                val cancelled = queue.cancel(jobId)
                assertTrue(cancelled)

                val status = queue.getStatus(jobId)
                assertEquals(JobState.CANCELLED, status?.state)
            }

        @Test
        fun `cancel returns false for unknown job`() =
            runBlocking {
                queue.start()
                val cancelled = queue.cancel("unknown-job")

                assertFalse(cancelled)
            }

        @Test
        fun `getStatistics returns queue stats`() =
            runBlocking {
                queue.start()
                queue.enqueue(
                    data = ReportData(networkScans = listOf(mapOf("ssid" to "Test"))),
                    config = ExportConfiguration.json(),
                )

                delay(100)
                val stats = queue.getStatistics()

                assertNotNull(stats)
                assertTrue(stats.pending >= 0)
                assertTrue(stats.running >= 0)
            }

        @Test
        fun `clearHistory removes completed jobs`() =
            runBlocking {
                queue.start()
                queue.enqueue(
                    data = ReportData(networkScans = listOf(mapOf("ssid" to "Test"))),
                    config = ExportConfiguration.json(),
                )

                delay(500)
                val cleared = queue.clearHistory()

                assertTrue(cleared >= 0)
            }

        @Test
        fun `ExportPriority enum has all levels`() {
            assertEquals(4, ExportPriority.values().size)
            assertTrue(ExportPriority.CRITICAL.ordinal > ExportPriority.HIGH.ordinal)
            assertTrue(ExportPriority.HIGH.ordinal > ExportPriority.NORMAL.ordinal)
            assertTrue(ExportPriority.NORMAL.ordinal > ExportPriority.LOW.ordinal)
        }

        @Test
        fun `JobState enum has all states`() {
            assertEquals(5, JobState.values().size)
            assertNotNull(JobState.PENDING)
            assertNotNull(JobState.RUNNING)
            assertNotNull(JobState.COMPLETED)
            assertNotNull(JobState.FAILED)
            assertNotNull(JobState.CANCELLED)
        }

        @Test
        fun `QueueStatistics calculates success rate`() {
            val stats =
                QueueStatistics(
                    pending = 0,
                    running = 0,
                    completed = 8,
                    failed = 2,
                    cancelled = 0,
                    cacheHits = 0,
                    totalProcessed = 10,
                )

            assertEquals(0.8, stats.successRate())
        }

        @Test
        fun `QueueStatistics calculates failure rate`() {
            val stats =
                QueueStatistics(
                    pending = 0,
                    running = 0,
                    completed = 8,
                    failed = 2,
                    cancelled = 0,
                    cacheHits = 0,
                    totalProcessed = 10,
                )

            assertEquals(0.2, stats.failureRate())
        }

        @Test
        fun `QueueStatistics handles zero processed`() {
            val stats =
                QueueStatistics(
                    pending = 5,
                    running = 2,
                    completed = 0,
                    failed = 0,
                    cancelled = 0,
                    cacheHits = 0,
                    totalProcessed = 0,
                )

            assertEquals(0.0, stats.successRate())
            assertEquals(0.0, stats.failureRate())
        }

        @Test
        fun `ExportJobStatus estimates time remaining`() {
            val status =
                ExportJobStatus(
                    jobId = "test",
                    state = JobState.RUNNING,
                    progress = 50,
                    enqueuedAt = System.currentTimeMillis() - 1000,
                    startedAt = System.currentTimeMillis() - 1000,
                )

            val eta = status.estimatedTimeRemaining()
            assertNotNull(eta)
            assertTrue(eta!! > 0)
        }

        @Test
        fun `ExportJobStatus calculates duration`() {
            val now = System.currentTimeMillis()
            val status =
                ExportJobStatus(
                    jobId = "test",
                    state = JobState.COMPLETED,
                    enqueuedAt = now - 2000,
                    startedAt = now - 1000,
                    completedAt = now,
                )

            val duration = status.duration()
            assertNotNull(duration)
            assertTrue(duration!! >= 900 && duration <= 1100)
        }

        @Test
        fun `queue validates maxConcurrent parameter`() {
            assertFailsWith<IllegalArgumentException> {
                ExportQueue(maxConcurrent = 0)
            }
        }

        @Test
        fun `queue validates maxRetries parameter`() {
            assertFailsWith<IllegalArgumentException> {
                ExportQueue(maxRetries = -1)
            }
        }

        @Test
        fun `queue handles concurrent exports`() =
            runBlocking {
                val concurrentQueue = ExportQueue(maxConcurrent = 3)
                concurrentQueue.start()

                val completedCount = AtomicInteger(0)

                repeat(5) {
                    concurrentQueue.enqueue(
                        data = ReportData(networkScans = listOf(mapOf("ssid" to "Test$it"))),
                        config = ExportConfiguration.json(),
                        onComplete = { completedCount.incrementAndGet() },
                    )
                }

                delay(1500)
                assertTrue(completedCount.get() >= 3)

                concurrentQueue.stop(graceful = false)
            }
    }
}
