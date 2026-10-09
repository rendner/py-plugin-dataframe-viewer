# https://hub.docker.com/_/python
FROM python:3.15-rc-slim-bookworm

ARG PROJECT

RUN pip install pipenv==2026.8.0

WORKDIR /usr/src/app

# create a non-root user
RUN \
    adduser --uid 1000 --no-create-home --disabled-password --gecos "" appuser \
    # add file permission
    && chown -R appuser /usr/src/app

# switch to it
USER appuser

COPY --chown=appuser requirements.txt requirements.txt

ENV PIPENV_VENV_IN_PROJECT=1

# create pipenv environment
RUN \
    mkdir -p ./projects/$PROJECT \
    && cd ./projects/$PROJECT \
    && pipenv --python 3.15 \
    && pipenv install -r /usr/src/app/requirements.txt --verbose

ENV \
    # https://github.com/pypa/pipenv/issues/4027
    PIPENV_QUIET=1 \
    # a volume will be mounted on container start to provide the required content
    PYTHONPATH=/usr/src/app/transfer/in/python_helpers/ \
    TRANSFER_OUT_DIR=/usr/src/app/transfer/out
