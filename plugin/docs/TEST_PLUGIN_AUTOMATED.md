# Test Plugin Automated
Most parts of the plugin can be tested automatically, if docker is installed on your operating system (OS).

If docker isn't installed, please read [TEST_PLUGIN_MANUALLY.md](TEST_PLUGIN_MANUALLY.md).

## Requirements
All listed requirements have to be fulfilled.
### Docker
Docker has to be installed and `docker` commands can be run directly from a terminal/shell.

See: https://docs.docker.com/engine/install/linux-postinstall/#manage-docker-as-a-non-root-user

## Tests After Code Modification

### 1) Run Unit-Tests
Run the gradle-task `test` (group `verification`).

### 2) Run Integration-Tests
#### Plugin Code Is UpToDate
Please double-check that, for every modified project under `<PROJECTS_DIR>/python_plugin_code` the `main.py` was executed to generate an updated `generated/plugin_code` file for these projects.

#### Rebuild Docker Image
Required if the `pipfiles` of these projects have been modified.

Run the gradle-task `buildPythonDockerImages` (group `docker`).

#### Run Tests
Run gradle-task `integrationTest_all` (group `verification`).