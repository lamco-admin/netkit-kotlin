package io.lamco.netkit.optimizer.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive tests for QosGoals
 */
class QosGoalsTest {

    @Test
    fun `create goals with valid parameters succeeds`() {
        val goals = QosGoals(
            enableWmm = true,
            voicePriority = 3,
            videoPriority = 2,
            dataPriority = 1,
            backgroundPriority = 0
        )

        assertTrue(goals.enableWmm)
        assertEquals(3, goals.voicePriority)
    }

    @Test
    fun `create goals with invalid voice priority throws exception`() {
        assertThrows<IllegalArgumentException> {
            QosGoals(voicePriority = 4)
        }
        assertThrows<IllegalArgumentException> {
            QosGoals(voicePriority = -1)
        }
    }

    @Test
    fun `create goals with invalid video priority throws exception`() {
        assertThrows<IllegalArgumentException> {
            QosGoals(videoPriority = 4)
        }
    }

    @Test
    fun `create goals with invalid data priority throws exception`() {
        assertThrows<IllegalArgumentException> {
            QosGoals(dataPriority = 4)
        }
    }

    @Test
    fun `create goals with invalid background priority throws exception`() {
        assertThrows<IllegalArgumentException> {
            QosGoals(backgroundPriority = 4)
        }
    }

    @Test
    fun `create goals with invalid fairness threshold throws exception`() {
        assertThrows<IllegalArgumentException> {
            QosGoals(airtimeFairnessThreshold = -0.1)
        }
        assertThrows<IllegalArgumentException> {
            QosGoals(airtimeFairnessThreshold = 1.1)
        }
    }

    @Test
    fun `create goals with invalid voice latency throws exception`() {
        assertThrows<IllegalArgumentException> {
            QosGoals(maxVoiceLatencyMs = 0)
        }
        assertThrows<IllegalArgumentException> {
            QosGoals(maxVoiceLatencyMs = 101)
        }
    }

    @Test
    fun `create goals with invalid video throughput throws exception`() {
        assertThrows<IllegalArgumentException> {
            QosGoals(minVideoThroughputMbps = 0)
        }
        assertThrows<IllegalArgumentException> {
            QosGoals(minVideoThroughputMbps = -1)
        }
    }

    @Test
    fun `create goals with voice priority less than video throws exception`() {
        assertThrows<IllegalArgumentException> {
            QosGoals(
                enableWmm = true,
                voicePriority = 1,
                videoPriority = 2
            )
        }
    }

    @Test
    fun `create goals with video priority less than data throws exception`() {
        assertThrows<IllegalArgumentException> {
            QosGoals(
                enableWmm = true,
                videoPriority = 0,
                dataPriority = 1
            )
        }
    }

    @Test
    fun `create goals with data priority less than background throws exception`() {
        assertThrows<IllegalArgumentException> {
            QosGoals(
                enableWmm = true,
                dataPriority = 0,
                backgroundPriority = 1
            )
        }
    }

    @Test
    fun `hasStrictHierarchy is true for 3-2-1-0 priorities`() {
        val goals = QosGoals(
            voicePriority = 3,
            videoPriority = 2,
            dataPriority = 1,
            backgroundPriority = 0
        )

        assertTrue(goals.hasStrictHierarchy)
    }

    @Test
    fun `hasStrictHierarchy is false for equal priorities`() {
        val goals = QosGoals(
            enableWmm = false,
            voicePriority = 1,
            videoPriority = 1,
            dataPriority = 1,
            backgroundPriority = 1
        )

        assertFalse(goals.hasStrictHierarchy)
    }

    @Test
    fun `voiceIsHighestPriority is true when voice has max priority`() {
        val goals = QosGoals(voicePriority = 3)
        assertTrue(goals.voiceIsHighestPriority)
    }

    @Test
    fun `fairnessStrictness VERY_STRICT for low threshold`() {
        val goals = QosGoals(airtimeFairnessThreshold = 0.15)
        assertEquals(FairnessStrictness.VERY_STRICT, goals.fairnessStrictness)
    }

    @Test
    fun `fairnessStrictness STRICT for medium-low threshold`() {
        val goals = QosGoals(airtimeFairnessThreshold = 0.25)
        assertEquals(FairnessStrictness.STRICT, goals.fairnessStrictness)
    }

    @Test
    fun `fairnessStrictness MODERATE for medium threshold`() {
        val goals = QosGoals(airtimeFairnessThreshold = 0.4)
        assertEquals(FairnessStrictness.MODERATE, goals.fairnessStrictness)
    }

    @Test
    fun `fairnessStrictness PERMISSIVE for high threshold`() {
        val goals = QosGoals(airtimeFairnessThreshold = 0.7)
        assertEquals(FairnessStrictness.PERMISSIVE, goals.fairnessStrictness)
    }

    @Test
    fun `voiceQualityTier PREMIUM for very low latency`() {
        val goals = QosGoals(maxVoiceLatencyMs = 10)
        assertEquals(VoiceQualityTier.PREMIUM, goals.voiceQualityTier)
    }

    @Test
    fun `voiceQualityTier STANDARD for low latency`() {
        val goals = QosGoals(maxVoiceLatencyMs = 20)
        assertEquals(VoiceQualityTier.STANDARD, goals.voiceQualityTier)
    }

    @Test
    fun `voiceQualityTier ACCEPTABLE for medium latency`() {
        val goals = QosGoals(maxVoiceLatencyMs = 40)
        assertEquals(VoiceQualityTier.ACCEPTABLE, goals.voiceQualityTier)
    }

    @Test
    fun `voiceQualityTier BASIC for high latency`() {
        val goals = QosGoals(maxVoiceLatencyMs = 50)
        assertEquals(VoiceQualityTier.BASIC, goals.voiceQualityTier)
    }

    @Test
    fun `summary contains key information`() {
        val goals = QosGoals()
        val summary = goals.summary

        assertTrue(summary.contains("WMM"))
        assertTrue(summary.contains("V3"))
        assertTrue(summary.contains("Vi2"))
        assertTrue(summary.contains("D1"))
        assertTrue(summary.contains("B0"))
    }

    @Test
    fun `standard factory creates appropriate goals`() {
        val goals = QosGoals.standard()

        assertTrue(goals.enableWmm)
        assertEquals(3, goals.voicePriority)
        assertEquals(2, goals.videoPriority)
        assertEquals(0.4, goals.airtimeFairnessThreshold)
        assertEquals(20, goals.maxVoiceLatencyMs)
    }

    @Test
    fun `enterprise factory creates strict goals`() {
        val goals = QosGoals.enterprise()

        assertTrue(goals.enableWmm)
        assertEquals(0.3, goals.airtimeFairnessThreshold)
        assertEquals(15, goals.maxVoiceLatencyMs)
        assertFalse(goals.allowAirtimeHogs)
    }

    @Test
    fun `voipCentric factory prioritizes voice quality`() {
        val goals = QosGoals.voipCentric()

        assertEquals(10, goals.maxVoiceLatencyMs)
        assertEquals(0.25, goals.airtimeFairnessThreshold)
        assertEquals(VoiceQualityTier.PREMIUM, goals.voiceQualityTier)
    }

    @Test
    fun `videoCentric factory has high video priority`() {
        val goals = QosGoals.videoCentric()

        assertEquals(3, goals.videoPriority)
        assertEquals(25, goals.minVideoThroughputMbps)
    }

    @Test
    fun `bestEffort factory allows flexible priorities`() {
        val goals = QosGoals.bestEffort()

        assertFalse(goals.enableWmm)
        assertTrue(goals.allowAirtimeHogs)
        assertEquals(0.6, goals.airtimeFairnessThreshold)
    }

    @Test
    fun `WmmAccessCategory has correct priorities`() {
        assertEquals(3, WmmAccessCategory.VOICE.priority)
        assertEquals(2, WmmAccessCategory.VIDEO.priority)
        assertEquals(1, WmmAccessCategory.BEST_EFFORT.priority)
        assertEquals(0, WmmAccessCategory.BACKGROUND.priority)
    }
}
