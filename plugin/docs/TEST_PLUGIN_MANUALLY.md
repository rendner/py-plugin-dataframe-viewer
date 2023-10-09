# Test Plugin Manually
If docker is installed, please read [TEST_PLUGIN_AUTOMATED.md](TEST_PLUGIN_AUTOMATED.md).

The plugin is automatically available (installed) in the PyCharm instance started by the gradle-task `runIde`.
In case you want to test the plugin in another locally installed IntelliJ/PyCharm, use the following steps:

1. run the gradle-task `buildPlugin`, to build the plugin
2. open the new IntelliJ/PyCharm Release
   1. if IntelliJ is used please install the python plugin from `Jetbrains`
3. follow the instructions from [install plugin from disk](https://www.jetbrains.com/help/idea/managing-plugins.html#install_plugin_from_disk)
   1. navigate to `<PLUGIN_DIR>/distributions/` and select version to install
4. open one of the projects from `<PROJECTS_DIR>/python_plugin_code/`
   1. configure the required Python interpreter
5. use the test files from the `manual_testing` directory
   1. plugin works as expected if the debugger can load and display data of the styled DataFrame
      1. read [the general documentation](../../README.md#how-does-it-work) for a detailed description how to interact with the debugger

## Tests After Code Modification

### 1) Plugin Code Is UpToDate
Please double-check that, for every modified project under `<PROJECTS_DIR>/python_plugin_code` the `main.py` was executed to generate an updated `generated/plugin_code` file for these projects.

If one of the `generated/plugin_code` has changed, the started PyCharm instance has to be restarted by the gradle-task `runIde`. 
This will automatically copy over the re-generated `plugin_code` files to the plugin, and starts PyCharm with these updated files.

### 2) Do Tests
The `<PROJECTS_DIR>/python_plugin_code` projects provide test files in the `manual_testing` directories.
Run these files with the Python debugger and use the plugin dialog to validate the displayed data.

