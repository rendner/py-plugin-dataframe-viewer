# Generate Test Data Automated
The data to be created will be extracted by using a dockered Python Interpreter.

If docker isn't installed, please read [GENERATE_TEST_DATA_MANUALLY.md](GENERATE_TEST_DATA_MANUALLY.md).

Most unit-tests are executed against prefetched HTML files.  
Otherwise, the test time would be much longer if the required data has to be fetched from a Python interpreter each time.

## Requirements
All listed requirements have to be fulfilled.
### Docker
Docker has to be installed and `docker` commands can be run directly from a terminal/shell.

See: https://docs.docker.com/engine/install/linux-postinstall/#manage-docker-as-a-non-root-user

The gradle-task `buildPythonDockerImage` (group `docker`) must have been executed at least once.

### JDK
A [JBR 11 with JCEF](https://confluence.jetbrains.com/pages/viewpage.action?pageId=221478946) has to be installed and one of the following environment variable has to be set:
- `JAVA_HOME` which points to the folder where the JBR 11 with JCEF was installed
- or `JCEF_11_JDK` which points to the folder where the JBR 11 with JCEF was installed

>Note: On Linux you can store the environment variable in `~/.profile`.

## Rebuild Docker Image
If files from the projects under `<PROJECTS_DIR>/html_from_styler` have been modified, the Docker image must be recreated.

Run the gradle-task `buildPythonDockerImage` (group `docker`).

## When To Re-Generate
Whenever plugin related code has changed, which could affect one of the following parts:

- injecting the Python plugin code (Kotlin)
- pandas related plugin code (Python)
- the structure of the returned html string (Python)

All files of the affected pandas versions, supported by the plugin, have to be re-generated.

## Generate Test Data
The test data will be automatically created by running the corresponding gradle-task:
- pandas 1.3 was modified -> run gradle-task `generateTestData_pandas_1.3` (group `generate`)
- multiple ones were modified -> run gradle-task `regenerateTestData_all` (group `generate`)

The generated files are created under `src/test/resources/generated/<pandas_x.y>/...`.

## Verify Changes
Check if all changes are as expected. Not all changes may have been intentional.
If everything is OK, commit the changes.