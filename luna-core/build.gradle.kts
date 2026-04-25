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

dependencies {
    // Exposed in public API surface (suspend fun / @Serializable / JsonElement on signatures,
    // and the public `httpClient: OkHttpClient` / `json: Json` properties on LunaHeliusClient).
    // Downstream feature modules get these transitively.
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)

    // Internal implementation details — not exposed to downstream modules.
    implementation(libs.okhttp.sse)

    // gRPC for Yellowstone streaming (internal)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.netty.shaded)
    implementation(libs.protobuf.kotlin)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            
            groupId = "xyz.selenus.luna"
            artifactId = "luna-core"
            version = project.version.toString()
            
            pom {
                name.set("LunaSDK Core")
                description.set("Core module for LunaSDK - Helius Solana SDK for Kotlin")
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


