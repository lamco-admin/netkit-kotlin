// WiFi Optimizer module - channel and configuration optimization
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
