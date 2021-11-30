import numpy as np
import pandas as pd

df = pd.DataFrame({"a": [pd.NA, 1, None], "b": [np.nan, 1, -1]})

test_case = {
    "styler": df.style.format(na_rep='').highlight_min(),
    "chunk_size": (1, 2),
    # in pandas < 1.3.2 there was a bug: https://github.com/pandas-dev/pandas/pull/42650
    # which raised an error when calling "df.style.highlight_min().render()"
    "pandas_version": ">=1.3.2"
}
