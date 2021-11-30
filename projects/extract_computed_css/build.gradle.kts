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
    implementation("commons-cli:commons-cli:1.4")
}

application {
    mainClass.set("MainKt")
}

tasks.register("extractComputedCSSForPlugin") {
    group = "application"
    dependsOn(tasks.build)
    doLast {
        tasks.run.get().setArgs(
            listOf(
                "extract",
                "-d",
                "$projectDir/../../plugin/src/test/resources/generated/",
                "-i",
                "expected.html",
                "-o",
                "expected.css-html"
            )
        ).exec()
    }
}