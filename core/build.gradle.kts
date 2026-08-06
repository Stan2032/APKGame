plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

/**
 * Renders sample system pads as .wav so they can be listened to. Sound is the
 * one generated thing in this project that measurement cannot sign off, so it
 * has to be auditionable without a device (AmbienceWav).
 *
 *     ./gradlew :core:ambienceWav
 */
tasks.register<JavaExec>("ambienceWav") {
    group = "verification"
    description = "Write sample Ambience pads to core/build/ambience as .wav for listening."
    mainClass.set("io.github.stan2032.gravitygambit.core.AmbienceWav")
    classpath = sourceSets["test"].runtimeClasspath
    args(layout.buildDirectory.dir("ambience").get().asFile.absolutePath)
}
