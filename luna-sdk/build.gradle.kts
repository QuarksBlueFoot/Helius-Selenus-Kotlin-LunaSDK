plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `maven-publish`
    signing
}

kotlin {
    jvmToolchain(21)
}

group = property("GROUP_ID") as String
version = property("VERSION_NAME") as String

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation(kotlin("stdlib"))

    testImplementation(kotlin("test"))
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
    from(files()) // Empty Javadoc JAR to satisfy Maven Central requirements
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(sourcesJar)
            artifact(javadocJar)

            pom {
                name.set("LunaSDK")
                description.set(property("POM_DESCRIPTION") as String)
                url.set(property("POM_URL") as String)
                licenses {
                    license {
                        name.set(property("POM_LICENSE_NAME") as String)
                        url.set(property("POM_LICENSE_URL") as String)
                        distribution.set(property("POM_LICENSE_DIST") as String)
                    }
                }
                developers {
                    developer {
                        id.set(property("POM_DEVELOPER_ID") as String)
                        name.set(property("POM_DEVELOPER_NAME") as String)
                    }
                }
                scm {
                    url.set(property("POM_SCM_URL") as String)
                    connection.set(property("POM_SCM_CONNECTION") as String)
                    developerConnection.set(property("POM_SCM_DEV_CONNECTION") as String)
                }
            }
        }
    }
    repositories {
        maven {
            name = "OSSRH"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("OSSRH_USERNAME")
                password = System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

signing {
    val signingKey = System.getenv("SIGNING_KEY")
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["maven"])
}
