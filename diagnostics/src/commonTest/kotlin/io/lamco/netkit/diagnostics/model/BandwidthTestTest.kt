package io.lamco.netkit.diagnostics.model

import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class BandwidthTestTest {
    @Test
    fun `create valid bandwidth test with download and upload`() {
        val test =
            BandwidthTest(
                downloadMbps = 85.3,
                uploadMbps = 42.7,
                downloadBytes = 106_625_000,
                uploadBytes = 53_375_000,
                testDuration = 10.seconds,
                serverHost = "speedtest.example.com",
            )

        assertEquals(85.3, test.downloadMbps)
        assertEquals(42.7, test.uploadMbps)
        assertTrue(test.isSuccessful)
        assertFalse(test.isFailed)
    }

    @Test
    fun `reject blank server host`() {
        assertFailsWith<IllegalArgumentException> {
            BandwidthTest(
                downloadMbps = 85.0,
                testDuration = 10.seconds,
                serverHost = "",
            )
        }
    }

    @Test
    fun `reject negative download speed`() {
        assertFailsWith<IllegalArgumentException> {
            BandwidthTest(
                downloadMbps = -5.0,
                testDuration = 10.seconds,
                serverHost = "test.server",
            )
        }
    }

    @Test
    fun `reject zero or negative test duration`() {
        assertFailsWith<IllegalArgumentException> {
            BandwidthTest(
                downloadMbps = 85.0,
                testDuration = 0.seconds,
                serverHost = "test.server",
            )
        }
    }

    @Test
    fun `download only test requires download speed`() {
        assertFailsWith<IllegalArgumentException> {
            BandwidthTest(
                downloadMbps = null,
                uploadMbps = 42.0,
                testDuration = 10.seconds,
                serverHost = "test.server",
                testType = BandwidthTestType.DOWNLOAD_ONLY,
            )
        }
    }

    @Test
    fun `upload only test requires upload speed`() {
        assertFailsWith<IllegalArgumentException> {
            BandwidthTest(
                downloadMbps = 85.0,
                uploadMbps = null,
                testDuration = 10.seconds,
                serverHost = "test.server",
                testType = BandwidthTestType.UPLOAD_ONLY,
            )
        }
    }

    @Test
    fun `bidirectional test requires both speeds`() {
        assertFailsWith<IllegalArgumentException> {
            BandwidthTest(
                downloadMbps = 85.0,
                uploadMbps = null,
                testDuration = 10.seconds,
                serverHost = "test.server",
                testType = BandwidthTestType.BIDIRECTIONAL,
            )
        }
    }

    @Test
    fun `download quality EXCELLENT for 100+ Mbps`() {
        val test =
            BandwidthTest(
                downloadMbps = 150.0,
                testDuration = 10.seconds,
                serverHost = "test.server",
            )

        assertEquals(BandwidthQuality.EXCELLENT, test.downloadQuality)
    }

    @Test
    fun `download quality CRITICAL for under 10 Mbps`() {
        val test =
            BandwidthTest(
                downloadMbps = 5.0,
                testDuration = 10.seconds,
                serverHost = "test.server",
            )

        assertEquals(BandwidthQuality.CRITICAL, test.downloadQuality)
    }

    @Test
    fun `overall quality is worst of download and upload`() {
        val test =
            BandwidthTest(
                downloadMbps = 150.0, // EXCELLENT
                uploadMbps = 8.0, // CRITICAL
                testDuration = 10.seconds,
                serverHost = "test.server",
            )

        assertEquals(BandwidthQuality.CRITICAL, test.overallQuality)
    }

    @Test
    fun `calculate efficiency percentage`() {
        val test =
            BandwidthTest(
                downloadMbps = 85.0,
                testDuration = 10.seconds,
                serverHost = "test.server",
            )

        val efficiency = test.calculateEfficiency(theoreticalMaxMbps = 100.0)
        assertEquals(85.0, efficiency)
    }

    @Test
    fun `meets requirements when speeds exceed minimums`() {
        val test =
            BandwidthTest(
                downloadMbps = 85.0,
                uploadMbps = 42.0,
                testDuration = 10.seconds,
                serverHost = "test.server",
            )

        assertTrue(test.meetsRequirements(minDownloadMbps = 50.0, minUploadMbps = 25.0))
        assertFalse(test.meetsRequirements(minDownloadMbps = 100.0, minUploadMbps = 50.0))
    }

    @Test
    fun `average speed calculation`() {
        val test =
            BandwidthTest(
                downloadMbps = 100.0,
                uploadMbps = 50.0,
                testDuration = 10.seconds,
                serverHost = "test.server",
            )

        assertEquals(75.0, test.averageSpeedMbps)
    }

    @Test
    fun `failed factory creates failed test`() {
        val test =
            BandwidthTest.failed(
                serverHost = "unreachable.server",
                reason = "Connection timeout",
                testDuration = 5.seconds,
            )

        assertEquals("unreachable.server", test.serverHost)
        assertNull(test.downloadMbps)
        assertNull(test.uploadMbps)
        assertEquals("Connection timeout", test.failureReason)
        assertTrue(test.isFailed)
    }

    @Test
    fun `summary includes speeds and quality`() {
        val test =
            BandwidthTest(
                downloadMbps = 85.3,
                uploadMbps = 42.7,
                testDuration = 10.seconds,
                serverHost = "speedtest.example.com",
            )

        val summary = test.summary()
        assertTrue(summary.contains("85.3") || summary.contains("85.30"))
        assertTrue(summary.contains("42.7") || summary.contains("42.70"))
        assertTrue(summary.contains("speedtest.example.com"))
    }
}
