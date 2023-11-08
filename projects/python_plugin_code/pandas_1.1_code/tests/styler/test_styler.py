import pandas as pd
import numpy as np
import pytest
from pandas import DataFrame

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


@pytest.mark.parametrize("my_df", [
    df_non_unique_cols,
    df_non_unique_idx,
    df_non_unique_cols_idx,
])
def test_raise_non_unique_key_error(my_df: DataFrame):
    msg = "style is not supported for non-unique indices."
    with pytest.raises(ValueError, match=msg):
        # noinspection PyStatementEffect
        my_df.style
