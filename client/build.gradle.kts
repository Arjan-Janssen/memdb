plugins {
    kotlin("jvm") version ("2.2.20")
    alias(libs.plugins.google.protobuf)
    alias(libs.plugins.arturbosch.detekt)
    alias(libs.plugins.jlleitschuh.ktlint)
    alias(libs.plugins.dorongold.tasktree)
}

repositories {
    mavenCentral()
}

buildscript {
    dependencies {
        classpath(libs.google.protobuf.gradle.plugin)
        classpath(libs.jetbrains.kotlin.gradle.plugin)
    }
}
