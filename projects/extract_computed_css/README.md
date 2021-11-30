# Extract Computed CSS

This command line tool extracts the computed CSS values for each table element by using [JCEF](https://github.com/chromiumembedded/java-cef).
The generated output file contains the computed CSS styles, stored in the `style` attribute of each element. 

>Note: CSS rules are not re-exported!

## Setup
### JDK
To minimize the configuration complexity, the tool requires a [JBR 11 with JCEF](https://confluence.jetbrains.com/pages/viewpage.action?pageId=221478946) as Project SDK.
Later versions could be incompatible.

### Gradle JVM
The Gradle JVM should be set to `Project SDK` to point to a **JBR with JCEF**: [Access the Gradle JVM settings](https://www.jetbrains.com/help/idea/gradle-jvm-selection.html#jvm_settings)

## Run The Tool
There is a preconfigured gradle-task named `extractComputedCSSForPlugin` (group `application`) to generate computed test data for the plugin.

The task will:
  - build the tool
  - run the tool with the right configured parameters

The tool iterates recursive over an input directory and generates for each `expected.html` file in `<PLUGIN_DIR>/src/test/resources/generated/` an `expected.css-html`.

A more detailed description how to generate test data can be found in [PLUGIN_TEST_DATA.md](../../plugin/docs/PLUGIN_TEST_DATA.md)

## Upgrading JBR
In case the tool has to be migrated to a newer "JBR with JCEF". The following steps have to be done:

* adapt the code of the `MainFrame` class from [JetBrains jcef repo](https://github.com/JetBrains/jcef/blob/master/java_tests/tests/simple/MainFrame.java) in `example.simple.MainFrame`
    * convert to `Kotlin` code (is done by IntelliJ if pasted into a kotlin file)
    * add a `getClient` method to allow access the `CefClient` instance
* fix problems in `CSSStyleComputer`, which implements `CefMessageRouterHandlerAdapter` and `CefLoadHandler`.

