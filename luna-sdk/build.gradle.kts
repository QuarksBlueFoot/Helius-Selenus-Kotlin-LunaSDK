plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
    signing
}

version = "5.6.0"
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

// ============================================================================
// LunaSDK - All-in-One Meta Package
// Aggregates all Luna modules for convenience
// ============================================================================
dependencies {
    api(project(":luna-core"))
    api(project(":luna-rpc"))
    api(project(":luna-das"))
    api(project(":luna-webhooks"))
    api(project(":luna-priority"))
    api(project(":luna-enhanced-tx"))
    api(project(":luna-analytics"))
    api(project(":luna-innovations"))
    api(project(":luna-privacy"))
    api(project(":luna-jupiter"))
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = "xyz.selenus.luna"
            artifactId = "luna-sdk"
            version = project.version.toString()

            pom {
                name.set("LunaSDK")
                description.set("Complete Helius Solana SDK for Kotlin - All modules in one package")
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



