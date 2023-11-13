# Python Plugin Code
This directory contains the Python code, which is executed by the PyCharm debugger to fetch the required data for a `DataFrame` like structure.

## Directory: sdfv_base
This directory contains the interface used by the plugin to extract data from the debugger.
All code in this project is written for the lowest supported Python version (currently Python 3.7).

Each supported data frame library, like `pandas`, has to implement these interfaces and provide the implementation as a "package dump".
A "package dump" is a JSON file which contains a dictionary like structure of the code to use. 
A "package dump" can be created by calling `generate_plugin_modules_dump` from `tools/generate_plugin_modules_dump.py`.

The `sdfv_base` project provides an import mechanism for the plugin, which allows to import parts of the "package dump" at runtime like normal Python namespace modules.

The modules in the "package dump" must meet the following requirements:
- No `__init__.py` files.
- No relative imports.
- The package structure has to start with `cms_rendner_sdfv` followed by a unique package name
  - In general, the name of the library should be used here. For pandas the unique package name is `pandas`.
- The unit tests must not be located in the directory to be dumped.

Changing code in this project may require adjustments to the following things:
- Adapt the plugin code writen in Kotlin which calls the code.
- Adapt all Python projects which implement these interfaces.
- Regenerate the code for the plugin.

## Link sdfv_base in other projects
To implement against the `sdfv_base` code, the project has to be linked.

See: [add sdfv_base as a content-root](https://www.jetbrains.com/help/pycharm/2021.3/configuring-project-structure.html#create-content-root)

Afterwards mark:
- The `src` folder from `sdfv_base` as a `Sources` folder

Note: Don't edit the linked files of the `sdfv_base` project from your project. 
Otherwise, it could happen that you accidentally use Python code which is not compatible with the Python version used in `sdfv_base`.

