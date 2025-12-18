package io.lamco.netkit.optimizer.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive tests for EnterpriseConfig and related models
 */
class EnterpriseConfigTest {
    // ========================================
    // EnterpriseConfig Tests
    // ========================================

    @Test
    fun `create config with valid parameters succeeds`() {
        val config =
            EnterpriseConfig(
                ssid = "CorporateWiFi",
                has8021X = true,
                hasVlanSupport = true,
                enterpriseFeatures =
                    setOf(
                        EnterpriseFeature.RADIUS_AUTHENTICATION,
                        EnterpriseFeature.VLAN_SEGMENTATION,
                    ),
            )

        assertEquals("CorporateWiFi", config.ssid)
        assertTrue(config.has8021X)
        assertTrue(config.hasVlanSupport)
    }

    @Test
    fun `create config with blank SSID throws exception`() {
        assertThrows<IllegalArgumentException> {
            EnterpriseConfig(ssid = "")
        }
    }

    @Test
    fun `isEnterpriseGrade is true with 802_1X`() {
        val config =
            EnterpriseConfig(
                ssid = "Corp",
                has8021X = true,
            )

        assertTrue(config.isEnterpriseGrade)
    }

    @Test
    fun `isEnterpriseGrade is true with VLAN support`() {
        val config =
            EnterpriseConfig(
                ssid = "Corp",
                hasVlanSupport = true,
            )

        assertTrue(config.isEnterpriseGrade)
    }

    @Test
    fun `isEnterpriseGrade is true with multiple features`() {
        val config =
            EnterpriseConfig(
                ssid = "Corp",
                enterpriseFeatures =
                    setOf(
                        EnterpriseFeature.FAST_ROAMING,
                        EnterpriseFeature.BAND_STEERING,
                    ),
            )

        assertTrue(config.isEnterpriseGrade)
    }

    @Test
    fun `isEnterpriseGrade is false for consumer network`() {
        val config = EnterpriseConfig(ssid = "HomeWiFi")
        assertFalse(config.isEnterpriseGrade)
    }

    @Test
    fun `enterpriseSecurityScore is 40 for 802_1X only`() {
        val config =
            EnterpriseConfig(
                ssid = "Corp",
                has8021X = true,
            )

        assertEquals(40, config.enterpriseSecurityScore)
    }

    @Test
    fun `enterpriseSecurityScore is 20 for VLAN only`() {
        val config =
            EnterpriseConfig(
                ssid = "Corp",
                hasVlanSupport = true,
            )

        assertEquals(20, config.enterpriseSecurityScore)
    }

    @Test
    fun `enterpriseSecurityScore increases with features`() {
        val config =
            EnterpriseConfig(
                ssid = "Corp",
                has8021X = true,
                hasVlanSupport = true,
                enterpriseFeatures =
                    setOf(
                        EnterpriseFeature.RADIUS_AUTHENTICATION,
                        EnterpriseFeature.FAST_ROAMING,
                    ),
            )

        assertTrue(config.enterpriseSecurityScore >= 60)
    }

    @Test
    fun `enterpriseSecurityScore bonus for guest with captive portal`() {
        val config =
            EnterpriseConfig(
                ssid = "Guest",
                isGuestNetwork = true,
                hasCaptivePortal = true,
            )

        assertEquals(25, config.enterpriseSecurityScore) // 15 + 10
    }

    @Test
    fun `deploymentTier is FULL_ENTERPRISE for complete setup`() {
        val config =
            EnterpriseConfig(
                ssid = "Corp",
                has8021X = true,
                hasVlanSupport = true,
                enterpriseFeatures =
                    setOf(
                        EnterpriseFeature.RADIUS_AUTHENTICATION,
                        EnterpriseFeature.VLAN_SEGMENTATION,
                        EnterpriseFeature.FAST_ROAMING,
                    ),
            )

        assertEquals(EnterpriseDeploymentTier.FULL_ENTERPRISE, config.deploymentTier)
    }

    @Test
    fun `deploymentTier is PARTIAL_ENTERPRISE for 802_1X only`() {
        val config =
            EnterpriseConfig(
                ssid = "Corp",
                has8021X = true,
            )

        assertEquals(EnterpriseDeploymentTier.PARTIAL_ENTERPRISE, config.deploymentTier)
    }

    @Test
    fun `deploymentTier is GUEST_CAPABLE for guest network`() {
        val config =
            EnterpriseConfig(
                ssid = "Guest",
                isGuestNetwork = true,
            )

        assertEquals(EnterpriseDeploymentTier.GUEST_CAPABLE, config.deploymentTier)
    }

    @Test
    fun `deploymentTier is CONSUMER for standard network`() {
        val config = EnterpriseConfig(ssid = "HomeWiFi")
        assertEquals(EnterpriseDeploymentTier.CONSUMER, config.deploymentTier)
    }

    @Test
    fun `summary contains key information`() {
        val config =
            EnterpriseConfig(
                ssid = "CorporateWiFi",
                has8021X = true,
                hasVlanSupport = true,
                isGuestNetwork = false,
                hasCaptivePortal = false,
            )

        val summary = config.summary
        assertTrue(summary.contains("CorporateWiFi"))
        assertTrue(summary.contains("802.1X"))
        assertTrue(summary.contains("VLAN"))
    }

    // ========================================
    // VlanConfig Tests
    // ========================================

    @Test
    fun `create VLAN config with valid ID succeeds`() {
        val vlan =
            VlanConfig(
                vlanId = 10,
                purpose = VlanPurpose.CORPORATE,
            )

        assertEquals(10, vlan.vlanId)
        assertTrue(vlan.hasKnownId)
    }

    @Test
    fun `create VLAN config without ID succeeds`() {
        val vlan =
            VlanConfig(
                vlanId = null,
                purpose = VlanPurpose.GUEST,
            )

        assertFalse(vlan.hasKnownId)
    }

    @Test
    fun `create VLAN config with ID 0 throws exception`() {
        assertThrows<IllegalArgumentException> {
            VlanConfig(
                vlanId = 0,
                purpose = VlanPurpose.CORPORATE,
            )
        }
    }

    @Test
    fun `create VLAN config with ID 4095 throws exception`() {
        assertThrows<IllegalArgumentException> {
            VlanConfig(
                vlanId = 4095,
                purpose = VlanPurpose.CORPORATE,
            )
        }
    }

    @Test
    fun `VlanPurpose has typical SSID patterns`() {
        assertTrue(VlanPurpose.CORPORATE.typicalSsidPatterns.contains("corp"))
        assertTrue(VlanPurpose.GUEST.typicalSsidPatterns.contains("guest"))
        assertTrue(VlanPurpose.IOT.typicalSsidPatterns.contains("iot"))
        assertTrue(VlanPurpose.VOICE.typicalSsidPatterns.contains("voip"))
    }

    // ========================================
    // Dot1XConfiguration Tests
    // ========================================

    @Test
    fun `Dot1X disabled has NONE security strength`() {
        val config = Dot1XConfiguration(isEnabled = false)
        assertEquals(Dot1XSecurityStrength.NONE, config.securityStrength)
    }

    @Test
    fun `Dot1X with certificate has VERY_STRONG security`() {
        val config =
            Dot1XConfiguration(
                isEnabled = true,
                requiresCertificate = true,
            )

        assertEquals(Dot1XSecurityStrength.VERY_STRONG, config.securityStrength)
    }

    @Test
    fun `Dot1X with secure EAP has STRONG security`() {
        val config =
            Dot1XConfiguration(
                isEnabled = true,
                eapMethods = setOf(EapMethod.EAP_TLS, EapMethod.PEAP),
            )

        assertEquals(Dot1XSecurityStrength.STRONG, config.securityStrength)
    }

    @Test
    fun `Dot1X with weak EAP has MODERATE security`() {
        val config =
            Dot1XConfiguration(
                isEnabled = true,
                eapMethods = setOf(EapMethod.EAP_MD5),
            )

        assertEquals(Dot1XSecurityStrength.MODERATE, config.securityStrength)
    }

    @Test
    fun `EAP-TLS is secure`() {
        assertTrue(EapMethod.EAP_TLS.isSecure)
    }

    @Test
    fun `EAP-MD5 is not secure`() {
        assertFalse(EapMethod.EAP_MD5.isSecure)
    }

    @Test
    fun `LEAP is not secure`() {
        assertFalse(EapMethod.LEAP.isSecure)
    }

    // ========================================
    // CaptivePortalConfig Tests
    // ========================================

    @Test
    fun `captive portal not detected has NONE complexity`() {
        val config = CaptivePortalConfig(isDetected = false)
        assertEquals(PortalComplexity.NONE, config.complexity)
    }

    @Test
    fun `captive portal with auth and terms has COMPLEX complexity`() {
        val config =
            CaptivePortalConfig(
                isDetected = true,
                requiresAuth = true,
                requiresTerms = true,
            )

        assertEquals(PortalComplexity.COMPLEX, config.complexity)
    }

    @Test
    fun `captive portal with auth only has MODERATE complexity`() {
        val config =
            CaptivePortalConfig(
                isDetected = true,
                requiresAuth = true,
                requiresTerms = false,
            )

        assertEquals(PortalComplexity.MODERATE, config.complexity)
    }

    @Test
    fun `captive portal with terms only has MODERATE complexity`() {
        val config =
            CaptivePortalConfig(
                isDetected = true,
                requiresAuth = false,
                requiresTerms = true,
            )

        assertEquals(PortalComplexity.MODERATE, config.complexity)
    }

    @Test
    fun `captive portal click-through has SIMPLE complexity`() {
        val config =
            CaptivePortalConfig(
                isDetected = true,
                portalType = CaptivePortalType.CLICK_THROUGH,
            )

        assertEquals(PortalComplexity.SIMPLE, config.complexity)
    }

    // ========================================
    // GuestIsolationValidation Tests
    // ========================================

    @Test
    fun `isolated guest network has positive isolation score`() {
        val validation =
            GuestIsolationValidation(
                isIsolated = true,
                isolationMethods =
                    setOf(
                        IsolationMethod.VLAN_SEPARATION,
                        IsolationMethod.CLIENT_ISOLATION,
                    ),
            )

        assertTrue(validation.isolationScore >= 60)
    }

    @Test
    fun `non-isolated network has zero isolation score`() {
        val validation = GuestIsolationValidation(isIsolated = false)
        assertEquals(0, validation.isolationScore)
    }

    @Test
    fun `isolation score increases with methods`() {
        val basic =
            GuestIsolationValidation(
                isIsolated = true,
                isolationMethods = setOf(IsolationMethod.CLIENT_ISOLATION),
            )

        val advanced =
            GuestIsolationValidation(
                isIsolated = true,
                isolationMethods =
                    setOf(
                        IsolationMethod.VLAN_SEPARATION,
                        IsolationMethod.CLIENT_ISOLATION,
                        IsolationMethod.FIREWALL,
                    ),
            )

        assertTrue(advanced.isolationScore > basic.isolationScore)
    }

    @Test
    fun `isolation score decreases with security issues`() {
        val withIssues =
            GuestIsolationValidation(
                isIsolated = true,
                isolationMethods = setOf(IsolationMethod.CLIENT_ISOLATION),
                securityIssues = listOf("Weak encryption", "No firewall"),
            )

        val withoutIssues =
            GuestIsolationValidation(
                isIsolated = true,
                isolationMethods = setOf(IsolationMethod.CLIENT_ISOLATION),
            )

        assertTrue(withIssues.isolationScore < withoutIssues.isolationScore)
    }

    // ========================================
    // Enterprise Feature Enum Tests
    // ========================================

    @Test
    fun `all enterprise features have display names`() {
        EnterpriseFeature.entries.forEach { feature ->
            assertTrue(feature.displayName.isNotBlank())
            assertTrue(feature.description.isNotBlank())
        }
    }

    @Test
    fun `all deployment tiers have descriptions`() {
        EnterpriseDeploymentTier.entries.forEach { tier ->
            assertTrue(tier.displayName.isNotBlank())
            assertTrue(tier.description.isNotBlank())
        }
    }

    @Test
    fun `all VLAN purposes have patterns`() {
        VlanPurpose.entries.forEach { purpose ->
            assertTrue(purpose.displayName.isNotBlank())
            // UNKNOWN has empty patterns, others should have patterns
            if (purpose != VlanPurpose.UNKNOWN) {
                assertTrue(purpose.typicalSsidPatterns.isNotEmpty())
            }
        }
    }

    @Test
    fun `all EAP methods have descriptions`() {
        EapMethod.entries.forEach { method ->
            assertTrue(method.displayName.isNotBlank())
            assertTrue(method.description.isNotBlank())
        }
    }

    @Test
    fun `all captive portal types have display names`() {
        CaptivePortalType.entries.forEach { type ->
            assertTrue(type.displayName.isNotBlank())
        }
    }

    @Test
    fun `all isolation methods have display names`() {
        IsolationMethod.entries.forEach { method ->
            assertTrue(method.displayName.isNotBlank())
        }
    }
}
