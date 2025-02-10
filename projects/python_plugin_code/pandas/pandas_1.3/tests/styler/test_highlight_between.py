import pandas as pd
import pytest

from cms_rendner_sdfv.base.types import Cell
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext
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

    ctx = PatchedStylerContext(my_df.style.highlight_between(left=2, right=4))
    actual = ctx.get_chunk_data_generator().generate()

    assert actual.cells == [
        [
            Cell(value='0'),
            Cell(value='3', css={'background-color': 'yellow'}),
        ],
        [
            Cell(value='1'),
            Cell(value='4', css={'background-color': 'yellow'}),
        ],
        [
            Cell(value='2', css={'background-color': 'yellow'}),
            Cell(value='5'),
        ],
    ]


@pytest.mark.parametrize("axis", [None, 0, 1])
@pytest.mark.parametrize("subset", [None, pd.IndexSlice[2:3, ["col_2", "col_3"]]])
@pytest.mark.parametrize("color", [None, "pink"])
@pytest.mark.parametrize("props", [None, "font-weight: bold;"])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        df.shape  # single chunk
    ])
def test_chunked(axis, subset, color, props, rows_per_chunk, cols_per_chunk):
    assert_patched_styler(
        df,
        lambda styler: styler.highlight_between(axis=axis, subset=subset, color=color, props=props),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize("axis", [None, 0, 1, "index", "columns"])
@pytest.mark.parametrize("subset", [None, pd.IndexSlice[2:3, ["col_2", "col_3"]]])
def test_can_handle_axis_values(axis, subset):
    assert_patched_styler(
        df,
        lambda styler: styler.highlight_between(axis=axis, subset=subset),
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
        lambda styler: styler.highlight_between(axis=None, subset=subset),
        2,
        2
    )


# https://pandas.pydata.org/pandas-docs/stable/reference/api/pandas.io.formats.style.Styler.highlight_between.html
pandas_api_example_df = pd.DataFrame({
    'One': [1.2, 1.6, 1.5],
    'Two': [2.9, 2.1, 2.5],
    'Three': [3.1, 3.2, 3.8],
})


def test_pandas_api_example_1():
    assert_patched_styler(
        pandas_api_example_df,
        lambda styler: styler.highlight_between(left=2.1, right=2.9),
        2,
        2
    )


def test_pandas_api_example_2():
    assert_patched_styler(
        pandas_api_example_df,
        lambda styler: styler.highlight_between(left=[1.4, 2.4, 3.4], right=[1.6, 2.6, 3.6], axis=1),
        2,
        2
    )


def test_pandas_api_example_3():
    assert_patched_styler(
        pandas_api_example_df,
        lambda styler: styler.highlight_between(left=[[2, 2, 3], [2, 2, 3], [3, 3, 3]], right=3.5, axis=None),
        2,
        2
    )


pandas_test_example_df = pd.DataFrame({
    'One': [1.2, 1.6, 1.5],
    'Two': [2.9, 2.1, 2.5],
    'Three': [3.1, 3.2, 3.8],
})


# https://github.com/pandas-dev/pandas/blob/v1.3.0/pandas/tests/io/formats/style/test_highlight.py#L81-L101
@pytest.mark.parametrize(
    "kwargs",
    [
        {"left": 0, "right": 1},  # test basic range
        {"left": 0, "right": 1, "props": "background-color: yellow"},  # test props
        {"left": -100, "right": 100, "subset": pd.IndexSlice[[0, 1], :]},  # test subset
        {"left": 0, "subset": pd.IndexSlice[[0, 1], :]},  # test no right
        {"right": 1},  # test no left
        {"left": [0, 0, 11], "axis": 0},  # test left as sequence
        {"left": pd.DataFrame({"A": [0, 0, 11], "B": [1, 1, 11]}), "axis": None},  # axis
        {"left": 0, "right": [0, 0, 1], "axis": 1},  # test sequence right
    ],
)
def test_pandas_test_example_highlight_between(kwargs):
    assert_patched_styler(
        pandas_test_example_df,
        lambda styler: styler.highlight_between(**kwargs),
        2,
        2
    )
