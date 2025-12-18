package io.lamco.netkit.security.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Comprehensive tests for WPS risk assessment classes
 *
 * Validates:
 * - WpsInfo data model
 * - WpsConfigMethod enumeration
 * - WpsState enumeration
 * - WpsRiskScore calculation
 * - WpsIssue detection
 * - Risk level categorization
 */
class WpsRiskTest {

    // ============================================
    // WpsInfo TESTS
    // ============================================

    @Test
    fun `create WpsInfo with all fields`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.PUSH_BUTTON, WpsConfigMethod.LABEL),
            wpsState = WpsState.CONFIGURED,
            locked = false,
            deviceName = "Router-5G",
            manufacturer = "TestCorp",
            modelName = "AC1900",
            version = "2.0"
        )

        assertEquals(2, wpsInfo.configMethods.size)
        assertEquals(WpsState.CONFIGURED, wpsInfo.wpsState)
        assertFalse(wpsInfo.locked!!)
        assertEquals("Router-5G", wpsInfo.deviceName)
        assertEquals("TestCorp", wpsInfo.manufacturer)
        assertEquals("AC1900", wpsInfo.modelName)
        assertEquals("2.0", wpsInfo.version)
    }

    @Test
    fun `supportsPinMethod true when PIN methods present`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.LABEL, WpsConfigMethod.PUSH_BUTTON),
            wpsState = WpsState.CONFIGURED,
            locked = false,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        assertTrue(wpsInfo.supportsPinMethod)
    }

    @Test
    fun `supportsPinMethod false when only push-button`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.PUSH_BUTTON),
            wpsState = WpsState.CONFIGURED,
            locked = false,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        assertFalse(wpsInfo.supportsPinMethod)
    }

    @Test
    fun `isPushButtonOnly true for push-button only config`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.PUSH_BUTTON),
            wpsState = WpsState.CONFIGURED,
            locked = false,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        assertTrue(wpsInfo.isPushButtonOnly)
    }

    @Test
    fun `isPushButtonOnly false when PIN methods present`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.PUSH_BUTTON, WpsConfigMethod.DISPLAY),
            wpsState = WpsState.CONFIGURED,
            locked = false,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        assertFalse(wpsInfo.isPushButtonOnly)
    }

    @Test
    fun `isDisabled true for NOT_CONFIGURED with no methods`() {
        val wpsInfo = WpsInfo(
            configMethods = emptySet(),
            wpsState = WpsState.NOT_CONFIGURED,
            locked = null,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        assertTrue(wpsInfo.isDisabled)
    }

    @Test
    fun `isConfigured true for CONFIGURED state`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.PUSH_BUTTON),
            wpsState = WpsState.CONFIGURED,
            locked = false,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        assertTrue(wpsInfo.isConfigured)
    }

    @Test
    fun `isLocked true when locked is true`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.LABEL),
            wpsState = WpsState.CONFIGURED,
            locked = true,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        assertTrue(wpsInfo.isLocked)
    }

    @Test
    fun `statusSummary describes WPS state correctly`() {
        val disabled = WpsInfo(
            configMethods = emptySet(),
            wpsState = WpsState.NOT_CONFIGURED,
            locked = null,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )
        assertTrue(disabled.statusSummary.contains("Disabled", ignoreCase = true))

        val locked = WpsInfo(
            configMethods = setOf(WpsConfigMethod.LABEL),
            wpsState = WpsState.CONFIGURED,
            locked = true,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )
        assertTrue(locked.statusSummary.contains("Locked", ignoreCase = true))

        val pushButtonOnly = WpsInfo(
            configMethods = setOf(WpsConfigMethod.PUSH_BUTTON),
            wpsState = WpsState.CONFIGURED,
            locked = false,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )
        assertTrue(pushButtonOnly.statusSummary.contains("Push-button", ignoreCase = true))
    }

    // ============================================
    // WpsConfigMethod TESTS
    // ============================================

    @Test
    fun `WpsConfigMethod PIN methods have usesPin true`() {
        assertTrue(WpsConfigMethod.LABEL.usesPin)
        assertTrue(WpsConfigMethod.DISPLAY.usesPin)
        assertTrue(WpsConfigMethod.KEYPAD.usesPin)
        assertTrue(WpsConfigMethod.VIRTUAL_DISPLAY.usesPin)
        assertTrue(WpsConfigMethod.PHYSICAL_DISPLAY.usesPin)
    }

    @Test
    fun `WpsConfigMethod non-PIN methods have usesPin false`() {
        assertFalse(WpsConfigMethod.PUSH_BUTTON.usesPin)
        assertFalse(WpsConfigMethod.USB.usesPin)
        assertFalse(WpsConfigMethod.ETHERNET.usesPin)
        assertFalse(WpsConfigMethod.NFC_INTERFACE.usesPin)
    }

    @Test
    fun `fromBitmask parses config methods correctly`() {
        // Label (0x0004) + Display (0x0008) = 0x000C
        val methods = WpsConfigMethod.fromBitmask(0x000C)
        assertTrue(methods.contains(WpsConfigMethod.LABEL))
        assertTrue(methods.contains(WpsConfigMethod.DISPLAY))
        assertEquals(2, methods.size)
    }

    @Test
    fun `fromBitmask with push-button bit`() {
        val methods = WpsConfigMethod.fromBitmask(0x0080)
        assertTrue(methods.contains(WpsConfigMethod.PUSH_BUTTON))
    }

    @Test
    fun `fromBitmask with zero returns empty set`() {
        val methods = WpsConfigMethod.fromBitmask(0x0000)
        assertTrue(methods.isEmpty())
    }

    // ============================================
    // WpsState TESTS
    // ============================================

    @Test
    fun `WpsState fromValue parses correctly`() {
        assertEquals(WpsState.NOT_CONFIGURED, WpsState.fromValue(1))
        assertEquals(WpsState.CONFIGURED, WpsState.fromValue(2))
    }

    @Test
    fun `WpsState fromValue handles invalid values`() {
        assertEquals(WpsState.NOT_CONFIGURED, WpsState.fromValue(0))
        assertEquals(WpsState.NOT_CONFIGURED, WpsState.fromValue(99))
    }

    // ============================================
    // WpsRiskScore CONSTRUCTION
    // ============================================

    @Test
    fun `create WpsRiskScore with valid parameters`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.LABEL),
            wpsState = WpsState.CONFIGURED,
            locked = false,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        val riskScore = WpsRiskScore(
            bssid = "00:11:22:33:44:55",
            riskScore = 0.8,
            issues = listOf(WpsIssue.PinSupported, WpsIssue.UnlockedState),
            wpsInfo = wpsInfo
        )

        assertEquals("00:11:22:33:44:55", riskScore.bssid)
        assertEquals(0.8, riskScore.riskScore, 0.001)
        assertEquals(2, riskScore.issues.size)
        assertNotNull(riskScore.wpsInfo)
    }

    @Test
    fun `reject blank BSSID in WpsRiskScore`() {
        assertThrows<IllegalArgumentException> {
            WpsRiskScore(
                bssid = "",
                riskScore = 0.5,
                issues = emptyList(),
                wpsInfo = null
            )
        }
    }

    @Test
    fun `reject risk score out of range`() {
        assertThrows<IllegalArgumentException> {
            WpsRiskScore(
                bssid = "00:11:22:33:44:55",
                riskScore = 1.5,  // Invalid
                issues = emptyList(),
                wpsInfo = null
            )
        }

        assertThrows<IllegalArgumentException> {
            WpsRiskScore(
                bssid = "00:11:22:33:44:55",
                riskScore = -0.1,  // Invalid
                issues = emptyList(),
                wpsInfo = null
            )
        }
    }

    // ============================================
    // RISK SCORE CALCULATION
    // ============================================

    @Test
    fun `calculateRiskScore returns 0 point 0 for null WpsInfo`() {
        val score = WpsRiskScore.calculateRiskScore(null)
        assertEquals(0.0, score, 0.001)
    }

    @Test
    fun `calculateRiskScore returns 0 point 0 for disabled WPS`() {
        val wpsInfo = WpsInfo(
            configMethods = emptySet(),
            wpsState = WpsState.NOT_CONFIGURED,
            locked = null,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        val score = WpsRiskScore.calculateRiskScore(wpsInfo)
        assertEquals(0.0, score, 0.001)
    }

    @Test
    fun `calculateRiskScore returns 1 point 0 for PIN unlocked configured`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.LABEL),
            wpsState = WpsState.CONFIGURED,
            locked = false,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        val score = WpsRiskScore.calculateRiskScore(wpsInfo)
        assertEquals(1.0, score, 0.001)
    }

    @Test
    fun `calculateRiskScore returns 0 point 8 for PIN unlocked not configured`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.DISPLAY),
            wpsState = WpsState.NOT_CONFIGURED,
            locked = false,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        val score = WpsRiskScore.calculateRiskScore(wpsInfo)
        assertEquals(0.8, score, 0.001)
    }

    @Test
    fun `calculateRiskScore returns 0 point 6 for PIN locked`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.KEYPAD),
            wpsState = WpsState.CONFIGURED,
            locked = true,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        val score = WpsRiskScore.calculateRiskScore(wpsInfo)
        assertEquals(0.6, score, 0.001)
    }

    @Test
    fun `calculateRiskScore returns 0 point 4 for push-button only unlocked configured`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.PUSH_BUTTON),
            wpsState = WpsState.CONFIGURED,
            locked = false,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        val score = WpsRiskScore.calculateRiskScore(wpsInfo)
        assertEquals(0.4, score, 0.001)
    }

    @Test
    fun `calculateRiskScore returns 0 point 2 for push-button only locked`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.PUSH_BUTTON),
            wpsState = WpsState.CONFIGURED,
            locked = true,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        val score = WpsRiskScore.calculateRiskScore(wpsInfo)
        assertEquals(0.2, score, 0.001)
    }

    // ============================================
    // ISSUE DETECTION
    // ============================================

    @Test
    fun `detectIssues returns empty for null WpsInfo`() {
        val issues = WpsRiskScore.detectIssues(null)
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `detectIssues returns empty for disabled WPS`() {
        val wpsInfo = WpsInfo(
            configMethods = emptySet(),
            wpsState = WpsState.NOT_CONFIGURED,
            locked = null,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        val issues = WpsRiskScore.detectIssues(wpsInfo)
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `detectIssues detects PIN support`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.LABEL),
            wpsState = WpsState.NOT_CONFIGURED,
            locked = true,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        val issues = WpsRiskScore.detectIssues(wpsInfo)
        assertTrue(issues.any { it is WpsIssue.PinSupported })
    }

    @Test
    fun `detectIssues detects unlocked state`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.DISPLAY),
            wpsState = WpsState.CONFIGURED,
            locked = false,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        val issues = WpsRiskScore.detectIssues(wpsInfo)
        assertTrue(issues.any { it is WpsIssue.UnlockedState })
    }

    @Test
    fun `detectIssues detects configured but still active`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.PUSH_BUTTON),
            wpsState = WpsState.CONFIGURED,
            locked = false,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        val issues = WpsRiskScore.detectIssues(wpsInfo)
        assertTrue(issues.any { it is WpsIssue.ConfiguredButStillActive })
    }

    @Test
    fun `detectIssues detects unknown lock state`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.KEYPAD),
            wpsState = WpsState.CONFIGURED,
            locked = null,  // Unknown
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        val issues = WpsRiskScore.detectIssues(wpsInfo)
        assertTrue(issues.any { it is WpsIssue.UnknownLockState })
    }

    // ============================================
    // fromWpsInfo FACTORY METHOD
    // ============================================

    @Test
    fun `fromWpsInfo creates complete risk score`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.LABEL),
            wpsState = WpsState.CONFIGURED,
            locked = false,
            deviceName = "TestDevice",
            manufacturer = "TestCorp",
            modelName = "X100",
            version = "2.0"
        )

        val riskScore = WpsRiskScore.fromWpsInfo("AA:BB:CC:DD:EE:FF", wpsInfo)

        assertEquals("AA:BB:CC:DD:EE:FF", riskScore.bssid)
        assertEquals(1.0, riskScore.riskScore, 0.001)  // PIN unlocked configured = max risk
        assertTrue(riskScore.issues.any { it is WpsIssue.PinSupported })
        assertTrue(riskScore.issues.any { it is WpsIssue.UnlockedState })
        assertNotNull(riskScore.wpsInfo)
    }

    @Test
    fun `fromWpsInfo with null creates zero-risk score`() {
        val riskScore = WpsRiskScore.fromWpsInfo("00:11:22:33:44:55", null)

        assertEquals(0.0, riskScore.riskScore, 0.001)
        assertTrue(riskScore.issues.isEmpty())
        assertNull(riskScore.wpsInfo)
    }

    // ============================================
    // RISK LEVEL CATEGORIZATION
    // ============================================

    @Test
    fun `riskLevel CRITICAL for score above 0 point 9`() {
        val riskScore = WpsRiskScore(
            bssid = "00:11:22:33:44:55",
            riskScore = 1.0,
            issues = emptyList(),
            wpsInfo = null
        )

        assertEquals(RiskLevel.CRITICAL, riskScore.riskLevel)
    }

    @Test
    fun `riskLevel HIGH for score 0 point 6 to 0 point 89`() {
        val riskScore = WpsRiskScore(
            bssid = "00:11:22:33:44:55",
            riskScore = 0.7,
            issues = emptyList(),
            wpsInfo = null
        )

        assertEquals(RiskLevel.HIGH, riskScore.riskLevel)
    }

    @Test
    fun `riskLevel MEDIUM for score 0 point 3 to 0 point 59`() {
        val riskScore = WpsRiskScore(
            bssid = "00:11:22:33:44:55",
            riskScore = 0.4,
            issues = emptyList(),
            wpsInfo = null
        )

        assertEquals(RiskLevel.MEDIUM, riskScore.riskLevel)
    }

    @Test
    fun `riskLevel LOW for score below 0 point 3`() {
        val riskScore = WpsRiskScore(
            bssid = "00:11:22:33:44:55",
            riskScore = 0.2,
            issues = emptyList(),
            wpsInfo = null
        )

        assertEquals(RiskLevel.LOW, riskScore.riskLevel)
    }

    @Test
    fun `isCriticalRisk true for CRITICAL level`() {
        val riskScore = WpsRiskScore(
            bssid = "00:11:22:33:44:55",
            riskScore = 0.95,
            issues = emptyList(),
            wpsInfo = null
        )

        assertTrue(riskScore.isCriticalRisk)
    }

    @Test
    fun `hasSignificantRisk true for score above 0 point 5`() {
        val riskScore = WpsRiskScore(
            bssid = "00:11:22:33:44:55",
            riskScore = 0.6,
            issues = emptyList(),
            wpsInfo = null
        )

        assertTrue(riskScore.hasSignificantRisk)
    }

    @Test
    fun `hasSignificantRisk false for score below 0 point 5`() {
        val riskScore = WpsRiskScore(
            bssid = "00:11:22:33:44:55",
            riskScore = 0.3,
            issues = emptyList(),
            wpsInfo = null
        )

        assertFalse(riskScore.hasSignificantRisk)
    }

    // ============================================
    // WpsIssue TESTS
    // ============================================

    @Test
    fun `WpsIssue objects have descriptions`() {
        assertTrue(WpsIssue.PinSupported.description.isNotBlank())
        assertTrue(WpsIssue.UnlockedState.description.isNotBlank())
        assertTrue(WpsIssue.ConfiguredButStillActive.description.isNotBlank())
        assertTrue(WpsIssue.UnknownLockState.description.isNotBlank())
    }

    @Test
    fun `WpsIssue descriptions mention key concepts`() {
        assertTrue(WpsIssue.PinSupported.description.contains("PIN", ignoreCase = true))
        assertTrue(WpsIssue.UnlockedState.description.contains("unlocked", ignoreCase = true))
        assertTrue(WpsIssue.ConfiguredButStillActive.description.contains("configured", ignoreCase = true))
        assertTrue(WpsIssue.UnknownLockState.description.contains("unknown", ignoreCase = true))
    }

    // ============================================
    // RISK SUMMARY
    // ============================================

    @Test
    fun `riskSummary includes level and status`() {
        val wpsInfo = WpsInfo(
            configMethods = setOf(WpsConfigMethod.PUSH_BUTTON),
            wpsState = WpsState.CONFIGURED,
            locked = false,
            deviceName = null,
            manufacturer = null,
            modelName = null,
            version = null
        )

        val riskScore = WpsRiskScore.fromWpsInfo("00:11:22:33:44:55", wpsInfo)
        val summary = riskScore.riskSummary

        assertTrue(summary.contains("WPS"))
        assertTrue(summary.isNotBlank())
    }
}
