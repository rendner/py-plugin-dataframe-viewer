/*
 * Copyright 2021-2025 cms.rendner (Daniel Schmidt)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package images.plugin_code_projects

import PipEnvProject
import java.io.File

/**
 * Note:
 * The mounted projects should not have their .venv folders inside the projects.
 * The installed dependencies may not compatible with the os of the docker container.
 * Pipenv checks for existing .venv folders inside projects first.
 *
 * Script to generate the Dockerfile from ProjectsConfig.
 * This ensures consistency between project configuration and Docker build process.
 *
 * BUILD-TIME UID/GID CONFIGURATION:
 * =================================
 *
 * The Docker image requires USER_UID and USER_GID to be passed as build arguments.
 * These must match the host user's UID/GID to avoid permission issues with mounted volumes.
 *
 * Example build command:
 *   docker build \
 *     --build-arg USER_UID=$(id -u) \
 *     --build-arg USER_GID=$(id -g) \
 *     -t myimage .
 *
 * The build will fail if USER_UID or USER_GID are not provided or are empty.
 *
 * MULTI-STAGE BUILD APPROACH:
 * ============================
 *
 * This script generates a Dockerfile using Docker's multi-stage build feature, where each
 * project is built in its own isolated stage. This approach provides significant benefits
 * for build performance and caching efficiency.
 *
 * WHY MULTI-STAGE BUILDS?
 * ------------------------
 *
 * 1. **Optimal Layer Caching**:
 *    - Each project stage is independent - changing pandas_2.0 source code only rebuilds
 *      the pandas_2.0 stage and the final assembly stage
 *    - Without multi-stage: changing pandas_2.0 would invalidate ALL subsequent projects
 *      due to Docker's sequential layer invalidation
 *
 * 2. **Parallel Execution**:
 *    - With Docker BuildKit (DOCKER_BUILDKIT=1), independent stages can build in parallel
 *    - All projects can install dependencies simultaneously, dramatically reducing
 *      initial build time
 *
 * 3. **True Isolation**:
 *    - Each project's dependencies and source files are in a separate stage
 *    - No risk of one project's changes affecting another project's cached layers
 *    - Easy to debug individual projects: `docker build --target pandas_2.0`
 *
 * STRUCTURE:
 * ----------
 *
 * The generated Dockerfile has these main components:
 *
 * 1. PYENV-BUILDER STAGE (AS pyenv-builder):
 *    - Builds pyenv from source and compiles Python versions
 *    - Contains build tools needed only for this stage
 *    - Expensive operations that rarely change
 *    - Output: /opt/pyenv with all Python installations
 *
 * 2. BASE STAGE (AS base):
 *    - Minimal runtime dependencies only (ca-certificates, libopenblas0, libgomp1)
 *    - Copies pyenv and Python installations from pyenv-builder stage
 *    - No build tools - keeps the base image lean
 *    - All subsequent stages inherit from this
 *
 * 3. BUILDER STAGE (AS builder):
 *    - Inherits from base stage
 *    - Adds compilation tools needed for pipenv sync (gcc, g++, gfortran, etc.)
 *    - Used by all project stages to compile Python packages (numpy, pandas, polars, etc.)
 *
 * 4. BASE PROJECT STAGE (AS sdfv_base):
 *    - Contains the shared base project dependencies
 *    - All other projects depend on this via .pth files
 *
 * 5. PROJECT STAGES (AS project_name):
 *    - One stage per project (pandas_1.1, pandas_1.2, etc.)
 *    - Each stage: COPY Pipfile → RUN pipenv sync → builds .venv
 *    - Completely independent - can be built in parallel
 *
 * 6. FINAL STAGE (AS final):
 *    - Inherits from base (no build tools, minimal size)
 *    - Requires USER_UID and USER_GID build arguments
 *    - Creates appuser with the provided UID/GID
 *    - Assembles all projects using COPY --from=<stage> for .venv directories only
 *    - Sets up sdfv_base path dependencies via .pth files
 *    - Sets ownership to appuser
 *    - This is the stage that gets tagged as the final image
 */
object DockerfileGenerator {

    private const val BASE_IMAGE = "debian:bullseye-20241202-slim"

    private fun toStageName(name: String): String {
        // Use underscores in stage name (Docker doesn't allow dots)
        return name.replace('.', '_')
    }

    private fun List<String>.joinWithIndentedContinuation(indent: String = "    "): String {
        return joinToString(" && \\\n") { "$indent$it" }
    }

    /**
     * Generate the pyenv builder stage that compiles pyenv and installs Python versions.
     */
    private fun generatePyenvBuilderStage(image: PluginCodeProjectsImage): String {
        val pythonVersions = mutableSetOf<String>().apply {
            add(image.baseProject.pythonVersion)
            image.projects.forEach { add(it.pythonVersion) }
        }.sorted()
        val pyenvInstalls = pythonVersions.map { "pyenv install $it" }
        val pipenvInstalls = pythonVersions.map { version ->
            val pipEnvVersion = if (version == "3.7") "pipenv<2023.10.20" else "pipenv==2024.1.0"
            """PYENV_VERSION=$version python -m pip install --root-user-action=ignore "$pipEnvVersion""""
        }

        return """
# Stage 1: Build pyenv and install Python versions
FROM $BASE_IMAGE AS pyenv-builder

# Set locale to avoid warnings
ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

ENV PYENV_ROOT="/opt/pyenv"
ENV PATH="${'$'}PYENV_ROOT/shims:${'$'}PYENV_ROOT/bin:${'$'}PATH"

# Install build dependencies needed for pyenv and Python compilation
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    git \
    curl \
    gcc \
    g++ \
    build-essential \
    libssl-dev \
    zlib1g-dev \
    libbz2-dev \
    libreadline-dev \
    libsqlite3-dev \
    libncursesw5-dev \
    xz-utils \
    tk-dev \
    libxml2-dev \
    libxmlsec1-dev \
    libffi-dev \
    liblzma-dev \
    && rm -rf /var/lib/apt/lists/*

# Clone and build pyenv
RUN git clone --depth 1 https://github.com/pyenv/pyenv.git ${'$'}PYENV_ROOT \
    && cd ${'$'}PYENV_ROOT \
    && src/configure \
    && make -C src

# Install Python versions via pyenv
RUN \
${pyenvInstalls.joinWithIndentedContinuation()}

# Set global Python versions
RUN pyenv global ${pythonVersions.sortedDescending().joinToString(" ")}

# Install pipenv for all Python versions
RUN \
${pipenvInstalls.joinWithIndentedContinuation()}"""
    }

    /**
     * Generate the base stage with runtime dependencies and pyenv copied from builder.
     */
    private fun generateBaseStage(): String {
        return """
# Stage 2: Base runtime with pyenv (minimal dependencies)
FROM $BASE_IMAGE AS base

# Set locale to avoid warnings
ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

ENV PYENV_ROOT="/opt/pyenv"
ENV PATH="${'$'}PYENV_ROOT/shims:${'$'}PYENV_ROOT/bin:${'$'}PATH"

# Install only runtime dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    libopenblas0 \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# Copy pyenv and all Python installations from the builder stage
COPY --from=pyenv-builder /opt/pyenv /opt/pyenv

# Set pipenv configuration
ENV \
    PIPENV_QUIET=1 \
    WORKON_HOME=/usr/src/app/venvs

WORKDIR /usr/src/app

# Create projects directory structure for all stages
RUN mkdir -p projects venvs"""
    }

    /**
     * Generate the builder stage with compilation tools for pipenv sync.
     */
    private fun generateBuilderStage(): String {
        return """
# Stage 3: Builder with compilation tools (for pipenv sync --dev)
FROM base AS builder

# Install build dependencies needed for compiling Python packages (numpy, pandas, etc.)
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    g++ \
    gfortran \
    build-essential \
    libopenblas-dev \
    liblapack-dev \
    libssl-dev \
    zlib1g-dev \
    libbz2-dev \
    libreadline-dev \
    libsqlite3-dev \
    libncursesw5-dev \
    xz-utils \
    tk-dev \
    libxml2-dev \
    libxmlsec1-dev \
    libffi-dev \
    liblzma-dev \
    && rm -rf /var/lib/apt/lists/*"""
    }

    /**
     * Generate a build stage for the base project (sdfv_base).
     * This stage is built first and contains shared dependencies that other projects depend on.
     */
    private fun generateBaseProjectStage(baseProject: PipEnvProject): String {
        return """
# Build stage for ${baseProject.name} (base project - built first)
FROM builder AS ${toStageName(baseProject.name)}

# Copy Pipfile and install dependencies
COPY projects/python_plugin_code/${baseProject.name}/Pipfile* projects/${baseProject.name}/
RUN cd projects/${baseProject.name} \
    && PYENV_VERSION=${baseProject.pythonVersion} pipenv sync --dev"""
    }

    /**
     * Generate a build stage for a single project.
     */
    private fun generateProjectStage(project: PipEnvProject, baseProjectName: String): String {
        return """
# Build stage for ${project.name}
FROM builder AS ${toStageName(project.name)}

# Copy Pipfile and install dependencies
COPY projects/python_plugin_code/${project.name}/Pipfile* projects/${project.name}/
RUN cd projects/${project.name} \
    && PYENV_VERSION=${project.pythonVersion} pipenv sync --dev \
    && echo "/usr/src/app/projects/$baseProjectName" > "$(pipenv run python -c 'import site; print(site.getsitepackages()[0])')/$baseProjectName.pth""""
    }

    /**
     * Generate the final stage that assembles all projects with volume mounting support.
     */
    private fun generateFinalStage(
        baseProject: PipEnvProject,
        implementingProjects: List<PipEnvProject>
    ): String {
        return """
# Final stage - assemble all projects
FROM base AS final

# Create app user with specified UID and GID
ARG USER_UID
ARG USER_GID

# Validate build arguments and create appuser
# If the GID already exists, use the existing group; otherwise create a new one
RUN test -n "${'$'}USER_UID" || (echo "ERROR: USER_UID build argument is required but not set" && exit 1) && \
    test -n "${'$'}USER_GID" || (echo "ERROR: USER_GID build argument is required but not set" && exit 1) && \
    (getent group ${'$'}USER_GID || addgroup --gid ${'$'}USER_GID appuser) && \
    adduser --uid ${'$'}USER_UID --gid ${'$'}USER_GID --no-create-home --disabled-password --gecos "" appuser

WORKDIR /usr/src/app

# Copy virtual environments from their respective build stages with numeric UID:GID ownership
# Each stage contains one venv directory, we copy the entire venvs folder to accumulate all venvs
# The .pth files are already created in each project's build stage
${
(listOf(baseProject) + implementingProjects).joinToString("\n") { project ->
    // Copy with numeric UID:GID ownership using build args
    "COPY --chown=\$USER_UID:\$USER_GID --from=${toStageName(project.name)} /usr/src/app/venvs/ venvs/"
}
}

# Copy container scripts with numeric UID:GID ownership
COPY --chown=${'$'}USER_UID:${'$'}USER_GID docker/plugin_code_projects/container_scripts/ scripts/

# Make all scripts executable
RUN chmod +x scripts/*

# Switch to appuser using numeric UID:GID
USER ${'$'}USER_UID:${'$'}USER_GID

# Set environment variables
# Note: The following directories are mounted at runtime and don't need to be created in the image:
# - /usr/src/app/transfer/in (mounted read-only)
# - /usr/src/app/transfer/out (mounted read-write)
# - /usr/src/app/projects (mounted with project source code)
ENV \
    PYTHONPATH=/usr/src/app/transfer/in/python_helpers/ \
    TRANSFER_OUT_DIR=/usr/src/app/transfer/out

CMD ["/bin/bash"]"""
    }

    /**
     * Generate the complete multi-stage Dockerfile content.
     */
    fun generateDockerfile(image: PluginCodeProjectsImage): String {
        return """# Dockerfile for all Python plugin code projects
# This image contains all pandas and polars test environments
# Automatically generated - do not edit manually!
# Built using multi-stage approach for optimal caching

${generatePyenvBuilderStage(image)}
${generateBaseStage()}
${generateBuilderStage()}
${generateBaseProjectStage(image.baseProject)}
${image.projects.joinToString("\n") { generateProjectStage(it, image.baseProject.name) }}
${generateFinalStage(image.baseProject, image.projects)}"""
    }

    /**
     * Generate and write the Dockerfile to the specified output file.
     */
    fun writeDockerfile(image: PluginCodeProjectsImage, outputFile: File) {
        println("Generating Dockerfile: ${outputFile.absolutePath}")

        println("Base project: ${image.baseProject.name} (Python ${image.baseProject.pythonVersion})")

        println("Found ${image.projects.size} projects")
        image.projects.forEach { project ->
            println("  - ${project.name} (Python ${project.pythonVersion})")
        }
        println()

        val dockerfileContent = generateDockerfile(image)
        outputFile.writeText(dockerfileContent)

        println("✓ Dockerfile generated successfully!")
        println()
        println("Note: Review the generated Dockerfile before building.")
    }
}