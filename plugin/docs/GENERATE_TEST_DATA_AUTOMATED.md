# Generate Test Data Automated
Requires docker to be installed, to extract the data from a dockered Python Interpreter.

If docker isn't installed on your os, please read [GENERATE_TEST_DATA_MANUALLY.md](GENERATE_TEST_DATA_MANUALLY.md).

Most unit-tests are executed against prefetched JSON files.  
Otherwise, the test time would be much longer if the required data has to be fetched from a Python interpreter each time.

## Requirements
All listed requirements have to be fulfilled.
### Docker
Docker has to be installed and `docker` commands can be run directly from a terminal/shell.

See: https://docs.docker.com/engine/install/linux-postinstall/#manage-docker-as-a-non-root-user

The gradle-task `buildPythonDockerImages` (group `docker`) must have been executed at least once.

## Rebuild Docker Image
If files from the projects under `<PROJECTS_DIR>/html_from_styler` have been modified, the corresponding Docker images that contains the modified projects needs to be rebuild.

Run the gradle-task `buildPythonDockerImages` or the corresponding `buildPythonDockerImage_X`-tasks (group `docker`).

## When To Re-Generate
Whenever plugin related code has changed, which could affect one of the following parts:

- the structure of the returned JSON string (Python)

All files of the affected pandas versions, supported by the plugin, have to be re-generated.

## Generate Test Data
The test data will be automatically created by running the corresponding gradle-task:
- pandas 1.3 was modified -> run gradle-task `generateTestData_pandas_1.3` (group `generate`)
- multiple ones were modified -> run gradle-task `regenerateTestData_all` (group `generate`)

The generated files are created under `src/test/resources/generated/<pandas_x.y>/...`.

## Verify Changes
Check if all changes are as expected by inspecting the local changes in the IntelliJ `Commit´ tool window. Not all changes may have been intentional.
If everything is OK, commit the changes.