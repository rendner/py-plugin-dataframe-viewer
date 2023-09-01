# https://hub.docker.com/_/python?tab=description
FROM python:3.9.17-slim-buster

RUN pip install pipenv

WORKDIR /usr/src/app

# create a non-root user
RUN \
    adduser --uid 1000 --no-create-home --disabled-password --gecos "" appuser \
    # add file permission
    && chown -R appuser /usr/src/app

# switch to it
USER appuser


ENV \
    PIPENV_VENV_IN_PROJECT=1 \
    # https://github.com/pypa/pipenv/issues/4027
    PIPENV_QUIET=1 \
    # a volume will be mounted on container start to provide the required content
    PYTHONPATH=/usr/src/app/transfer/in/python_helpers/ \
    TRANSFER_OUT_DIR=/usr/src/app/transfer/out

COPY --chown=appuser content/pipenv_environments/ pipenv_environments/

# build all pipenv environments
RUN \
    for i in ./pipenv_environments/* \
    ;do \
        cd "$i" \
        && pipenv sync \
        && cd - \
    ;done

COPY --chown=appuser content/merge_into_pipenv_environments/ pipenv_environments/