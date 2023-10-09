# Python Plugin Code
This directory contains the Python code, separated by the supported pandas version, which is executed by the PyCharm debugger to fetch the required data from a pandas `DataFrame`.

Whenever the code was modified in these projects, the `main.py` file of the modified projects has to be executed to re-generate the `generated/plugin_code` file.
The generated plugin-code files are automatically copied to the `<PLUGIN_DIR>src/main/resources` directory whenever the gradle-task `processResources` of the plugin project is executed. 
Thus, these files do not have to be copied by hand.

## Pre-Requirements
All projects are set up via [pipenv](https://pypi.org/project/pipenv/) and use the minimum Python version required by pandas.

| pandas    | min required Python version |
|:----------|:---------------------------:|
| 1.1 - 1.3 |             3.7             |
| 1.4 - 2.0 |             3.8             |
| >= 2.1    |             3.9             |


It is important to use the specified minimum required version to guarantee that the plugin code is also compatible with the specified Python version.

### Older Python Versions
- [how to install older versions of Python on Ubuntu](https://stackoverflow.com/questions/61430166/python-3-7-on-ubuntu-20-04) (works also for Ubuntu 22)
- run a test with `python3.x -m pip` (if you installed Python 3.7 => `python3.7 -m pip`)
- in case of `ModuleNotFoundError: No module named 'distutils.cmd'`
- run `sudo apt install python3.x-distutils` (if you installed Python 3.7 => `sudo apt install python3.7-distutils`)

### Check Pipenv
- run `pipenv`
- in case of `AttributeError: module 'collections' has no attribute 'MutableMapping'` 
  - ["pipenv" fails with AttributeError after upgrading from ubuntu 20.04 to 22.04](https://github.com/pypa/pipenv/issues/5088)
  - [the pipenv package as it is seems incompatible with python 3.10 and 3.11 (jammy and kinetic)](https://bugs.launchpad.net/ubuntu/+source/pipenv/+bug/1998280)

## Running These Projects (preferred way)
The easiest way is to open these projects from a PyCharm instance started by the `runIde` gradle-task (no need for an additional PyCharm installation).
To do this, open the plugin project (located in the root folder) and run the `runIde` gradle-task. This will bring up a new PyCharm instance.

Then open one of these projects, if it is the first time, you have to configure a Python interpreter (use the correct Python version). 

>**Note:** The project can be executed with any PyCharm instance, therefore it doesn't need to be started by the `runIde` gradle-task of the plugin project.
>But using the PyCharm instance, started by the plugin project, allows you to use the latest state of the plugin in case you want to do a quick test
> with the plugin.

## Configure Python Interpreter (PyCharm)
After importing one of the Python projects, open the `Add Python Interpreter` dialog:
- select `PipEnv Environment`
- set `Base Interpreter` to the min required Python version (see table from `Pre-Requirements`)

A more detailed description can be found in thy official PyCharm documentation - [Configure pipenv for an existing Python project](https://www.jetbrains.com/help/pycharm/pipenv.html#pipenv-existing-project).

## Configure pytest In IntelliJ
These projects use `pytest` to run unit tests. `pytest` has to be enabled in IntelliJ:
- open IntelliJ `Settings` dialog
- navigate to `Tools` -> `Python Integrated Tools`
- under `Testing` select `pytest` as default test runner

A more detailed description can be found here: [IntelliJ - enable-pytest](https://www.jetbrains.com/help/pycharm/pytest.html#enable-pytest)

## Project Structure Of The Projects
The structure below lists only the important folders/files.
```text
├── generated
│   └── plugin_code
├── main.py
├── plugin_code
│   └── patched_styler.py
│   └── styled_data_frame_viewer_bridge.py
│   └── ...
└── tests
```

### File: main.py
Is used to generate the script file `generated/plugin_code` (used by the plugin). 
All explicit listed py-files are taken into account.

### Directory: plugin_code
The directory contains all the code required by the plugin to fetch data from a styled `DataFrame`. 
All files from this directory, which are explict listed in the `main.py` file, are copied into `generated/plugin_code`.

Each file has to contain the marker line: ```# == copy after here ==```

Only the code below the marker will be copied into `generated/plugin_code`. 
All code above the marker is excluded. 
Since all files are copied into a single script file, all imports from `plugin_code` are removed. 
Therefore, this kind of imports have to be above the mentioned marker.
This requires some extra care, but simplifies the code to generate the script.

Comments starting with a `#` are excluded from the generated script file.
Please put comments always on an extra line, so that they are completely excluded from the generated script.

>**Note:**
The folder `plugin_code` can't be renamed to `code` because this will cause problems with IntelliJ.
In general there should be no package or file with the name `code` because this results in an error and
prevents you from using the debugger.
>
> For more detailed info about these problems read:
>- https://youtrack.jetbrains.com/issue/PY-37283
>- https://youtrack.jetbrains.com/issue/PY-38859
 

#### PatchedStyler.py
This class is used by the plugin (Kotlin part) to extract information from a pandas `Styler`.
Therefore, it is mandatory that the method signatures of this class match those used by the `PyPatchedStylerRef` counterpart of the plugin project.

Short overview about the public methods of the class:

| Method                             | used by the plugin | purpose                                                                                   |
|:-----------------------------------|:------------------:|:------------------------------------------------------------------------------------------|
| compute_chunk_table_frame          |         X          | To extract a table like structure of a chunk.                                             |
| get_org_indices_of_visible_columns |         X          | Used to identify the right column after filtering, to restore the column state.           |
| get_style_function_details         |         X          | To extract information about used styling functions.                                      |
| get_table_structure                |         X          | To extract initial information about a DataFrame.                                         |
| internal_get_context               |         -          | Required for unit tests.                                                                  |
| set_sort_criteria                  |         X          | To set the sort criteria for the styled DataFrame.                                        |
| to_json                            |         X          | To convert method results into JSON. Simplifies the parsing of the results in the plugin. |
| validate_style_functions           |         X          | To validate styling functions.                                                            |

#### StyledDataFrameViewerBridge.py
This class is used by the plugin (Kotlin part) to create `PatchedStyler` instances.
Therefore, it is mandatory that the method signatures of this class match those used by the `PythonCodeBridge` counterpart of the plugin project.

Short overview about the public methods of the class:

| Method                | used by the plugin | purpose                                     |
|:----------------------|:------------------:|:--------------------------------------------|
| create_patched_styler |         X          | To create a `PatchedStyler` instance.       |

### Directory: test
The directory `tests` contains the unit tests for the Python plugin code.
Therefore, each supported builtin style implementation has its own set of tests.

It is also possible to write unit tests that will only be executed when run with a specific Pandas version (currently only in `pandas_1.3_code`):
```python
@pytest.mark.skipif(not_required_pandas_version(">=1.3.2"), reason="at least pandas-1.3.2 required")
```
`not_required_pandas_version` uses [python-semanticversion](https://pypi.org/project/semantic-version/) to parse the specified version string.

All unit tests are executed against the installed dependencies listed in the `Pipfile`. 

You have to manually downgrade the dependencies if you want to verify that your changes also work for on older revision as the current installed one.
It would be good to have an easy way to switch between different versions. 
But this wasn't required in the past.
