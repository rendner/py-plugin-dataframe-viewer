#  Copyright 2021-2023 cms.rendner (Daniel Schmidt)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
import sys
from importlib.abc import MetaPathFinder
from pathlib import Path

import pytest
from typing import List


def pytest_addoption(parser):
    # "simulates" a non-installed jinja2 (even it is installed)
    parser.addoption("--no-jinja", action="store_true", help="disable import of the optional dependency jinja2")


# https://docs.pytest.org/en/7.2.x/writing_plugins.html
# activate pytest asserts introspection for our asserts helpers
pytest.register_assert_rewrite('tests.helpers.asserts')


def pytest_make_parametrize_id(config, val, argname):
    return f'{argname}: {str(val)}'


class RaiseOnImport(MetaPathFinder):
    def __init__(self, packages: List[str]):
        self._packages = packages

    def find_spec(self, fullname, path, target=None):
        if fullname in self._packages:
            raise ImportError()


def pytest_ignore_collect(collection_path, path, config):
    if config.getoption("--no-jinja"):
        if collection_path.is_dir():
            test_dir = Path(config.rootdir, "tests")
            require_jinja = [str(Path.joinpath(test_dir, p).resolve()) for p in ["styler"]]
            return next(filter(lambda x: str(collection_path.resolve()).startswith(x), require_jinja), False)
    return False


def pytest_sessionstart(session):
    if session.config.getoption("--no-jinja"):
        # disable the import
        sys.meta_path.insert(0, RaiseOnImport(['jinja2', 'Jinja2']))
