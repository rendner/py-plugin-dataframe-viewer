import pandas as pd
from semantic_version import SimpleSpec, Version

pandas_sem_version = Version(pd.__version__)


def not_required_pandas_version(semver: str):
    spec = SimpleSpec(semver)
    return not spec.match(pandas_sem_version)
