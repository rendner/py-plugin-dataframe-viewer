import numpy as np
import pandas as pd
import pytest

from tests.helpers.asserts.assert_patched_styler import assert_patched_styler
from tests.helpers.custom_styler_functions import highlight_even_numbers

df = pd.DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


@pytest.mark.parametrize("subset", [None, pd.IndexSlice[2:3, ["col_2", "col_3"]]])
@pytest.mark.parametrize("kwargs", [{}, {'color': 'pink'}])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        df.shape  # single chunk
    ])
def test_chunked(subset, kwargs, rows_per_chunk, cols_per_chunk):
    assert_patched_styler(
        df,
        lambda styler: styler.applymap(highlight_even_numbers, subset=subset, **kwargs),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize("subset", [
    2,  # reduce to row
    "col_2",  # reduce to column
    (2, "col_2"),  # reduce to scalar
])
def test_frame_can_handle_reducing_subset(subset):
    assert_patched_styler(
        df,
        lambda styler: styler.applymap(highlight_even_numbers, subset=subset),
        2,
        2
    )


def test_forwards_kwargs():
    def my_styling_func(data, **kwargs):
        attr = f'background-color: {kwargs.get("color")}'
        return np.where(data % 2 == 0, attr, None)

    assert_patched_styler(
        df,
        lambda styler: styler.applymap(my_styling_func, color="pink"),
        2,
        2
    )
