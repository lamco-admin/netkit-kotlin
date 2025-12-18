package io.lamco.netkit.model.topology

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class SecurityFingerprintTest {
    @Test
    fun `creates valid security fingerprint`() {
        val fingerprint =
            SecurityFingerprint(
                authType = AuthType.WPA3_SAE,
                cipherSet = setOf(CipherSuite.CCMP, CipherSuite.GCMP),
                pmfRequired = true,
                transitionMode = null,
            )

        assertEquals(AuthType.WPA3_SAE, fingerprint.authType)
        assertEquals(2, fingerprint.cipherSet.size)
        assertTrue(fingerprint.pmfRequired)
        assertNull(fingerprint.transitionMode)
    }

    @Test
    fun `requires non-empty cipher set`() {
        assertThrows<IllegalArgumentException> {
            SecurityFingerprint(
                authType = AuthType.WPA2_PSK,
                cipherSet = emptySet(),
                pmfRequired = false,
                transitionMode = null,
            )
        }
    }

    @Test
    fun `WPA3 with strong ciphers is secure`() {
        val fingerprint = SecurityFingerprint.wpa3Personal()
        assertTrue(fingerprint.isSecure)
    }

    @Test
    fun `WPA2 with strong ciphers is secure`() {
        val fingerprint = SecurityFingerprint.wpa2Personal(pmfRequired = true)
        assertTrue(fingerprint.isSecure)
    }

    @Test
    fun `Open network is not secure`() {
        val fingerprint = SecurityFingerprint.open()
        assertFalse(fingerprint.isSecure)
    }

    @Test
    fun `WPA3 is modern`() {
        val fingerprint = SecurityFingerprint.wpa3Personal()
        assertTrue(fingerprint.isModern)
    }

    @Test
    fun `WPA2 with PMF is modern`() {
        val fingerprint = SecurityFingerprint.wpa2Personal(pmfRequired = true)
        assertTrue(fingerprint.isModern)
    }

    @Test
    fun `WPA2 without PMF is not modern`() {
        val fingerprint = SecurityFingerprint.wpa2Personal(pmfRequired = false)
        assertFalse(fingerprint.isModern)
    }

    @Test
    fun `Enhanced Open is modern`() {
        val fingerprint = SecurityFingerprint.enhancedOpen()
        assertTrue(fingerprint.isModern)
    }

    @Test
    fun `detects weak ciphers`() {
        val fingerprint =
            SecurityFingerprint(
                authType = AuthType.WPA2_PSK,
                cipherSet = setOf(CipherSuite.CCMP, CipherSuite.TKIP),
                pmfRequired = false,
                transitionMode = null,
            )

        assertTrue(fingerprint.hasWeakCiphers)
    }

    @Test
    fun `no weak ciphers in modern config`() {
        val fingerprint = SecurityFingerprint.wpa3Personal()
        assertFalse(fingerprint.hasWeakCiphers)
    }

    @Test
    fun `identifies strongest cipher`() {
        val fingerprint =
            SecurityFingerprint(
                authType = AuthType.WPA3_ENTERPRISE_192,
                cipherSet = setOf(CipherSuite.CCMP, CipherSuite.GCMP_256),
                pmfRequired = true,
                transitionMode = null,
            )

        assertEquals(CipherSuite.GCMP_256, fingerprint.strongestCipher)
    }

    @Test
    fun `identifies weakest cipher`() {
        val fingerprint =
            SecurityFingerprint(
                authType = AuthType.WPA2_PSK,
                cipherSet = setOf(CipherSuite.CCMP, CipherSuite.TKIP),
                pmfRequired = false,
                transitionMode = null,
            )

        assertEquals(CipherSuite.TKIP, fingerprint.weakestCipher)
    }

    @Test
    fun `security score accounts for auth type and ciphers`() {
        val wpa3 = SecurityFingerprint.wpa3Personal()
        val wpa2 = SecurityFingerprint.wpa2Personal()
        val open = SecurityFingerprint.open()

        assertTrue(wpa3.securityScore > wpa2.securityScore)
        assertTrue(wpa2.securityScore > open.securityScore)
    }

    @Test
    fun `PMF increases security score`() {
        val withPmf = SecurityFingerprint.wpa2Personal(pmfRequired = true)
        val withoutPmf = SecurityFingerprint.wpa2Personal(pmfRequired = false)

        assertTrue(withPmf.securityScore >= withoutPmf.securityScore)
    }

    @Test
    fun `weak ciphers decrease security score`() {
        val strongOnly =
            SecurityFingerprint(
                authType = AuthType.WPA2_PSK,
                cipherSet = setOf(CipherSuite.CCMP),
                pmfRequired = false,
                transitionMode = null,
            )

        val withWeak =
            SecurityFingerprint(
                authType = AuthType.WPA2_PSK,
                cipherSet = setOf(CipherSuite.CCMP, CipherSuite.TKIP),
                pmfRequired = false,
                transitionMode = null,
            )

        assertTrue(strongOnly.securityScore > withWeak.securityScore)
    }

    @Test
    fun `security score is bounded 0-100`() {
        SecurityFingerprint.entries.forEach { fingerprint ->
            assertTrue(fingerprint.securityScore in 0..100)
        }
    }

    @Test
    fun `security summary includes auth type`() {
        val fingerprint = SecurityFingerprint.wpa3Personal()
        assertTrue(fingerprint.securitySummary.contains("WPA3"))
    }

    @Test
    fun `security summary includes cipher`() {
        val fingerprint = SecurityFingerprint.wpa3Personal()
        assertTrue(fingerprint.securitySummary.contains("AES"))
    }

    @Test
    fun `security summary includes PMF when required`() {
        val fingerprint = SecurityFingerprint.wpa3Personal()
        assertTrue(fingerprint.securitySummary.contains("PMF"))
    }

    @Test
    fun `security summary includes transition mode`() {
        val fingerprint = SecurityFingerprint.wpa2Wpa3Transition()
        assertTrue(fingerprint.securitySummary.contains("Transition"))
    }

    @Test
    fun `identical fingerprints match`() {
        val fp1 = SecurityFingerprint.wpa3Personal()
        val fp2 = SecurityFingerprint.wpa3Personal()

        assertTrue(fp1.matches(fp2))
    }

    @Test
    fun `different auth types do not match`() {
        val wpa3 = SecurityFingerprint.wpa3Personal()
        val wpa2 = SecurityFingerprint.wpa2Personal()

        assertFalse(wpa3.matches(wpa2))
    }

    @Test
    fun `different cipher sets do not match`() {
        val fp1 =
            SecurityFingerprint(
                authType = AuthType.WPA2_PSK,
                cipherSet = setOf(CipherSuite.CCMP),
                pmfRequired = false,
                transitionMode = null,
            )

        val fp2 =
            SecurityFingerprint(
                authType = AuthType.WPA2_PSK,
                cipherSet = setOf(CipherSuite.CCMP, CipherSuite.GCMP),
                pmfRequired = false,
                transitionMode = null,
            )

        assertFalse(fp1.matches(fp2))
    }

    @Test
    fun `compatible fingerprints share auth type and cipher`() {
        val fp1 =
            SecurityFingerprint(
                authType = AuthType.WPA2_PSK,
                cipherSet = setOf(CipherSuite.CCMP, CipherSuite.GCMP),
                pmfRequired = false,
                transitionMode = null,
            )

        val fp2 =
            SecurityFingerprint(
                authType = AuthType.WPA2_PSK,
                cipherSet = setOf(CipherSuite.CCMP),
                pmfRequired = true,
                transitionMode = null,
            )

        assertTrue(fp1.isCompatibleWith(fp2))
    }

    @Test
    fun `incompatible auth types are not compatible`() {
        val wpa3 = SecurityFingerprint.wpa3Personal()
        val wpa2 = SecurityFingerprint.wpa2Personal()

        assertFalse(wpa3.isCompatibleWith(wpa2))
    }

    @Test
    fun `open fingerprint has correct properties`() {
        val fingerprint = SecurityFingerprint.open()

        assertEquals(AuthType.OPEN, fingerprint.authType)
        assertTrue(fingerprint.cipherSet.contains(CipherSuite.NONE))
        assertFalse(fingerprint.pmfRequired)
        assertNull(fingerprint.transitionMode)
    }

    @Test
    fun `enhanced open has PMF required`() {
        val fingerprint = SecurityFingerprint.enhancedOpen()

        assertEquals(AuthType.OWE, fingerprint.authType)
        assertTrue(fingerprint.pmfRequired)
    }

    @Test
    fun `WPA2 WPA3 transition has transition mode set`() {
        val fingerprint = SecurityFingerprint.wpa2Wpa3Transition()

        assertEquals(TransitionMode.WPA2_WPA3, fingerprint.transitionMode)
    }

    companion object {
        val SecurityFingerprint.Companion.entries: List<SecurityFingerprint>
            get() =
                listOf(
                    open(),
                    enhancedOpen(),
                    wpa2Personal(false),
                    wpa2Personal(true),
                    wpa3Personal(),
                    wpa2Wpa3Transition(),
                )
    }
}
