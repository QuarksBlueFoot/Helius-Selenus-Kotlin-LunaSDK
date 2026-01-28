pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Selenus-Solana-SDKs"

// LunaSDK - Helius Solana SDK (Modular)
include(":luna-core")
include(":luna-rpc")
include(":luna-das")
include(":luna-webhooks")
include(":luna-priority")
include(":luna-enhanced-tx")
include(":luna-analytics")
include(":luna-innovations")
include(":luna-privacy")
include(":luna-jupiter")
include(":luna-nlp")
include(":luna-sdk")

// IrisSDK - QuickNode Solana SDK  
include(":iris-sdk")

// Sample Android Application
include(":sample-app")
