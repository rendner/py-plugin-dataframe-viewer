import pandas as pd
import pytest

from tests.helpers.asserts.assert_styler import create_and_assert_patched_styler

df = pd.DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


@pytest.mark.parametrize("subset", [df.columns.tolist(), ["col_1", "col_4"]])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        (len(df.index), len(df.columns))  # single chunk
    ])
def test_with_subsets(subset, rows_per_chunk, cols_per_chunk):
    create_and_assert_patched_styler(
        df,
        # the max value is in hidden "col_4"
        # so the chunked table should also not include the highlighted value
        lambda styler: styler.highlight_max(axis=None).hide_columns(subset),
        rows_per_chunk,
        cols_per_chunk
    )
