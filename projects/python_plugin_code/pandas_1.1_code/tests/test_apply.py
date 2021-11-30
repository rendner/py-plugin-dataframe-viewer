import pandas as pd
import pytest

from tests.helpers.asserts.assert_styler import create_and_assert_patched_styler
from tests.helpers.custom_styler_functions import highlight_even_numbers, highlight_max_values

df = pd.DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


@pytest.mark.parametrize("axis", [None, 0, 1])
@pytest.mark.parametrize("subset", [None, pd.IndexSlice[2:3, ["col_2", "col_3"]]])
@pytest.mark.parametrize("kwargs", [{}, {'color': 'pink'}])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        (len(df.index), len(df.columns))  # single chunk
    ])
def test_chunked(axis, subset, kwargs, rows_per_chunk, cols_per_chunk):
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.apply(highlight_even_numbers, axis=axis, subset=subset, **kwargs),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize("axis", [None, 0, 1, "index", "columns"])
@pytest.mark.parametrize("subset", [None, pd.IndexSlice[2:3, ["col_2", "col_3"]]])
def test_can_handle_axis_values(axis, subset):
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.apply(highlight_even_numbers, axis=axis, subset=subset),
        2,
        2
    )


@pytest.mark.parametrize("subset", [
    2,  # reduce to row
    "col_2",  # reduce to column
    (2, "col_2"),  # reduce to scalar
])
def test_frame_can_handle_reducing_subset(subset):
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.apply(highlight_even_numbers, axis=None, subset=subset),
        2,
        2
    )


@pytest.mark.parametrize("axis", [None, 0, 1])
def test_chunk_parent_is_provided_for_function(axis):
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.apply(highlight_max_values, axis=axis),
        2,
        2
    )


@pytest.mark.parametrize("axis", [None, 0, 1])
def test_chunk_parent_is_provided_for_lambda(axis):
    highlighter = lambda d, chunk_parent=None: highlight_max_values(d, chunk_parent=chunk_parent)
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.apply(highlighter, axis=axis),
        2,
        2
    )
