# HTML From Styler
This directory contains projects, separated by the supported pandas version, for generating test data for the plugin.

These projects can also be used to verify that the plugin can interact with the PyCharm debugger.

## Pre-Requirements
All projects are set up via [pipenv](https://pypi.org/project/pipenv/) and require Python 3.7, the minimum version required by the supported pandas versions. 

- [ho to install Python 3.7 on Ubuntu](https://stackoverflow.com/questions/61430166/python-3-7-on-ubuntu-20-04)

## Running These Projects (preferred way)
The test data for the plugin can only be generated when using a PyCharm instance started by the gradle-task `runIde` of the plugin project.
A more detailed description can be found in [PLUGIN_TEST_DATA.md](../../plugin/docs/PLUGIN_TEST_DATA.md)

## Configure Python Interpreter (PyCharm)
After importing one of the Python projects, open the `Add Python Interpreter` dialog:
- select `PipEnv Environment`
- set `Base Interpreter` to your installed Python 3.7 (min required version of the pandas versions)

A more detailed description can be found in thy official PyCharm documentation - [Configure pipenv for an existing Python project](https://www.jetbrains.com/help/pycharm/pipenv.html#pipenv-existing-project).

## Project Structure Of The Projects
The structure below lists only the important folders/files.
```text
├── export_data
│   ├── main.py
│   └── parsing
└── manual_testing
    └── styles.py
```

### File: export_data/main.py
Running the `export_data/main.py` via the PyCharm debugger, started from the plugin project, allows generating test data for the plugin.

#### How To Run
>**Note:** The project has to be started by a PyCharm instance, started from the `runIde` gradle-task of the plugin project.

1. run `export_data/main.py` in debug mode
2. in the debugger tab, right-click on `export_test_data` to open the context menu
3. select `Export DataFrame Test Data` from the context menu
    - (option is only available if started via `runIde` gradle-task of the plugin project)
    - this starts the export of the HTML files
    - the progress of the export can be monitored in the console of the IntelliJ instance

#### Generated Output
The following files are created for each pandas `Styler` instance listed in `export_test_data`:
- `expected.html`
    - contains the HTML as returned by pandas `Styler.render()`
- multiple `r<X>_c<Y>.html` files
    - each one contains one chunk as fetched by the plugin
    - `<X>` index of first row of the chunk
    - `<Y>` index of first column of the chunk
- `test.properties`
    - contains some required information about the structure of the `DataFrame` represented by the HTML files

### Directory: export_data/parsing
The directory `export_data/parsing`contains pre-configured styled DataFrames which are automatically picked up the code when running the `export_data/main.py` file.

A test file has to specify a dictionary named `test_case` with the following two keys:

| key        | purpose                                                 |
|:-----------|:--------------------------------------------------------|
| styler     | The styler to dump. Used to generate test data from it. |
| chunk_size | The chunk size to use when dumping the `styler`.        |

Nearly all tests specify small styled `DataFrames`, because the generated data is used to validate if the HTML can be parsed and is combined correctly.

The benefit of small test cases is, that in a short amount of time many test cases can be fetched from the debugger without running into a timeout exception.

### Directory: manual_testing
The directory contains one example of a large `DataFrame` and should be used to manually test the fetch behavior of the plugin when scrolling around.

- debug the file
- in the debugger tab, right-click on a pandas `Styler` instance to open the context menu
- select `Export DataFrame Test Data` from the context menu