# https://hub.docker.com/_/python?tab=description
FROM python:3.9.17-slim-buster

ARG pipenv_environment

ENV PIPENV_VENV_IN_PROJECT=1

# https://github.com/pypa/pipenv/issues/4027
ENV PIPENV_QUIET=1

WORKDIR /usr/src/app

RUN pip install pipenv

COPY requirements.txt requirements.txt

# create pipenv environment
RUN \
    mkdir -p ./pipenv_environments/$pipenv_environment \
    && cd ./pipenv_environments/$pipenv_environment \
    && pipenv --python 3.9 \
    && pipenv install -r /usr/src/app/requirements.txt \
    # link python_helpers
    && echo PYTHONPATH=/usr/src/app/python_helpers/ > .env

COPY content/python_helpers/ python_helpers/