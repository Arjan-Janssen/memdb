plugins {
    kotlin("jvm") version ("2.2.20")
    alias(libs.plugins.google.protobuf)
    alias(libs.plugins.arturbosch.detekt)
    alias(libs.plugins.jetbrains.dokka)
    alias(libs.plugins.jlleitschuh.ktlint)
    alias(libs.plugins.dorongold.tasktree)
}

repositories {
    mavenCentral()
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")
}
