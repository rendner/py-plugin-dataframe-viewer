# Python Plugin Code

## Table of Contents

- [Overview](#overview)
  - [Architecture](#architecture)
  - [Individual Project Structure](#individual-project-structure)
- [Python Version Requirements](#python-version-requirements)
- [Setup Instructions](#setup-instructions)
  - [Prerequisites](#prerequisites)
  - [Step 1: Add pyenv Python Interpreters to PyCharm](#step-1-add-pyenv-python-interpreters-to-pycharm)
  - [Step 2: Install pipenv_2022](#step-2-install-pipenv_2022)
  - [Step 3: Open Projects in PyCharm](#step-3-open-projects-in-pycharm)
  - [Step 4: Configure a Project](#step-4-configure-a-project)
  - [Step 5: Link the sdfv_base Project](#step-5-link-the-sdfv_base-project-for-library-specific-projects-only)
  - [Step 6: Configure pytest](#step-6-configure-pytest-one-time-setup)
- [Workflow for Making Changes](#workflow-for-making-changes)

## Overview

This directory contains the Python code that runs inside the PyCharm debugger to extract and format data from DataFrames.
The plugin uses this code to efficiently retrieve data from DataFrames while you're debugging your application.

**Currently supported DataFrame libraries:**
- pandas
- polars

### Architecture

The project consists of multiple Python sub-projects that work together:

**`sdfv_base`** - The foundation project that defines the interfaces and core mechanisms:
- Contains abstract interfaces that define how to extract data from DataFrames
- Provides an import system that loads code at runtime from "package dumps" (JSON files)
- Written for Python 3.7 (the lowest supported version) to ensure maximum compatibility

**Library-specific projects** - Implementation projects:
- Each project implements the `sdfv_base` interfaces for a specific version of a DataFrame library
- Contains the actual logic to extract data, handle styling, filtering, and sorting
- Uses the minimum Python version supported by that library version (e.g., pandas 2.1 requires Python 3.9)
- Each project generates a "package dump" (JSON file) containing its implementation code
  - The modules in the "package dump" must meet the following requirements:
    - No `__init__.py` files.
    - No relative imports.
    - The top level package has to be `cms_rendner_sdfv`.
    - Inside the top level package should be a sub package named after the `DataFrame` library.
        - For pandas the name is `pandas`.
    - The unit tests must not be located in the directory to be dumped.

**How it works:**
1. When you debug a DataFrame in PyCharm, the plugin injects the appropriate "package dump" for your library version
2. The `sdfv_base` import mechanism loads the code from the JSON dump at runtime
3. The library-specific implementation extracts and formats the data
4. The plugin displays the formatted data

This architecture allows the plugin to support multiple DataFrame library versions without version conflicts, as each implementation is isolated in its own dump file.

### Individual Project Structure

Each project (both `sdfv_base` and library-specific projects) follows this structure:

```text
project_name/
├── .python-version          # Specifies required Python version
├── Pipfile                  # Dependencies managed by pipenv
├── generate_dump.py         # Generates the plugin_modules_dump.json
├── run_tests.py             # Runs pytest with proper configuration
├── src/
│   └── cms_rendner_sdfv/    # Top level package (contains implementation)
├── tests/                   # Unit tests (not included in dumps)
└── manual_testing/          # Example scripts for testing the plugin

```

**Key files:**
- **`generate_dump.py`**: Converts the code in `src/cms_rendner_sdfv/` into a JSON dump file used by the plugin
- **`run_tests.py`**: Convenience script to run all tests with pytest
- **`src/cms_rendner_sdfv/`**: Contains the actual implementation code (comments starting with `#` are automatically excluded from dumps)

> [!IMPORTANT]
> When you modify code in any project, you must run `generate_dump.py` to regenerate the JSON dump file.

## Python Version Requirements

Each project must use a specific Python version to ensure compatibility with the target DataFrame library version.
Using the correct Python version guarantees that the plugin code works with the minimum Python version supported by that library.

| project folder | required Python version | pipenv name  | 
|:---------------|:-----------------------:|--------------|
| sdfv_base      |           3.7           | pipenv_2022  |
| polars_x.y     |           3.8           | pipenv_2022  |
| pandas_1.1     |           3.7           | pipenv_2022  |
| pandas_1.2     |           3.7           | pipenv_2022  |
| pandas_1.3     |           3.7           | pipenv_2022  |
| pandas_1.4     |           3.8           | pipenv_2022  |
| pandas_1.5     |           3.8           | pipenv_2022  |
| pandas_2.0     |           3.8           | pipenv_2022  |
| pandas_2.1     |           3.9           | pipenv_2022  |
| pandas_2.2     |           3.9           | pipenv_2022  |
| pandas_2.3     |           3.9           | pipenv_2022  |

All projects use `pipenv_2022`, a specific older version of pipenv that is compatible with Python 3.7-3.9.
This ensures consistent dependency management across all projects.

> [!NOTE]
> For ARM-based Macs (Apple Silicon): 
> The projects `pandas_1.1`, `pandas_1.2`, and `pandas_1.3` cannot be installed because pre-compiled binaries for some of their dependencies are not available for this architecture.

## Setup Instructions

Follow these steps to set up your development environment for working on the Python plugin code.

### Prerequisites

You need Python versions 3.7, 3.8, and 3.9 available on your system. I recommend using **pyenv** to manage multiple Python versions:

```sh
# Install pyenv (example uses brew)
brew install pyenv

# Install required Python versions
pyenv install 3.7
pyenv install 3.8
pyenv install 3.9

# Verify installations
pyenv versions
```

For other installation methods, see the [pyenv installation guide](https://github.com/pyenv/pyenv#installation).

### Step 1: Add pyenv Python Interpreters to PyCharm

PyCharm doesn't automatically detect pyenv installations because they're in non-standard locations. You need to add them manually:

```sh
# Get the full paths to each Python version
pyenv prefix 3.7
pyenv prefix 3.8
pyenv prefix 3.9
```

Then add these interpreters to PyCharm: [Configure a system interpreter](https://www.jetbrains.com/help/pycharm/2023.3/configuring-python-interpreter.html#-roqa5o_152)

### Step 2: Install pipenv_2022

Install the specific version of pipenv used by all projects.

**Why pipenv_2022?**  
Newer versions of pipenv (2023+) [dropped support for Python 3.7](https://github.com/pypa/pipenv/releases/tag/v2023.10.20), which is still required by several projects in this repository.
By installing an older version with a custom suffix (`_2022`), you can use it alongside any newer pipenv installation you might have.

**Why pipx?**  
pipx installs Python applications in isolated environments, preventing conflicts with your system Python packages.
This ensures that `pipenv_2022` runs independently and doesn't interfere with other Python tools.

```sh
# Install pipx if not already installed
brew install pipx
pipx ensurepath

# Install pipenv version < 2023 with a custom suffix
pipx install --suffix="_2022" "pipenv<2023"

# Verify installation
pipx list | grep pipenv_2022

# Get the path to pipenv_2022 (you'll need this later)
which pipenv_2022
```

### Step 3: Open Projects in PyCharm

> [!IMPORTANT]
> Open these projects from a PyCharm instance launched by the plugin's `runIde` gradle task.
> This gives you:
>   - The latest version of the plugin automatically installed
>   - Ability to test your changes immediately using the manual testing scripts

To launch the development PyCharm instance:
1. Run the `runIde` gradle task
2. A new PyCharm window will open with the plugin pre-installed

### Step 4: Configure a Project

When you open one of the Python projects for the first time:

1. **Check the required Python version:**
   - Open the `.python-version` file in the project root, keep it open to have it visible in the background

2. **Configure the pipenv environment:**
   - Follow the PyCharm guide, but use the **correct** Base interpreter and Pipenv executable: [Configure pipenv for an existing Python project](https://www.jetbrains.com/help/pycharm/2023.3/pipenv.html#pipenv-existing-project)
     - **Base interpreter**: Select the Python version that matches the version in the opened `.python-version` file
     - **Pipenv executable**: Paste the path to `pipenv_2022` (from `which pipenv_2022`)

3. **Wait for dependency installation:**
   - PyCharm will automatically install the dependencies and create the virtual environment

### Step 5: Link the sdfv_base Project (for library-specific projects only)

Library-specific projects (pandas_x.x, polars_x.y) need to reference the `sdfv_base` interfaces. Link it as a content root:

1. Follow: [Add sdfv_base as a content-root](https://www.jetbrains.com/help/pycharm/2021.3/configuring-project-structure.html#create-content-root)
2. Mark the `src` folder from `sdfv_base` as a **Sources** folder

**Important:** Don't edit `sdfv_base` files from within a library-specific project. The `sdfv_base` project uses Python 3.7, and you might accidentally introduce incompatible syntax if editing from a project with a newer Python version.

### Step 6: Configure pytest (one-time setup)

All projects use pytest for testing. Enable it globally in PyCharm:

1. Open PyCharm **Settings/Preferences**
2. Navigate to **Tools** → **Python Integrated Tools**
3. Under **Testing**, select **pytest** as the default test runner

See: [Enable pytest in PyCharm](https://www.jetbrains.com/help/pycharm/pytest.html#enable-pytest)

## Workflow for Making Changes

After you've modified code in a project:

1. **Run tests:** Use `run_tests.py` or run tests directly from PyCharm
2. **Regenerate the dump:** Run `generate_dump.py` to update the JSON file
3. **Restart PyCharm:** Close PyCharm and re-run gradle task `runIde`
4. **Test in the plugin:** Use scripts in the `manual_testing/` folder to verify your changes work correctly in the plugin

If you change the `sdfv_base` interfaces:
- Update all library-specific projects that implement those interfaces
- Regenerate dumps for all affected projects
- Update the Kotlin plugin code if the interface changes affect how it calls the Python code
