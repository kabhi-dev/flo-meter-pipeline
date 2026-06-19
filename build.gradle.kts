plugins {
    kotlin("jvm") version "2.3.21"
    application
}

group = "com.flo.nem12"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    // Explicit entry point for the application plugin.
    mainClass.set("com.flo.nem12.MainKt")
    applicationName = "flo-meter-pipeline"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
