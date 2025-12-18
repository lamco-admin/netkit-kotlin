// Export module - report generation and data export
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
