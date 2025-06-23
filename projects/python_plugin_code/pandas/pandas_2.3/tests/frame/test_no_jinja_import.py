import pandas as pd
import pytest


def test_blocked_jinja2_import(request):
    if request.config.getoption("--no-jinja"):
        msg = "The '.style' accessor requires jinja2"
        with pytest.raises(AttributeError, match=msg):
            # noinspection PyStatementEffect
            pd.DataFrame().style
