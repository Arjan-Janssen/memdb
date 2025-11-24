import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("jvm") version ("2.2.20")
    alias(libs.plugins.arturbosch.detekt)
    alias(libs.plugins.jlleitschuh.ktlint)
    alias(libs.plugins.google.protobuf)
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.google.protobuf.kotlin)
    implementation(libs.jetbrains.kotlin.stdlib)
    testImplementation(libs.test.junit.jupiter)
    testRuntimeOnly(libs.test.junit.launcher)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.32.1"
    }
}

kotlin {
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        jvmTarget.set(JvmTarget.JVM_21)
        allWarningsAsErrors = true
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(":generateProto")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    maxHeapSize = "1G"
    testLogging {
        events("passed")
    }
}
