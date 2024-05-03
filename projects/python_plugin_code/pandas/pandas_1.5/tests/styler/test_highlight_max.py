import pandas as pd
import numpy as np
import pytest

from cms_rendner_sdfv.base.types import TableFrameCell
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext
from tests.helpers.asserts.assert_style_func_parameters import assert_style_func_parameters
from tests.helpers.asserts.assert_patched_styler import assert_patched_styler

df = pd.DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


def test_expected_cell_styling():
    my_df = pd.DataFrame.from_dict({
        0: [0, 1, 2],
        1: [3, 4, 5],
    })

    ctx = PatchedStylerContext(my_df.style.highlight_max())
    actual = ctx.get_table_frame_generator().generate()

    assert actual.cells == [
        [
            TableFrameCell(value='0'),
            TableFrameCell(value='3'),
        ],
        [
            TableFrameCell(value='1'),
            TableFrameCell(value='4'),
        ],
        [
            TableFrameCell(value='2', css={'background-color': 'yellow'}),
            TableFrameCell(value='5', css={'background-color': 'yellow'}),
        ],
    ]


@pytest.mark.parametrize("axis", [None, 0, 1])
@pytest.mark.parametrize("subset", [None, pd.IndexSlice[2:3, ["col_2", "col_3"]]])
@pytest.mark.parametrize("color, props", [(None, "font-weight: bold;"), ("pink", None)])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        df.shape  # single chunk
    ])
def test_chunked(axis, subset, color, props, rows_per_chunk, cols_per_chunk):
    assert_patched_styler(
        df,
        lambda styler: styler.highlight_max(axis=axis, subset=subset, color=color, props=props),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize("axis", [None, 0, 1, "index", "columns"])
@pytest.mark.parametrize("subset", [None, pd.IndexSlice[2:3, ["col_2", "col_3"]]])
def test_can_handle_axis_values(axis, subset):
    assert_patched_styler(
        df,
        lambda styler: styler.highlight_max(axis=axis, subset=subset),
        2,
        2
    )


@pytest.mark.parametrize("subset", [
    2,  # reduce to row
    "col_2",  # reduce to column
    (2, "col_2"),  # reduce to scalar
])
def test_frame_can_handle_reducing_subset(subset):
    assert_patched_styler(
        df,
        lambda styler: styler.highlight_max(axis=None, subset=subset),
        2,
        2
    )


@pytest.mark.parametrize("axis", [None, 0, 1])
def test_highlight_max_nulls(axis):
    # GH 42750
    assert_patched_styler(
        pd.DataFrame({"a": [pd.NA, 1, None], "b": [np.nan, 1, -1]}),
        # replace pd.NA values with '' otherwise the are rendered as <NA> and interpreted as html tag
        lambda styler: styler.format(na_rep='').highlight_max(axis=axis),
        2,
        2
    )


def test_highlight_max_handles_na_values():
    # GH 45804
    assert_patched_styler(
        pd.DataFrame({"A": [0, np.nan, 10], "B": [1, pd.NA, 2]}, dtype="Int64"),
        # replace pd.NA values with '' otherwise the are rendered as <NA> and interpreted as html tag
        lambda styler: styler.format(na_rep='').highlight_max(axis=1),
        2,
        2
    )


def test_for_new_parameters():
    assert_style_func_parameters(
        df.style.highlight_max,
        ['axis', 'subset', 'color', 'props']
    )
