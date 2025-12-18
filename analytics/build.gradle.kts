// Analytics module - network analytics and trend analysis
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
