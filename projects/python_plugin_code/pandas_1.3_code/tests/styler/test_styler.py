from typing import Callable

import pandas as pd
import numpy as np
import pytest
from pandas.io.formats.style import Styler

df_non_unique_cols = pd.DataFrame(
    data=np.arange(1, 10).reshape(3, 3),
    columns=["a", "b", "a"]
)
df_non_unique_idx = pd.DataFrame(
    data=np.arange(1, 10).reshape(3, 3),
    columns=["a", "b", "c"],
    index=["x", "y", "x"]
)
df_non_unique_cols_idx = pd.DataFrame(
    data=np.arange(1, 10).reshape(3, 3),
    columns=["a", "b", "a"],
    index=["x", "y", "x"]
)

'''
These tests are vital. If they fail, the patchers can no longer work.
'''


def ids_for_styler(styler_func: Callable[..., Styler]) -> str:
    styler = styler_func(lambda x: x)
    return f"func: {styler_func.__name__}, cols: {str(styler.data.columns.tolist())}, idx: {str(styler.data.index.tolist())}"


# behavior has slightly changed in 1.3: https://github.com/pandas-dev/pandas/pull/41269
@pytest.mark.parametrize("styler_func", [
    df_non_unique_cols.style.apply,
    df_non_unique_cols.style.applymap,
    df_non_unique_idx.style.apply,
    df_non_unique_idx.style.applymap,
    df_non_unique_cols_idx.style.apply,
    df_non_unique_cols_idx.style.applymap
], ids=ids_for_styler)
def test_raise_non_unique_key_error(styler_func: Callable[..., Styler]):
    msg = "`Styler.apply` and `.applymap` are not compatible with non-unique index or columns."
    with pytest.raises(KeyError, match=msg):
        styler_func(lambda x: x).render()
