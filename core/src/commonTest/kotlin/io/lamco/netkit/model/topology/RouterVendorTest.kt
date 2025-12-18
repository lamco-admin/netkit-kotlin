package io.lamco.netkit.model.topology

import org.junit.jupiter.api.Test
import kotlin.test.*

class RouterVendorTest {
    @Test
    fun `all vendors have valid properties`() {
        RouterVendor.entries.forEach { vendor ->
            assertNotNull(vendor.displayName)
            assertTrue(vendor.displayName.isNotBlank())
            assertTrue(vendor.reliability in 0.0..1.0, "Reliability for $vendor should be 0.0-1.0")
        }
    }

    @Test
    fun `enterprise vendors support multi-AP`() {
        assertTrue(RouterVendor.CISCO.supportsMultiAp)
        assertTrue(RouterVendor.UBIQUITI.supportsMultiAp)
        assertTrue(RouterVendor.ARUBA.supportsMultiAp)
        assertTrue(RouterVendor.RUCKUS.supportsMultiAp)
    }

    @Test
    fun `consumer mesh vendors support multi-AP`() {
        assertTrue(RouterVendor.GOOGLE.supportsMultiAp)
        assertTrue(RouterVendor.AMAZON_EERO.supportsMultiAp)
        assertTrue(RouterVendor.NETGEAR.supportsMultiAp)
        assertTrue(RouterVendor.TP_LINK.supportsMultiAp)
    }

    @Test
    fun `enterprise vendors support fast roaming`() {
        assertTrue(RouterVendor.CISCO.supportsFastRoaming)
        assertTrue(RouterVendor.UBIQUITI.supportsFastRoaming)
        assertTrue(RouterVendor.ARUBA.supportsFastRoaming)
        assertTrue(RouterVendor.RUCKUS.supportsFastRoaming)
    }

    @Test
    fun `mesh-focused vendors support fast roaming`() {
        assertTrue(RouterVendor.GOOGLE.supportsFastRoaming)
        assertTrue(RouterVendor.AMAZON_EERO.supportsFastRoaming)
    }

    @Test
    fun `unknown vendor has zero reliability`() {
        assertEquals(0.0, RouterVendor.UNKNOWN.reliability)
    }

    @Test
    fun `unknown vendor does not support multi-AP or fast roaming`() {
        assertFalse(RouterVendor.UNKNOWN.supportsMultiAp)
        assertFalse(RouterVendor.UNKNOWN.supportsFastRoaming)
    }

    @Test
    fun `deployment types are correct for enterprise vendors`() {
        assertEquals(DeploymentType.ENTERPRISE, RouterVendor.CISCO.deploymentType)
        assertEquals(DeploymentType.ENTERPRISE, RouterVendor.UBIQUITI.deploymentType)
        assertEquals(DeploymentType.ENTERPRISE, RouterVendor.ARUBA.deploymentType)
        assertEquals(DeploymentType.ENTERPRISE, RouterVendor.RUCKUS.deploymentType)
    }

    @Test
    fun `deployment types are correct for consumer mesh`() {
        assertEquals(DeploymentType.CONSUMER_MESH, RouterVendor.GOOGLE.deploymentType)
        assertEquals(DeploymentType.CONSUMER_MESH, RouterVendor.AMAZON_EERO.deploymentType)
        assertEquals(DeploymentType.CONSUMER_MESH, RouterVendor.NETGEAR.deploymentType)
    }

    @Test
    fun `deployment types are correct for consumer single`() {
        assertEquals(DeploymentType.CONSUMER_SINGLE, RouterVendor.APPLE.deploymentType)
        assertEquals(DeploymentType.CONSUMER_SINGLE, RouterVendor.MOTOROLA.deploymentType)
    }

    @Test
    fun `fromOui returns correct vendor for known OUI`() {
        assertEquals(RouterVendor.CISCO, RouterVendor.fromOui("001D:AB:CD"))
        assertEquals(RouterVendor.UBIQUITI, RouterVendor.fromOui("F09F:12:34"))
        assertEquals(RouterVendor.GOOGLE, RouterVendor.fromOui("F4F26D:56:78"))
        assertEquals(RouterVendor.NETGEAR, RouterVendor.fromOui("1062EB:56:78"))
    }

    @Test
    fun `fromOui handles OUI without colons`() {
        assertEquals(RouterVendor.CISCO, RouterVendor.fromOui("001DAB"))
        assertEquals(RouterVendor.UBIQUITI, RouterVendor.fromOui("F09F12"))
    }

    @Test
    fun `fromOui is case-insensitive`() {
        assertEquals(RouterVendor.CISCO, RouterVendor.fromOui("001d:ab:cd"))
        assertEquals(RouterVendor.CISCO, RouterVendor.fromOui("001D:AB:CD"))
    }

    @Test
    fun `fromOui returns UNKNOWN for unrecognized OUI`() {
        assertEquals(RouterVendor.UNKNOWN, RouterVendor.fromOui("ZZ:ZZ:ZZ"))
        assertEquals(RouterVendor.UNKNOWN, RouterVendor.fromOui("99:99:99"))
    }

    @Test
    fun `fromWpsInfo detects vendor from device name`() {
        assertEquals(RouterVendor.CISCO, RouterVendor.fromWpsInfo("Cisco Router", null))
        assertEquals(RouterVendor.GOOGLE, RouterVendor.fromWpsInfo("Google Wifi", null))
        assertEquals(RouterVendor.AMAZON_EERO, RouterVendor.fromWpsInfo("eero Pro", null))
    }

    @Test
    fun `fromWpsInfo detects vendor from manufacturer`() {
        assertEquals(RouterVendor.UBIQUITI, RouterVendor.fromWpsInfo(null, "Ubiquiti Networks"))
        assertEquals(RouterVendor.NETGEAR, RouterVendor.fromWpsInfo(null, "NETGEAR Inc"))
    }

    @Test
    fun `fromWpsInfo detects vendor from mesh product names`() {
        assertEquals(RouterVendor.UBIQUITI, RouterVendor.fromWpsInfo("UniFi AP AC Pro", null))
        assertEquals(RouterVendor.NETGEAR, RouterVendor.fromWpsInfo("Orbi RBK50", null))
        assertEquals(RouterVendor.TP_LINK, RouterVendor.fromWpsInfo("Deco M5", null))
    }

    @Test
    fun `fromWpsInfo is case-insensitive`() {
        assertEquals(RouterVendor.CISCO, RouterVendor.fromWpsInfo("cisco router", null))
        assertEquals(RouterVendor.GOOGLE, RouterVendor.fromWpsInfo("GOOGLE WIFI", null))
    }

    @Test
    fun `fromWpsInfo returns UNKNOWN for unrecognized names`() {
        assertEquals(RouterVendor.UNKNOWN, RouterVendor.fromWpsInfo("Unknown Brand", null))
        assertEquals(RouterVendor.UNKNOWN, RouterVendor.fromWpsInfo(null, null))
        assertEquals(RouterVendor.UNKNOWN, RouterVendor.fromWpsInfo("", ""))
    }

    @Test
    fun `high reliability vendors have reliability at least 0 point 85`() {
        assertTrue(RouterVendor.CISCO.reliability >= 0.85)
        assertTrue(RouterVendor.UBIQUITI.reliability >= 0.85)
        assertTrue(RouterVendor.GOOGLE.reliability >= 0.85)
        assertTrue(RouterVendor.APPLE.reliability >= 0.85)
    }
}
