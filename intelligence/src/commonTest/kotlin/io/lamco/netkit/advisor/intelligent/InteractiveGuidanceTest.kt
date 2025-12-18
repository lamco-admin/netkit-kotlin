package io.lamco.netkit.advisor.intelligent

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class InteractiveGuidanceTest {

    private val guidance = InteractiveGuidance()

    // ========================================
    // Wizard Start Tests
    // ========================================

    @Test
    fun `start network setup wizard`() {
        val session = guidance.startWizard(WizardType.NETWORK_SETUP)

        assertEquals(WizardType.NETWORK_SETUP, session.wizardType)
        assertEquals(0, session.currentStep)
        assertTrue(session.steps.isNotEmpty())
    }

    @Test
    fun `start troubleshooting wizard`() {
        val session = guidance.startWizard(WizardType.TROUBLESHOOTING)

        assertEquals(WizardType.TROUBLESHOOTING, session.wizardType)
        assertTrue(session.steps.isNotEmpty())
    }

    @Test
    fun `start security setup wizard`() {
        val session = guidance.startWizard(WizardType.SECURITY_SETUP)

        assertEquals(WizardType.SECURITY_SETUP, session.wizardType)
        assertTrue(session.steps.isNotEmpty())
    }

    @Test
    fun `start performance tuning wizard`() {
        val session = guidance.startWizard(WizardType.PERFORMANCE_TUNING)

        assertEquals(WizardType.PERFORMANCE_TUNING, session.wizardType)
        assertTrue(session.steps.isNotEmpty())
    }

    @Test
    fun `start guest network wizard`() {
        val session = guidance.startWizard(WizardType.GUEST_NETWORK)

        assertEquals(WizardType.GUEST_NETWORK, session.wizardType)
        assertTrue(session.steps.isNotEmpty())
    }

    @Test
    fun `start mesh setup wizard`() {
        val session = guidance.startWizard(WizardType.MESH_SETUP)

        assertEquals(WizardType.MESH_SETUP, session.wizardType)
        assertTrue(session.steps.isNotEmpty())
    }

    // ========================================
    // Wizard Navigation Tests
    // ========================================

    @Test
    fun `wizard session starts at step 0`() {
        val session = guidance.startWizard(WizardType.NETWORK_SETUP)

        assertEquals(0, session.currentStep)
        assertNotNull(session.current)
    }

    @Test
    fun `wizard can advance to next step`() {
        val session = guidance.startWizard(WizardType.NETWORK_SETUP)
        val next = session.next("Home")

        assertEquals(1, next.currentStep)
        assertTrue(next.responses.containsKey(0))
        assertEquals("Home", next.responses[0])
    }

    @Test
    fun `wizard can go back to previous step`() {
        var session = guidance.startWizard(WizardType.NETWORK_SETUP)
        session = session.next("Home")
        session = session.next("100")

        val previous = session.previous()

        assertEquals(1, previous.currentStep)
    }

    @Test
    fun `wizard cannot go before step 0`() {
        val session = guidance.startWizard(WizardType.NETWORK_SETUP)
        val previous = session.previous()

        assertEquals(0, previous.currentStep)
    }

    @Test
    fun `wizard tracks progress percentage`() {
        val session = guidance.startWizard(WizardType.NETWORK_SETUP)

        assertTrue(session.progress in 0..100)
    }

    @Test
    fun `wizard progress increases with steps`() {
        var session = guidance.startWizard(WizardType.NETWORK_SETUP)
        val initialProgress = session.progress

        session = session.next("Home")
        val nextProgress = session.progress

        assertTrue(nextProgress > initialProgress)
    }

    @Test
    fun `wizard completes when all steps done`() {
        var session = guidance.startWizard(WizardType.GUEST_NETWORK)

        while (!session.isComplete) {
            session = session.next("Default response")
        }

        assertTrue(session.isComplete)
        assertEquals(100, session.progress)
    }

    // ========================================
    // Wizard Content Tests
    // ========================================

    @Test
    fun `network setup asks about network type`() {
        val session = guidance.startWizard(WizardType.NETWORK_SETUP)
        val firstStep = session.current!!

        assertTrue(
            firstStep.title.contains("type", ignoreCase = true) ||
            firstStep.description.contains("type", ignoreCase = true)
        )
    }

    @Test
    fun `network setup asks about coverage area`() {
        val session = guidance.startWizard(WizardType.NETWORK_SETUP)

        val coverageStep = session.steps.find {
            it.title.contains("coverage", ignoreCase = true) ||
            it.description.contains("coverage", ignoreCase = true)
        }

        assertNotNull(coverageStep)
    }

    @Test
    fun `troubleshooting asks about primary issue`() {
        val session = guidance.startWizard(WizardType.TROUBLESHOOTING)
        val firstStep = session.current!!

        assertTrue(
            firstStep.title.contains("issue", ignoreCase = true) ||
            firstStep.prompt.contains("problem", ignoreCase = true)
        )
    }

    @Test
    fun `security setup asks about current security`() {
        val session = guidance.startWizard(WizardType.SECURITY_SETUP)
        val firstStep = session.current!!

        assertTrue(
            firstStep.title.contains("security", ignoreCase = true) ||
            firstStep.description.contains("security", ignoreCase = true)
        )
    }

    @Test
    fun `guest network asks about access type`() {
        val session = guidance.startWizard(WizardType.GUEST_NETWORK)
        val firstStep = session.current!!

        assertTrue(
            firstStep.title.contains("access", ignoreCase = true) ||
            firstStep.description.contains("access", ignoreCase = true)
        )
    }

    @Test
    fun `mesh setup asks about node count`() {
        val session = guidance.startWizard(WizardType.MESH_SETUP)
        val firstStep = session.current!!

        assertTrue(
            firstStep.title.contains("node", ignoreCase = true) ||
            firstStep.description.contains("node", ignoreCase = true)
        )
    }

    // ========================================
    // Step Content Tests
    // ========================================

    @Test
    fun `wizard steps have titles`() {
        val session = guidance.startWizard(WizardType.NETWORK_SETUP)

        session.steps.forEach { step ->
            assertTrue(step.title.isNotEmpty(), "Step ${step.step} missing title")
        }
    }

    @Test
    fun `wizard steps have descriptions`() {
        val session = guidance.startWizard(WizardType.NETWORK_SETUP)

        session.steps.forEach { step ->
            assertTrue(step.description.isNotEmpty(), "Step ${step.step} missing description")
        }
    }

    @Test
    fun `wizard steps have prompts`() {
        val session = guidance.startWizard(WizardType.NETWORK_SETUP)

        session.steps.forEach { step ->
            assertTrue(step.prompt.isNotEmpty(), "Step ${step.step} missing prompt")
        }
    }

    @Test
    fun `choice steps have options`() {
        val session = guidance.startWizard(WizardType.NETWORK_SETUP)

        val choiceSteps = session.steps.filter { it.isChoice }
        choiceSteps.forEach { step ->
            assertTrue(step.options.isNotEmpty(), "Choice step ${step.step} missing options")
        }
    }

    @Test
    fun `steps with default values are helpful`() {
        val session = guidance.startWizard(WizardType.NETWORK_SETUP)

        val stepsWithDefaults = session.steps.filter { it.defaultValue != null }
        assertTrue(stepsWithDefaults.isNotEmpty(), "No steps with default values")
    }

    // ========================================
    // Help System Tests
    // ========================================

    @Test
    fun `get help for current step`() {
        val session = guidance.startWizard(WizardType.NETWORK_SETUP)
        val help = guidance.getHelpForStep(session)

        assertTrue(help.isNotEmpty())
        assertTrue(help.contains(session.current!!.title))
    }

    @Test
    fun `help includes description`() {
        val session = guidance.startWizard(WizardType.NETWORK_SETUP)
        val help = guidance.getHelpForStep(session)

        assertTrue(help.contains(session.current!!.description))
    }

    @Test
    fun `help includes options for choice steps`() {
        val session = guidance.startWizard(WizardType.NETWORK_SETUP)
        val help = guidance.getHelpForStep(session)

        if (session.current!!.isChoice) {
            session.current!!.options.forEach { option ->
                assertTrue(help.contains(option), "Help missing option: $option")
            }
        }
    }

    @Test
    fun `help includes default value if present`() {
        val session = guidance.startWizard(WizardType.NETWORK_SETUP)
        val help = guidance.getHelpForStep(session)

        if (session.current!!.defaultValue != null) {
            assertTrue(help.contains(session.current!!.defaultValue!!))
        }
    }

    @Test
    fun `help includes validation if present`() {
        var session = guidance.startWizard(WizardType.NETWORK_SETUP)
        // Advance to a step with validation
        session = session.next("Home")
        val help = guidance.getHelpForStep(session)

        if (session.current?.validation != null) {
            assertTrue(help.contains(session.current!!.validation!!))
        }
    }

    @Test
    fun `help for completed wizard says wizard complete`() {
        var session = guidance.startWizard(WizardType.GUEST_NETWORK)

        while (!session.isComplete) {
            session = session.next("Response")
        }

        val help = guidance.getHelpForStep(session)
        assertTrue(help.contains("complete", ignoreCase = true))
    }

    // ========================================
    // Wizard Completion Tests
    // ========================================

    @Test
    fun `complete wizard generates result`() {
        var session = guidance.startWizard(WizardType.GUEST_NETWORK)

        while (!session.isComplete) {
            session = session.next("Response")
        }

        val result = guidance.completeWizard(session)

        assertEquals(WizardType.GUEST_NETWORK, result.wizardType)
        assertTrue(result.recommendations.isNotEmpty())
    }

    @Test
    fun `cannot complete incomplete wizard`() {
        val session = guidance.startWizard(WizardType.NETWORK_SETUP)

        assertThrows<IllegalArgumentException> {
            guidance.completeWizard(session)
        }
    }

    @Test
    fun `wizard result includes all responses`() {
        var session = guidance.startWizard(WizardType.GUEST_NETWORK)

        while (!session.isComplete) {
            session = session.next("Response ${session.currentStep}")
        }

        val result = guidance.completeWizard(session)

        assertEquals(session.responses.size, result.responses.size)
    }

    @Test
    fun `wizard result includes recommendations`() {
        var session = guidance.startWizard(WizardType.NETWORK_SETUP)

        while (!session.isComplete) {
            session = session.next("Default")
        }

        val result = guidance.completeWizard(session)

        assertTrue(result.recommendations.isNotEmpty())
    }

    @Test
    fun `wizard result includes next steps`() {
        var session = guidance.startWizard(WizardType.SECURITY_SETUP)

        while (!session.isComplete) {
            session = session.next("Default")
        }

        val result = guidance.completeWizard(session)

        assertTrue(result.nextSteps.isNotEmpty())
    }

    @Test
    fun `wizard result summary is informative`() {
        var session = guidance.startWizard(WizardType.NETWORK_SETUP)

        while (!session.isComplete) {
            session = session.next("Response")
        }

        val result = guidance.completeWizard(session)

        assertTrue(result.summary.contains(result.wizardType.displayName))
        assertTrue(result.summary.contains("recommendation"))
    }

    // ========================================
    // Specific Wizard Tests
    // ========================================

    @Test
    fun `network setup wizard has at least 5 steps`() {
        val session = guidance.startWizard(WizardType.NETWORK_SETUP)

        assertTrue(session.steps.size >= 5)
    }

    @Test
    fun `troubleshooting wizard collects issue details`() {
        val session = guidance.startWizard(WizardType.TROUBLESHOOTING)

        assertTrue(session.steps.size >= 3)
        assertTrue(session.steps.any {
            it.title.contains("issue", ignoreCase = true) ||
            it.title.contains("problem", ignoreCase = true)
        })
    }

    @Test
    fun `security wizard covers compliance`() {
        val session = guidance.startWizard(WizardType.SECURITY_SETUP)

        assertTrue(session.steps.any {
            it.title.contains("compliance", ignoreCase = true) ||
            it.description.contains("compliance", ignoreCase = true)
        })
    }

    @Test
    fun `performance wizard asks about current speeds`() {
        val session = guidance.startWizard(WizardType.PERFORMANCE_TUNING)

        assertTrue(session.steps.any {
            it.title.contains("performance", ignoreCase = true) ||
            it.title.contains("speed", ignoreCase = true)
        })
    }

    @Test
    fun `guest network wizard configures isolation`() {
        val session = guidance.startWizard(WizardType.GUEST_NETWORK)

        assertTrue(session.steps.any {
            it.title.contains("isolation", ignoreCase = true) ||
            it.description.contains("isolation", ignoreCase = true)
        })
    }

    @Test
    fun `mesh wizard asks about backhaul`() {
        val session = guidance.startWizard(WizardType.MESH_SETUP)

        assertTrue(session.steps.any {
            it.title.contains("backhaul", ignoreCase = true) ||
            it.description.contains("backhaul", ignoreCase = true)
        })
    }

    // ========================================
    // Wizard Context Tests
    // ========================================

    @Test
    fun `wizard can start with initial context`() {
        val initialContext = mapOf<String, Any>(
            "networkType" to "Enterprise",
            "existingDevices" to 50
        )

        val session = guidance.startWizard(
            WizardType.NETWORK_SETUP,
            initialContext = initialContext
        )

        assertEquals("Enterprise", session.context["networkType"])
        assertEquals(50, session.context["existingDevices"])
    }

    @Test
    fun `wizard context is mutable`() {
        val session = guidance.startWizard(WizardType.NETWORK_SETUP)

        session.context["testKey"] = "testValue"

        assertEquals("testValue", session.context["testKey"])
    }
}
