# Test Plugin Automated
Most parts of the plugin can be tested automatically, if docker is installed on your operating system (OS).

If docker isn't installed, please read [TEST_PLUGIN_MANUALLY.md](TEST_PLUGIN_MANUALLY.md).

## Requirements
All listed requirements have to be fulfilled.
### Docker
Docker has to be installed and `docker` commands can be run directly from a terminal/shell.

See: https://docs.docker.com/engine/install/linux-postinstall/#manage-docker-as-a-non-root-user

## Test After Code Modification
The mentioned steps should be done in the listed order, because they depend on each other.

### 1) Plugin Code Is UpToDate
Please double-check that, for every modified project under `<PROJECTS_DIR>/python_plugin_code` the `main.py` was executed to generate an updated `generated/plugin_code` file for these projects.

### 2) Rebuild Docker Image
Run the gradle-task `buildPythonDockerImages` (group `docker`).

### 3) Run Integration Tests
Run gradle-task `integrationTest_all` (group `verification`).

### 4) Run Generate Test Data
Run gradle-task `generateTestData_all` (group `generate`).

This task deletes all existing test data from `src/test/resources/generated`, and re-creates the deleted files afterward.

### 5) Run Unit-Tests
Run the gradle-task `test` (group `verification`).

The generated resources are used by the unit-tests `ChunkValidationTest`.

#### ChunkValidationTest
Loads for all supported pandas versions the generated data from the `src/test/resources/generated/` folder.

ensures:
- combined chunks result in the same visual output as an un-chunked version of the content
   - correctness of computed CSS values can't be verified by this test
- parser can parse the generated chunks

In case the combined chunks don't match with the expected result, a screenshot is taken of the rendered DataFrame tables.
The screenshots are created in a folder named by the failed test case under `src/test/resources/generated-error-images/chunk-validation`.