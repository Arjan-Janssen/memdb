import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("jvm") version ("2.2.20")
    alias(libs.plugins.arturbosch.detekt)
    alias(libs.plugins.jlleitschuh.ktlint)
    alias(libs.plugins.dorongold.tasktree)
    application
}

repositories {
    mavenCentral()
}

buildscript {
    dependencies {
        classpath(libs.jetbrains.kotlin.gradle.plugin)
    }
}

dependencies {
    implementation(project(":lib"))
    implementation(libs.jetbrains.kotlin.stdlib)
    implementation(libs.jetbrains.kotlinx.cli)
    testImplementation(libs.test.junit.jupiter)
    testRuntimeOnly(libs.test.junit.launcher)
}

kotlin {
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        jvmTarget.set(JvmTarget.JVM_21)
        allWarningsAsErrors = true
    }
}


tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.janssen.memdb.MainKt"
    }
}

tasks.register<Jar>("uberJar") {
    archiveClassifier = "uber"
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath
            .get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    maxHeapSize = "1G"
    testLogging {
        events("passed")
    }
}
