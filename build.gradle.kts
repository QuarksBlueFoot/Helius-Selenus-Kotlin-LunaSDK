// Root build.gradle.kts
// ============================================================================
// LunaSDK 5.6.0 - Modern Modular Architecture
// Updated: January 2026
// ============================================================================
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.application) apply false
}

// Apply Maven Central Portal staging repository to all subprojects
subprojects {
    afterEvaluate {
        if (plugins.hasPlugin("maven-publish")) {
            extensions.configure<PublishingExtension> {
                repositories {
                    maven {
                        name = "Staging"
                        url = uri(layout.buildDirectory.dir("staging-deploy"))
                    }
                }
            }
        }
    }
}
