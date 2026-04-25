plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
    signing
}

version = "5.7.0"
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
    // luna-keys is intentionally dependency-free at the Helius level — it
    // only needs JDK crypto + a small base58 codec. Keep this module skinny
    // so callers can pull it independently of the rest of LunaSDK.
    testImplementation(libs.kotlin.test.junit5)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = "xyz.selenus.luna"
            artifactId = "luna-keys"
            version = project.version.toString()

            pom {
                name.set("LunaSDK Keys")
                description.set(
                    "Solana keypair generation, address validation, and base58 codec utilities. " +
                        "Pure-JVM, no Bouncy Castle dependency — uses JDK 17 native Ed25519."
                )
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
