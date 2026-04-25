plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
    signing
}

version = "5.3.0"
group = "xyz.selenus.luna"

java {
    withJavadocJar()
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

dependencies {
    api(project(":luna-core"))
    // Innovation classes call back into other extracted feature namespaces
    // (e.g. StrategyEngineApi uses client.jupiter.getQuote, NetworkIntelligenceApi uses client.priority).
    api(project(":luna-das"))
    api(project(":luna-rpc"))
    api(project(":luna-priority"))
    api(project(":luna-enhanced-tx"))
    api(project(":luna-jupiter"))
    api(project(":luna-analytics"))
    // WalletCorrelationApi calls client.privacy.analyzeAddressLinkage
    api(project(":luna-privacy"))
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            
            groupId = "xyz.selenus.luna"
            artifactId = "luna-innovations"
            version = project.version.toString()
            
            pom {
                name.set("LunaSDK Innovations")
                description.set("Innovations module for LunaSDK - Funding tracker, time travel, wallet correlation")
                url.set("https://github.com/nicholasxjy/Helius-Selenus-Kotlin-LunaSDK")
                
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
                developers {
                    developer {
                        id.set("selenus")
                        name.set("Selenus Team")
                        email.set("dev@selenus.com")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/nicholasxjy/Helius-Selenus-Kotlin-LunaSDK.git")
                    developerConnection.set("scm:git:ssh://github.com:nicholasxjy/Helius-Selenus-Kotlin-LunaSDK.git")
                    url.set("https://github.com/nicholasxjy/Helius-Selenus-Kotlin-LunaSDK")
                }
            }
        }
    }
    
    repositories {
        maven {
            name = "local"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["mavenJava"])
}

tasks.withType<Sign>().configureEach {
    onlyIf { gradle.taskGraph.hasTask("publish") }
}


