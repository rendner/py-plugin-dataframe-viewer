# Plugin Test Data
It seems not so easy to run unit tests against a PyCharm debugger, at least I couldn't find a way. Therefore, some tests use pre-generated data.

The pre-generated data is extracted from the PyCharm debugger in the same way as the plugin does.

## Re-Generate Plugin Test Data
Whenever plugin related code has changed, which could affect one of the following parts:

- communication between Python and the PyCharm debugger
- the structure of the returned html string

All files of the affected pandas versions supported by the plugin have to be re-generated. 

## Generate Test Data
The test data has to be generated in separate steps.
The generated files are created under `src/test/resources/generated/<pandas_x.y>/...`.

### [Step 1] - Delete Previous Generated Test Data
Delete the generated pandas test data for all modified projects. 
No worry, the data can be re-created by following the next steps.
This helps to get rid of generated test data for deleted or restructured test cases.

In case you are unsure which data should be deleted, here are some examples:

- if you modified `<PROJECTS_DIR>/python_plugin_code/pandas_1.1_code` 
  - delete the folder `src/test/resources/generated/pandas1.1` of the plugin project
  

- if you modified `<PROJECTS_DIR>/html_from_styler/pandas_1.1_styler`
  - delete the folder `src/test/resources/generated/pandas1.1` of the plugin project


- if you modified how the generated python plugin code is injected
  - delete all sub-folders of `src/test/resources/generated/` of the plugin project

### [Step 2] - Export HTML From Python
**Precondition:**
Run `runIde` gradle-task from IntelliJ, this will start a PyCharm instance with the plugin installed

For each pandas version from which the generated test files were deleted in **[Step 1]**:
  1. open the corresponding project from `<PROJECTS_DIR>/html_from_styler/` in PyCharm
      - for initial setup of such a project check [README.md of the "html_from_styler" projects](../../projects/html_from_styler/README.md)
  2. run `export_data/main.py` in debug mode
  3. in the debugger tab, right-click on `export_test_data` to open the context menu
  4. select `Export DataFrame Test Data` from the context menu
      - this starts the export of the HTML files
      - the progress of the export can be monitored in the console of the IntelliJ instance

### [Step 3] - Compute CSS For Exported HTML
1. open the project `<PROJECTS_DIR>/extract_computed_css` in IntelliJ
    - for initial setup please check [README.md of the tool](../../projects/extract_computed_css/README.md)
2. run gradle-task `extractComputedCSSForPlugin` (from group `application`) from `Gradle Tool Window` in IntelliJ
    - this will compile and run the tool
    - a window with the embedded browser is temporary displayed
    - the tool exits automatically afterwards