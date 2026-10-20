// ASTRA core — pure Kotlin (no Android). Shared by the app (M2+) and tested on the JVM.
plugins { id("org.jetbrains.kotlin.jvm") }

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    testLogging { events("passed", "failed"); showStandardStreams = true }
}
