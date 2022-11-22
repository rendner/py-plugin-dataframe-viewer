# https://hub.docker.com/_/python?tab=description
FROM python:3.7.12-slim-buster

ENV PIPENV_VENV_IN_PROJECT=1

WORKDIR /usr/src/app

RUN pip install pipenv

COPY content/pipenv_environments/ pipenv_environments/

# build all pipenv environments
RUN \
    for i in ./pipenv_environments/* \
    ;do \
        cd "$i" \
        && pipenv sync \
        # link python_helpers from WORKDIR
        && echo PYTHONPATH=/usr/src/app/python_helpers/ > .env \
        && cd - \
    ;done

COPY content/merge_into_pipenv_environments/ pipenv_environments/
COPY content/python_helpers/ python_helpers/