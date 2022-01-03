# Test Plugin Automated
Most parts of the plugin can be tested automatically, if docker is installed on your operating system (OS).

If docker isn't installed, please read [TEST_PLUGIN_MANUALLY.md](TEST_PLUGIN_MANUALLY.md).

## Requirements
All listed requirements have to be fulfilled.
### Docker
Docker has to be installed and `docker` commands can be run directly from a terminal/shell.

See: https://docs.docker.com/engine/install/linux-postinstall/#manage-docker-as-a-non-root-user

### JDK
A [JBR 11 with JCEF](https://confluence.jetbrains.com/pages/viewpage.action?pageId=221478946) has to be installed and one of the following environment variable has to be set:
- `JAVA_HOME` which points to the folder where the JBR 11 with JCEF was installed
- or `JCEF_11_JDK` which points to the folder where the JBR 11 with JCEF was installed

>Note: On Linux you can store the environment variable in `~/.profile`.

## Test Against New IntelliJ Release
1. run the gradle-task `buildPlugin`, to build the plugin
2. open the new IntelliJ/PyCharm Release
   1. if IntelliJ is used please install the python plugin from `Jetbrains`
3. follow the instructions from [install plugin from disk](https://www.jetbrains.com/help/idea/managing-plugins.html#install_plugin_from_disk)
   1. navigate to `<PLUGIN_DIR>/distributions/` and select version to install
4. open one of the projects from `<PROJECTS_DIR>/html_from_styler/`
   1. configure the required Python interpreter
5. use the test file from the `manual_testing` directory
   1. plugin works as expected if the debugger can load and display data of the styled DataFrame
      1. read [the general documentation](../../README.md#how-does-it-work) for a detailed description how to interact with the debugger

## Test After Code Modification
The mentioned steps should be done in the listed order, because they depend on each other.

### 1) Plugin Code Is UpToDate
Please double-check that, for every modified project under `<PROJECTS_DIR>/python_plugin_code` the `main.py` was executed to generate an updated `generated/plugin_code` file for these projects.

### 2) Rebuild Docker Image
Run the gradle-task `buildPythonDockerImage` (group `docker`).

### 3) Run Integration Tests
Run gradle-task `integrationTest_all` (group `verification`).

### 4) Run Generate Test Data
Run gradle-task `generateTestData_all` (group `generate`).

This task deletes all existing test data from `src/test/resources/generated`, and re-creates the deleted files afterwards.

### 5) Run Unit-Tests
Run the gradle-task `test` (group `verification`).

The generated resources are used by the unit-tests `ChunkValidationTest` and `CSSValidationTest`.

#### ChunkValidationTest
Loads for all supported pandas versions the generated data from the `src/test/resources/generated/` folder.

ensures:
- combined chunks result in the same visual output as using the unchunked HTML
   - correctness of computed CSS values can't be verified by this test
- HTML parser can parse the generated html
- chunks are combined in the correct order

In case the combined chunked HTML does not match the expected HTML, a screenshot is taken of the rendered DataFrame tables.
The screenshots are created in a folder named by the failed test case under `src/test/resources/generated-error-images/chunk-validation`.

#### CSSValidationTest
Loads for all supported pandas versions the generated data from the `src/test/resources/generated/` folder.

ensures:
- computed CSS values match with the ones computed by a web browser

In case the calculated CSS does not match the expected CSS, a screenshot is taken of the rendered DataFrame tables.
The screenshots are created in a folder named by the failed test case under `src/test/resources/generated-error-images/css-validation`.