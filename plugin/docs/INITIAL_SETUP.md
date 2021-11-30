# Initial Setup
These instructions will help you build the plugin.

## Building The Plugin
Version 2021.1 or newer of IntelliJ IDEA Community Edition or IntelliJ IDEA Ultimate Edition is required to build and develop the plugin.
The plugin is currently developed with the IntelliJ IDEA Community Edition.

### Build Configuration
Configure a JDK 11 as `Project SDK` to run and compile the plugin.

It's recommended to install a [JBR 11 with JCEF](https://confluence.jetbrains.com/pages/viewpage.action?pageId=221478946) as Project SDK, 
because such a JDK is needed anyway to run the tool `<PROJECTS_DIR>/extract_computed_css`. 

## Running The Plugin
To start a PyCharm instance with the latest state of the plugin use the gradle tool window and run the gradle-task `runIde`.

Open a Python project which uses pandas to try the plugin.
