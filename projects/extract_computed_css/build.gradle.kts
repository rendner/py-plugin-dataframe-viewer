plugins {
    kotlin("jvm") version "1.5.10"
    application
}

group = "cms.rendner"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jsoup:jsoup:1.14.3")
    implementation("commons-cli:commons-cli:1.5.0")
}

application {
    mainClass.set("MainKt")
}

tasks.register("extractComputedCSSForPlugin") {
    group = "application"
    dependsOn(tasks.build)
    doLast {
        val inputDir = project.properties["inputDir"] ?: "$projectDir/../../plugin/src/test/resources/generated/"
        tasks.run.get().setArgs(
            listOf(
                "extract",
                "-d",
                inputDir,
                "-i",
                "expected.html",
                "-o",
                "expected.css-html"
            )
        ).exec()
    }
}