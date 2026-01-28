plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
    signing
}

description = "Natural Language Transaction Builder for Luna SDK - Build Solana transactions by typing in plain English"

dependencies {
    api(project(":luna-core"))
    api(project(":luna-rpc"))
    api(project(":luna-das"))
    api(project(":luna-enhanced-tx"))
    api(project(":luna-priority"))
    api(project(":luna-jupiter"))
    
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            pom {
                name.set("Luna NLP")
                description.set("Natural Language Transaction Builder for Luna SDK - Build Solana transactions by typing in plain English")
                url.set("https://github.com/ArcMichael/Helius-Selenus-Kotlin-LunaSDK")
                
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
                developers {
                    developer {
                        id.set("moonmanquark")
                        name.set("Quark")
                        email.set("quark@bluefoot.tech")
                    }
                }
                
                scm {
                    url.set("https://github.com/ArcMichael/Helius-Selenus-Kotlin-LunaSDK")
                    connection.set("scm:git:git://github.com/ArcMichael/Helius-Selenus-Kotlin-LunaSDK.git")
                    developerConnection.set("scm:git:ssh://github.com/ArcMichael/Helius-Selenus-Kotlin-LunaSDK.git")
                }
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["maven"])
}

tasks.test {
    useJUnitPlatform()
}
