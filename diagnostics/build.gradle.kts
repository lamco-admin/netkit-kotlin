// Diagnostics module - network diagnostics and troubleshooting
// Base KMP configuration inherited from root build.gradle.kts

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":core"))
            }
        }
    }
}
