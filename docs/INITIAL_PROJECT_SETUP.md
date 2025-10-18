## Setup Integration Tests
To run the plugin related Python code against different Python versions, the integration tests require Docker to be installed and proper configured.


### Prerequisites

#### Docker
Before you start, make sure you have Docker installed.
Running `docker -v` from the terminal should show the installed version.

If the command fails and Docker is installed, follow the instructions for your OS:

On macOS and Windows, make sure Docker Desktop is open.
On Linux, try `sudo systemctl start docker`.

You’ll also want to confirm that your user has access to the Docker socket.
On Linux, adding your user to the docker group usually solves it:

`sudo usermod -aG docker $USER`

Then log out and back in for it to take effect.

#### Build Docker Images
To build the Docker images, use the provided Gradle tasks (group `docker`).
Building all images upfront takes considerable time, so it's recommended to build only the latest version which uses the Python code you may have modified initially and run the corresponding integration tests.

##### Build the Latest Image Only (Recommended)


### Troubleshoot

#### All Tests Are Failing with ModuleNotFoundError
If you see errors like:

```
"*** ModuleNotFoundError: No module named 'debugger_helpers'"
```

Then you have to explicitly configure Docker file permissions in Docker Desktop for the transfer folder.
The folder is located at:
`<PATH_TO_GITHUB_REPO>/plugin/src/test-dockered/transfer`
