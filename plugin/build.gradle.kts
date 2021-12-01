import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.intellij") version "1.0"
    // https://plugins.jetbrains.com/docs/intellij/kotlin.html#kotlin-standard-library
    kotlin("jvm") version "1.4.0" // provided by 2020.3
    kotlin("plugin.serialization") version "1.4.0"
}

group = "cms.rendner.intellij"
version = "0.5.1"

val mockitoVersion = "4.0.0"

val exportTestDataPath = "$projectDir/src/test/resources/generated/"
val exportTestErrorImagesPath = "$projectDir/src/test/resources/generated-error-images/"

repositories {
    mavenCentral()
}

dependencies {
    // is provided by the intellij instance which runs the plugin
    compileOnly(kotlin("stdlib"))

    implementation("org.jsoup:jsoup:1.14.3")
    implementation("net.sourceforge.cssparser:cssparser:0.9.29")
    implementation("org.beryx:awt-color-factory:1.0.2")

    // https://github.com/junit-team/junit5-samples/blob/r5.8.1/junit5-jupiter-starter-gradle-kotlin/build.gradle.kts
    testImplementation(platform("org.junit:junit-bom:5.8.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("org.mockito:mockito-junit-jupiter:$mockitoVersion")

    testImplementation("org.assertj:assertj-core:3.21.0")

    // latest usable version for kotlin version 1.4.0
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
// https://intellij-support.jetbrains.com/hc/en-us/community/posts/206590865-Creating-PyCharm-plugins
intellij {
    plugins.add("python-ce") // is required even if we specify a pyCharm ide
    version.set("2020.3")
    type.set("PC")
    downloadSources.set(false)
    updateSinceUntilBuild.set(false)
}

// https://github.com/nazmulidris/idea-plugin-example/blob/main/build.gradle.kts
tasks {
    // https://stackoverflow.com/questions/56628983/how-to-give-system-property-to-my-test-via-kotlin-gradle-and-d
    test {
        systemProperty("cms.rendner.dataframe.renderer.export.test.data.dir", exportTestDataPath)
        systemProperty("cms.rendner.dataframe.renderer.export.test.error.image.dir", exportTestErrorImagesPath)
        useJUnitPlatform()
        failFast = true
    }

    buildSearchableOptions {
        enabled = false
    }

    patchPluginXml {
        version.set(project.version.toString())
    }

    runIde {
        systemProperty("cms.rendner.dataframe.renderer.enable.test.data.export", true)
        systemProperty("cms.rendner.dataframe.renderer.export.test.data.dir", exportTestDataPath)
        // ideDir.set(File("/snap/intellij-idea-community/current"))
    }

    processResources {
        logger.lifecycle("copy 'plugin_code' files")
        listOf("pandas_1.1", "pandas_1.2", "pandas_1.3").forEach { version ->
            val from = project.file("../projects/python_plugin_code/${version}_code/generated/plugin_code")
            if(from.exists()) {
                copy {
                    from(from)
                    into(project.file("src/main/resources/$version"))
                }
            } else {
                logger.error("Missing file 'plugin_code' for version: $version")
            }
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
    }
}