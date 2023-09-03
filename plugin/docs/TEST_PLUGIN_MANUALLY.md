# Test Plugin Manually
If docker is installed, please read [TEST_PLUGIN_AUTOMATED.md](TEST_PLUGIN_AUTOMATED.md).

The plugin is automatically available (installed) in the PyCharm instance started by the gradle-task `runIde`.
In case you want to test the plugin in another locally installed IntelliJ/PyCharm, use the following steps:

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
The mentioned steps should be checked in the listed order, because they depend on each other.

### 1) Plugin Code Is UpToDate
Please double-check that, for every modified project under `<PROJECTS_DIR>/python_plugin_code` the `main.py` was executed to generate an updated `generated/plugin_code` file for these projects.

If one of the `generated/plugin_code` has changed, the started PyCharm instance has to be restarted by the gradle-task `runIde`. 
This will automatically copy over the re-generated `plugin_code` files to the plugin, and starts PyCharm with these updated files.

### 2) Plugin Can Fetch Data From PyCharm Debugger
The `<PROJECTS_DIR>/html_from_styler` provide prepared pandas Styler. 
The plugin has a builtin action named `ExportDataFrameTestDataAction` which fetches the HTML representation of styled DataFrames using the PyCharm debugger.

Please do **all** mentioned steps from [GENERATE_TEST_DATA_MANUALLY.md](GENERATE_TEST_DATA_MANUALLY.md)

After the data was successfully exported, it is already verified that:
- the version of the installed pandas was correct detected by the plugin
- the python plugin code was successfully injected using the PyCharm debugger
- fetching data from a styled DataFrame works

### 3) Plugin Can Parse Data From Pandas Styler
After all files are re-exported run the unit tests (gradle-task `test` under group `verification`) of the plugin project to check if everything is OK.

The generated resources are used by the unit tests `ChunkValidationTest`.

#### ChunkValidationTest
Loads for all supported pandas versions the generated data from the `src/test/resources/generated/` folder.

ensures:
- combined chunks result in the same visual output as an un-chunked version of the content
   - correctness of computed CSS values can't be verified by this test
- parser can parse the generated chunks

In case the combined chunks don't match with the expected result, a screenshot is taken of the rendered DataFrame tables.
The screenshots are created in a folder named by the failed test case under `src/test/resources/generated-error-images/chunk-validation`.
