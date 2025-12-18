package io.lamco.netkit.advisor.knowledge

import io.lamco.netkit.advisor.model.StandardCategory
import io.lamco.netkit.advisor.model.StandardsReference
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class StandardsDatabaseTest {

    @Test
    fun `create standards database`() {
        val standard = createTestStandard()
        val db = StandardsDatabase(listOf(standard))

        assertNotNull(db)
    }

    @Test
    fun `empty standards throws exception`() {
        assertThrows<IllegalArgumentException> {
            StandardsDatabase(emptyList())
        }
    }

    @Test
    fun `get standards by category`() {
        val db = StandardsDatabase.create()
        val wifiStandards = db.getStandardsByCategory(StandardCategory.IEEE_WIFI)

        assertTrue(wifiStandards.isNotEmpty())
        assertTrue(wifiStandards.all { it.category == StandardCategory.IEEE_WIFI })
    }

    @Test
    fun `get standard by ID`() {
        val db = StandardsDatabase.create()
        val standard = db.getStandard("WPA2")

        assertNotNull(standard)
        assertTrue(standard.id.contains("WPA2", ignoreCase = true))
    }

    @Test
    fun `search standards by keyword`() {
        val db = StandardsDatabase.create()
        val results = db.search("WiFi")

        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `get WiFi standards convenience method`() {
        val db = StandardsDatabase.create()
        val wifiStandards = db.getWiFiStandards()

        assertTrue(wifiStandards.isNotEmpty())
    }

    @Test
    fun `get security standards convenience method`() {
        val db = StandardsDatabase.create()
        val securityStandards = db.getSecurityStandards()

        assertTrue(securityStandards.isNotEmpty())
    }

    @Test
    fun `get compliance standards convenience method`() {
        val db = StandardsDatabase.create()
        val complianceStandards = db.getComplianceStandards()

        assertTrue(complianceStandards.isNotEmpty())
    }

    @Test
    fun `get summary returns statistics`() {
        val db = StandardsDatabase.create()
        val summary = db.getSummary()

        assertTrue(summary.totalStandards > 0)
        assertTrue(summary.byCategory.isNotEmpty())
    }

    @Test
    fun `standard category display names`() {
        assertEquals("IEEE WiFi Standards", StandardCategory.IEEE_WIFI.displayName)
        assertEquals("WiFi Alliance", StandardCategory.WIFI_ALLIANCE.displayName)
        assertEquals("Security Standards", StandardCategory.SECURITY.displayName)
    }

    private fun createTestStandard(): StandardsReference {
        return StandardsReference(
            id = "TEST-001",
            name = "Test Standard",
            description = "Test description",
            category = StandardCategory.IEEE_WIFI
        )
    }
}
