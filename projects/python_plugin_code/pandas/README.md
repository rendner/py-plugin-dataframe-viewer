# pandas
This directory contains the Python code, separated by the supported pandas version, which is executed by the PyCharm debugger to fetch the required data from a pandas `DataFrame`.

Whenever the code was modified in these projects, the `main.py` file of the modified projects has to be executed to re-generate the `generated/plugin_modules_dump.json` file.
The generated `plugin_modules_dump.json` files are automatically copied to the `<PLUGIN_DIR>src/main/resources` directory whenever the gradle-task `processResources` of the plugin project is executed. 
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
.
├── generated
│   └── plugin_modules_dump.json
├── main.py
├── manual_testing
├── src
│   └── cms_rendner_sdfv
│       ├── pandas
│       ├── frame
│       ├── shared
│       └── styler
└── tests
```

### Directory: generated
Contains the dumped package structure from `src/cms_rendner_sdfv/pandas`. The file is loaded by the plugin to fetch table like data from pandas objects.

### File: main.py
Generates the `plugin_modules_dump.json` (used later by the plugin).

### Directory: manual_testing
Contains minimal examples which can be used to test the installed plugin.
The plugin is automatically available (installed) in the PyCharm instance started by the gradle-task `runIde` of the plugin project.

### Directory: src/cms_rendner_sdfv/pandas
The directory contains all the code required by the plugin to fetch data from a pandas `DataFrame` or `Styler`. 
All files from this directory, are dumped into `generated/plugin_modules_dump.json`.

Comments starting with a `#` are excluded from the generated `plugin_modules_dump.json`.
Please put comments always on an extra line, so that they are completely excluded.

#### Subfolder: shared
Contains all code which is shared between the code in `frame` and `styler`. Since the code is shared it must not import or use a `Styler`.
This can be verified by running the unit tests with the option `--no-jinja`.

#### Subfolder: frame   
Contains all code used to extract the table like data from a `DataFrame`. 

#### Subfolder: styler   
Contains all code used to extract the table like data from a styled `DataFrame`.

### Directory: tests
Contains the unit tests for the Python plugin code.

