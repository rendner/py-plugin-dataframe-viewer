# https://hub.docker.com/_/python?tab=description
FROM python:3.7.12-slim-buster

WORKDIR /usr/src/app

RUN pip install pipenv

COPY pandas_1.1/ pandas_1.1/
RUN cd pandas_1.1 && pipenv sync

COPY pandas_1.2/ pandas_1.2/
RUN cd pandas_1.2 && pipenv sync

COPY pandas_1.3/ pandas_1.3/
RUN cd pandas_1.3 && pipenv sync

COPY enter_debugger_example.py .