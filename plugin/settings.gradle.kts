pluginManagement {
    repositories {
        gradlePluginPortal()
    }
    plugins {
        id("org.jetbrains.intellij.platform") version "2.12.0"
        id("org.jetbrains.kotlin.jvm") version "2.1.10"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.1.10"
    }
}

rootProject.name = "plugin-dataframe-viewer"
