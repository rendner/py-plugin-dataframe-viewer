# Test Plugin
There is no automated way, to test the plugin against a running PyCharm debugger.
Therefore, this has to be tested by hand before every new release or pull request.

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
The mentioned steps should be checked in the listed order, because they depend on each other.

### 1) Plugin Code Is UpToDate
Please double-check that, for every modified project under `<PROJECTS_DIR>/python_plugin_code` the `main.py` was executed to generate an updated `generated/plugin_code` file for these projects.

If one of the `generated/plugin_code` has changed, the started PyCharm instance has to be restarted by the gradle-task `runIde`. 
This will automatically copy over the re-generated `plugin_code` files to the plugin, and starts PyCharm with these updated files.

### 2) Plugin Can Fetch HTML From PyCharm Debugger
The `<PROJECTS_DIR>/html_from_styler` provide prepared pandas Styler. 
The plugin has a builtin action named `ExportDataFrameTestDataAction` which fetches the HTML representation of styled DataFrames using the PyCharm debugger.

Please do **all** mentioned steps from [PLUGIN_TEST_DATA.md](PLUGIN_TEST_DATA.md)

After the HTML was successfully exported, it is already verified that:
- the version of the installed pandas was correct detected by the plugin
- the python plugin code was successfully injected using the PyCharm debugger
- fetching the HTML output of a styled DataFrame works

### 3) Plugin Can Parse HTML From Pandas Styler
After all files are re-exported run the unit tests (gradle-task `test` under group `verification`) of the plugin project to check if everything is OK.

The generated resources are used by the unit tests `ChunkValidationTest` and `CSSValidationTest`. 
These are the most important ones of the plugin project.

#### ChunkValidationTest
Loads for all supported pandas versions the generated data from the `src/test/resources/generated/` folder.

This test class ensures:
- that the combined chunks result in the same visual output as using the unchunked HTML
  - correctness of computed CSS values can't be verified by this test
- that the HTML parser can parse the generated html
- the chunks are combined in the correct order

In case the combined chunked HTML does not match the expected HTML, a screenshot is taken of the rendered DataFrame tables.
The screenshots are created in a folder named by the failed test case under `src/test/resources/generated-error-images/chunk-validation`.

#### CSSValidationTest
Loads for all supported pandas versions the generated data from the `src/test/resources/generated/` folder.

This test class ensures:
- that the by the plugin computed CSS values match with the ones computed by a web browser

In case the calculated CSS does not match the expected CSS, a screenshot is taken of the rendered DataFrame tables.
The screenshots are created in a folder named by the failed test case under `src/test/resources/generated-error-images/css-validation`.