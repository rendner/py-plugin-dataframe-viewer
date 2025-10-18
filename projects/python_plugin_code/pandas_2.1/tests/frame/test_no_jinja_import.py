import pandas as pd
import pytest


def test_blocked_jinja2_import(request):
    if request.config.getoption("--no-jinja"):
        msg = "Missing optional dependency 'Jinja2'. DataFrame.style requires jinja2"
        with pytest.raises(ImportError, match=msg):
            # noinspection PyStatementEffect
            pd.DataFrame().style
