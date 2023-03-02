#  Copyright 2023 cms.rendner (Daniel Schmidt)
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
import pandas as pd
from semantic_version import SimpleSpec, Version


def get_pandas_sem_version():
    version_string = pd.__version__
    try:
        return Version(version_string)
    except ValueError:
        # handle invalid SemVer like '1.4.0.dev0+1574.g46ddb8ef88'
        parts = version_string.split(".")
        mmp = ".".join(parts[0:3])
        pre_release = ".".join(parts[3:])
        return Version(f'{mmp}-{pre_release}')


pandas_sem_version = get_pandas_sem_version()


def not_required_pandas_version(semver: str):
    spec = SimpleSpec(semver)
    return not spec.match(pandas_sem_version)
