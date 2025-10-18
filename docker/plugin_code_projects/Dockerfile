# Dockerfile for all Python plugin code projects
# This image contains all pandas and polars test environments
# Automatically generated - do not edit manually!
# Built using multi-stage approach for optimal caching


# Stage 1: Build pyenv and install Python versions
FROM debian:bullseye-20241202-slim AS pyenv-builder

# Set locale to avoid warnings
ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

ENV PYENV_ROOT="/opt/pyenv"
ENV PATH="$PYENV_ROOT/shims:$PYENV_ROOT/bin:$PATH"

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
RUN git clone --depth 1 https://github.com/pyenv/pyenv.git $PYENV_ROOT \
    && cd $PYENV_ROOT \
    && src/configure \
    && make -C src

# Install Python versions via pyenv
RUN \
    pyenv install 3.7 && \
    pyenv install 3.8 && \
    pyenv install 3.9

# Set global Python versions
RUN pyenv global 3.9 3.8 3.7

# Install pipenv for all Python versions
RUN \
    PYENV_VERSION=3.7 python -m pip install --root-user-action=ignore "pipenv<2023.10.20" && \
    PYENV_VERSION=3.8 python -m pip install --root-user-action=ignore "pipenv==2024.1.0" && \
    PYENV_VERSION=3.9 python -m pip install --root-user-action=ignore "pipenv==2024.1.0"

# Stage 2: Base runtime with pyenv (minimal dependencies)
FROM debian:bullseye-20241202-slim AS base

# Set locale to avoid warnings
ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

ENV PYENV_ROOT="/opt/pyenv"
ENV PATH="$PYENV_ROOT/shims:$PYENV_ROOT/bin:$PATH"

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
RUN mkdir -p projects venvs

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
    && rm -rf /var/lib/apt/lists/*

# Build stage for sdfv_base (base project - built first)
FROM builder AS sdfv_base

# Copy Pipfile and install dependencies
COPY projects/python_plugin_code/sdfv_base/Pipfile* projects/sdfv_base/
RUN cd projects/sdfv_base \
    && PYENV_VERSION=3.7 pipenv sync --dev

# Build stage for pandas_1.1
FROM builder AS pandas_1_1

# Copy Pipfile and install dependencies
COPY projects/python_plugin_code/pandas_1.1/Pipfile* projects/pandas_1.1/
RUN cd projects/pandas_1.1 \
    && PYENV_VERSION=3.7 pipenv sync --dev \
    && echo "/usr/src/app/projects/sdfv_base" > "$(pipenv run python -c 'import site; print(site.getsitepackages()[0])')/sdfv_base.pth"

# Build stage for pandas_1.2
FROM builder AS pandas_1_2

# Copy Pipfile and install dependencies
COPY projects/python_plugin_code/pandas_1.2/Pipfile* projects/pandas_1.2/
RUN cd projects/pandas_1.2 \
    && PYENV_VERSION=3.7 pipenv sync --dev \
    && echo "/usr/src/app/projects/sdfv_base" > "$(pipenv run python -c 'import site; print(site.getsitepackages()[0])')/sdfv_base.pth"

# Build stage for pandas_1.3
FROM builder AS pandas_1_3

# Copy Pipfile and install dependencies
COPY projects/python_plugin_code/pandas_1.3/Pipfile* projects/pandas_1.3/
RUN cd projects/pandas_1.3 \
    && PYENV_VERSION=3.7 pipenv sync --dev \
    && echo "/usr/src/app/projects/sdfv_base" > "$(pipenv run python -c 'import site; print(site.getsitepackages()[0])')/sdfv_base.pth"

# Build stage for pandas_1.4
FROM builder AS pandas_1_4

# Copy Pipfile and install dependencies
COPY projects/python_plugin_code/pandas_1.4/Pipfile* projects/pandas_1.4/
RUN cd projects/pandas_1.4 \
    && PYENV_VERSION=3.8 pipenv sync --dev \
    && echo "/usr/src/app/projects/sdfv_base" > "$(pipenv run python -c 'import site; print(site.getsitepackages()[0])')/sdfv_base.pth"

# Build stage for pandas_1.5
FROM builder AS pandas_1_5

# Copy Pipfile and install dependencies
COPY projects/python_plugin_code/pandas_1.5/Pipfile* projects/pandas_1.5/
RUN cd projects/pandas_1.5 \
    && PYENV_VERSION=3.8 pipenv sync --dev \
    && echo "/usr/src/app/projects/sdfv_base" > "$(pipenv run python -c 'import site; print(site.getsitepackages()[0])')/sdfv_base.pth"

# Build stage for pandas_2.0
FROM builder AS pandas_2_0

# Copy Pipfile and install dependencies
COPY projects/python_plugin_code/pandas_2.0/Pipfile* projects/pandas_2.0/
RUN cd projects/pandas_2.0 \
    && PYENV_VERSION=3.8 pipenv sync --dev \
    && echo "/usr/src/app/projects/sdfv_base" > "$(pipenv run python -c 'import site; print(site.getsitepackages()[0])')/sdfv_base.pth"

# Build stage for pandas_2.1
FROM builder AS pandas_2_1

# Copy Pipfile and install dependencies
COPY projects/python_plugin_code/pandas_2.1/Pipfile* projects/pandas_2.1/
RUN cd projects/pandas_2.1 \
    && PYENV_VERSION=3.9 pipenv sync --dev \
    && echo "/usr/src/app/projects/sdfv_base" > "$(pipenv run python -c 'import site; print(site.getsitepackages()[0])')/sdfv_base.pth"

# Build stage for pandas_2.2
FROM builder AS pandas_2_2

# Copy Pipfile and install dependencies
COPY projects/python_plugin_code/pandas_2.2/Pipfile* projects/pandas_2.2/
RUN cd projects/pandas_2.2 \
    && PYENV_VERSION=3.9 pipenv sync --dev \
    && echo "/usr/src/app/projects/sdfv_base" > "$(pipenv run python -c 'import site; print(site.getsitepackages()[0])')/sdfv_base.pth"

# Build stage for pandas_2.3
FROM builder AS pandas_2_3

# Copy Pipfile and install dependencies
COPY projects/python_plugin_code/pandas_2.3/Pipfile* projects/pandas_2.3/
RUN cd projects/pandas_2.3 \
    && PYENV_VERSION=3.9 pipenv sync --dev \
    && echo "/usr/src/app/projects/sdfv_base" > "$(pipenv run python -c 'import site; print(site.getsitepackages()[0])')/sdfv_base.pth"

# Build stage for polars_x.y
FROM builder AS polars_x_y

# Copy Pipfile and install dependencies
COPY projects/python_plugin_code/polars_x.y/Pipfile* projects/polars_x.y/
RUN cd projects/polars_x.y \
    && PYENV_VERSION=3.8 pipenv sync --dev \
    && echo "/usr/src/app/projects/sdfv_base" > "$(pipenv run python -c 'import site; print(site.getsitepackages()[0])')/sdfv_base.pth"

# Final stage - assemble all projects
FROM base AS final

# Create app user with specified UID and GID
ARG USER_UID
ARG USER_GID

# Validate build arguments and create appuser
# If the GID already exists, use the existing group; otherwise create a new one
RUN test -n "$USER_UID" || (echo "ERROR: USER_UID build argument is required but not set" && exit 1) && \
    test -n "$USER_GID" || (echo "ERROR: USER_GID build argument is required but not set" && exit 1) && \
    (getent group $USER_GID || addgroup --gid $USER_GID appuser) && \
    adduser --uid $USER_UID --gid $USER_GID --no-create-home --disabled-password --gecos "" appuser

WORKDIR /usr/src/app

# Copy virtual environments from their respective build stages with numeric UID:GID ownership
# Each stage contains one venv directory, we copy the entire venvs folder to accumulate all venvs
# The .pth files are already created in each project's build stage
COPY --chown=$USER_UID:$USER_GID --from=sdfv_base /usr/src/app/venvs/ venvs/
COPY --chown=$USER_UID:$USER_GID --from=pandas_1_1 /usr/src/app/venvs/ venvs/
COPY --chown=$USER_UID:$USER_GID --from=pandas_1_2 /usr/src/app/venvs/ venvs/
COPY --chown=$USER_UID:$USER_GID --from=pandas_1_3 /usr/src/app/venvs/ venvs/
COPY --chown=$USER_UID:$USER_GID --from=pandas_1_4 /usr/src/app/venvs/ venvs/
COPY --chown=$USER_UID:$USER_GID --from=pandas_1_5 /usr/src/app/venvs/ venvs/
COPY --chown=$USER_UID:$USER_GID --from=pandas_2_0 /usr/src/app/venvs/ venvs/
COPY --chown=$USER_UID:$USER_GID --from=pandas_2_1 /usr/src/app/venvs/ venvs/
COPY --chown=$USER_UID:$USER_GID --from=pandas_2_2 /usr/src/app/venvs/ venvs/
COPY --chown=$USER_UID:$USER_GID --from=pandas_2_3 /usr/src/app/venvs/ venvs/
COPY --chown=$USER_UID:$USER_GID --from=polars_x_y /usr/src/app/venvs/ venvs/

# Copy container scripts with numeric UID:GID ownership
COPY --chown=$USER_UID:$USER_GID docker/plugin_code_projects/container_scripts/ scripts/

# Make all scripts executable
RUN chmod +x scripts/*

# Switch to appuser using numeric UID:GID
USER $USER_UID:$USER_GID

# Set environment variables
# Note: The following directories are mounted at runtime and don't need to be created in the image:
# - /usr/src/app/transfer/in (mounted read-only)
# - /usr/src/app/transfer/out (mounted read-write)
# - /usr/src/app/projects (mounted with project source code)
ENV \
    PYTHONPATH=/usr/src/app/transfer/in/python_helpers/ \
    TRANSFER_OUT_DIR=/usr/src/app/transfer/out

CMD ["/bin/bash"]