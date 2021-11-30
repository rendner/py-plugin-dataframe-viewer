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

classes = pd.DataFrame([
    ["min-val red", "", "blue", "pink", "yellow"],
    ["red", None, "blue max-val", None, None],
    [None, None, None, None, None],
    [None, None, None, None, None],
    ["min-val red", "", "blue", "pink", "yellow"]
], index=df.index, columns=df.columns)


@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        (len(df.index), len(df.columns))  # single chunk
    ])
def test_set_td_classes_chunked(rows_per_chunk, cols_per_chunk):
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.set_td_classes(classes),
        rows_per_chunk,
        cols_per_chunk
    )
