plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
    signing
}

kotlin {
    jvmToolchain(17)
}

group = "xyz.selenus"
version = "1.4.0"

// ============================================================================
// Iris SDK 2026 Modern Dependencies
// Last Updated: January 2026
// ============================================================================
dependencies {
    // HTTP Client - OkHttp 5.3.2 (November 2025)
    // https://square.github.io/okhttp/changelogs/changelog/
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    
    // Kotlin Serialization 1.10.0 (January 2026)
    // https://github.com/Kotlin/kotlinx.serialization/releases
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    
    // Kotlin Coroutines 1.10.2 (April 2025 - Latest stable)
    // https://github.com/Kotlin/kotlinx.coroutines/releases
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    
    // gRPC for Yellowstone Geyser - Updated to 2026 versions
    // https://github.com/grpc/grpc-kotlin/releases - v1.5.0 (Sep 2025)
    // https://github.com/grpc/grpc-java/releases - v1.78.0 (Dec 2025)
    implementation("io.grpc:grpc-kotlin-stub:1.5.0")
    implementation("io.grpc:grpc-netty-shaded:1.78.0")
    implementation("io.grpc:grpc-protobuf:1.78.0")
    implementation("com.google.protobuf:protobuf-kotlin:4.32.0")
    
    // Kotlin Standard Library
    implementation(kotlin("stdlib"))

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

tasks.test {
    useJUnitPlatform()
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(rootProject.file("docs/IrisSDK_Guide.md"))
    into(".")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "iris-sdk"
            from(components["java"])
            artifact(sourcesJar)
            artifact(javadocJar)

            pom {
                name.set("IrisSDK")
                description.set("The definitive Kotlin-first QuickNode Solana SDK - featuring all marketplace add-ons, Yellowstone gRPC streaming, JITO bundles, Metis Jupiter, and exclusive privacy innovations.")
                url.set("https://github.com/QuarksBlueFoot/Helius-Selenus-Kotlin-LunaSDK")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("BoobiesInteractive")
                        name.set("Bluefoot Labs Boobies Interactive LLC")
                        email.set("quark@bluefoot.tech")
                        organization.set("Bluefoot Labs")
                        organizationUrl.set("https://www.bluefootlabs.com")
                    }
                }
                scm {
                    url.set("https://github.com/QuarksBlueFoot/Helius-Selenus-Kotlin-LunaSDK")
                    connection.set("scm:git:git://github.com/QuarksBlueFoot/Helius-Selenus-Kotlin-LunaSDK.git")
                    developerConnection.set("scm:git:ssh://github.com/QuarksBlueFoot/Helius-Selenus-Kotlin-LunaSDK.git")
                }
            }
        }
    }
    repositories {
        maven {
            name = "Staging"
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

signing {
    val useGpgAgent = System.getenv("USE_GPG_AGENT")?.toBoolean() ?: false
    val signingKeyId = System.getenv("SIGNING_KEY_ID") ?: ""
    val signingPassword = System.getenv("SIGNING_PASSWORD") ?: ""
    
    if (useGpgAgent && signingKeyId.isNotEmpty()) {
        useGpgCmd()
        sign(publishing.publications["maven"])
    } else {
        val signingKey = System.getenv("SIGNING_KEY")
        if (!signingKey.isNullOrEmpty()) {
            if (signingKeyId.isNotEmpty()) {
                useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
            } else {
                useInMemoryPgpKeys(signingKey, signingPassword)
            }
            sign(publishing.publications["maven"])
        }
    }
}

