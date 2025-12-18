package io.lamco.netkit.model.security

/**
 * Actionable security recommendation for improving network security
 *
 * Recommendations are prioritized and provide specific guidance on how to
 * improve the security posture of a WiFi network.
 *
 * @property priority Severity level (CRITICAL, HIGH, MEDIUM, LOW, INFO)
 * @property title Short summary of the recommendation
 * @property description Detailed explanation of the issue and impact
 * @property actionable Whether this recommendation can be acted upon by the user
 * @property action Specific action to take (if actionable)
 */
data class SecurityRecommendation(
    val priority: Priority,
    val title: String,
    val description: String,
    val actionable: Boolean,
    val action: RecommendedAction?,
) {
    /**
     * Priority level for security recommendations
     *
     * Determines urgency and visual presentation
     */
    enum class Priority {
        /**
         * CRITICAL - Immediate action required
         * Network is actively vulnerable to attacks
         * Examples: WEP encryption, open network with sensitive data
         */
        CRITICAL,

        /**
         * HIGH - Important upgrade needed
         * Significant security risk present
         * Examples: WPA2 without PMF, transition mode, WPS enabled
         */
        HIGH,

        /**
         * MEDIUM - Recommended improvement
         * Security could be enhanced
         * Examples: WPA2 to WPA3 upgrade, enable PMF
         */
        MEDIUM,

        /**
         * LOW - Optional enhancement
         * Minor security improvements
         * Examples: Band optimization, channel selection
         */
        LOW,

        /**
         * INFO - Informational only
         * No action required, educational content
         * Examples: Explanation of current security features
         */
        INFO,
    }
}

/**
 * Specific action recommended to improve security
 *
 * Sealed class hierarchy provides type-safe recommendations with
 * specific instructions for each action type.
 */
sealed class RecommendedAction {
    /**
     * Change router/AP settings
     *
     * @property instruction Step-by-step guide to change router settings
     */
    data class RouterSettings(
        val instruction: String,
    ) : RecommendedAction()

    /**
     * Change network configuration
     *
     * @property instruction How to reconfigure the network
     */
    data class NetworkSettings(
        val instruction: String,
    ) : RecommendedAction()

    /**
     * Change device/client settings
     *
     * @property instruction How to configure the connecting device
     */
    data class DeviceSettings(
        val instruction: String,
    ) : RecommendedAction()

    /**
     * Router should be replaced entirely
     *
     * @property reason Why replacement is necessary (e.g., too old, can't support WPA3)
     */
    data class ReplaceRouter(
        val reason: String,
    ) : RecommendedAction()

    /**
     * Avoid connecting to this network
     *
     * @property reason Why the network is unsafe
     */
    data class AvoidNetwork(
        val reason: String,
    ) : RecommendedAction()

    /**
     * Enable a specific feature
     *
     * @property feature Name of the feature to enable
     */
    data class EnableFeature(
        val feature: String,
    ) : RecommendedAction()

    /**
     * Disable a specific feature
     *
     * @property feature Name of the feature to disable
     * @property reason Why disabling improves security
     */
    data class DisableFeature(
        val feature: String,
        val reason: String,
    ) : RecommendedAction()

    /**
     * Upgrade firmware or router
     *
     * @property currentVersion Current firmware version (if known)
     * @property recommendation What to upgrade to
     */
    data class UpgradeFirmware(
        val currentVersion: String?,
        val recommendation: String,
    ) : RecommendedAction()

    /**
     * Contact network administrator
     *
     * @property issue Description of the issue to report
     */
    data class ContactAdmin(
        val issue: String,
    ) : RecommendedAction()
}
