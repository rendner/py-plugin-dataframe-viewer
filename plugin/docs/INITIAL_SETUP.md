# Initial Setup
These instructions will help you to build the plugin.

## Building The Plugin
Version 2022.3 or later of IntelliJ IDEA Community Edition or IntelliJ IDEA Ultimate Edition is required to build and develop the plugin.

### Build Configuration
Configure a JDK 17 as `Project SDK` to run and compile the plugin.

see: [Configure SDKs](https://www.jetbrains.com/help/idea/sdk.html#define-sdk)

### Gradle Configuration
Configure Gradle to use the `Project SDK`, configured in the previous step.

see: [Gradle JVM settings](https://www.jetbrains.com/help/idea/gradle-jvm-selection.html#jvm_settings)

## Running The Plugin
To start a PyCharm instance with the latest state of the plugin use the gradle tool window and run the gradle-task `runIde`.

Open a Python project which uses pandas to try the plugin.
