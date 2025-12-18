pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "netkit"

// Core modules
include(":core")

// Feature modules
include(":diagnostics")
include(":wifi-optimizer")
include(":analytics")
include(":security")
include(":intelligence")
include(":export")
