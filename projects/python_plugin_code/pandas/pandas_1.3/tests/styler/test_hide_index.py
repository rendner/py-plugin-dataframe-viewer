import pandas as pd
import pytest

from tests.helpers.asserts.assert_patched_styler import assert_patched_styler

df = pd.DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


@pytest.mark.parametrize("subset", [None, df.index.tolist(), pd.IndexSlice[3:4]])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        df.shape  # single chunk
    ])
def test_with_subsets(subset, rows_per_chunk, cols_per_chunk):
    assert_patched_styler(
        df,
        # the max value (located in "col_4") is hidden if subset is != None
        # so the chunked result should also not include the highlighted value
        lambda styler: styler.hide_index(subset),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        df.shape  # single chunk
    ])
def test_chunked(rows_per_chunk, cols_per_chunk):
    assert_patched_styler(
        df,
        lambda styler: styler.hide_index(),
        rows_per_chunk,
        cols_per_chunk
    )
