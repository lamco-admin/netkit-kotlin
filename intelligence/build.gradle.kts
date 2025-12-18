// Intelligence module - ML-based intelligent recommendations
// Base KMP configuration inherited from root build.gradle.kts

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":core"))
                api(project(":security"))
                api(project(":wifi-optimizer"))
            }
        }
    }
}
