import pandas as pd
import pytest

from cms_rendner_sdfv.base.types import Cell, CellMeta
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
        0: [0.0, 0.1, 0.2],
        1: [0.3, 0.4, 0.5],
    })

    ctx = PatchedStylerContext(my_df.style.highlight_quantile(q_left=0.2))
    actual = ctx.get_chunk_data_generator().generate()

    assert actual.cells == [
        [
            Cell(value='0.000000', meta=CellMeta.min().pack()),
            Cell(value='0.300000', meta=CellMeta.min().pack()),
        ],
        [
            Cell(value='0.100000', meta=CellMeta(cmap_value=50000, background_color='yellow').pack()),
            Cell(value='0.400000', meta=CellMeta(cmap_value=50000, background_color='yellow').pack()),
        ],
        [
            Cell(value='0.200000', meta=CellMeta.max(background_color='yellow').pack()),
            Cell(value='0.500000', meta=CellMeta.max(background_color='yellow').pack()),
        ],
    ]


@pytest.mark.parametrize("axis", [None, 0, 1])
@pytest.mark.parametrize("color, props", [(None, "font-weight: bold;"), ("pink", None)])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        df.shape  # single chunk
    ])
def test_chunked(axis, color, props, rows_per_chunk, cols_per_chunk):
    assert_patched_styler(
        df,
        lambda styler: styler.highlight_quantile(axis=axis, q_left=0.8, color=color, props=props),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize("axis", [None, 0, 1, "index", "columns"])
@pytest.mark.parametrize("subset", [None, pd.IndexSlice[2:3, ["col_2", "col_3"]]])
def test_can_handle_axis_values(axis, subset):
    assert_patched_styler(
        df,
        lambda styler: styler.highlight_quantile(axis=axis, subset=subset),
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
        lambda styler: styler.highlight_quantile(axis=None, subset=subset),
        2,
        2
    )


# https://github.com/pandas-dev/pandas/blob/v1.3.0/pandas/tests/io/formats/style/test_highlight.py#L150-L167
pandas_test_example_df = pd.DataFrame({
    'One': [1.2, 1.6, 1.5],
    'Two': [2.9, 2.1, 2.5],
    'Three': [3.1, 3.2, 3.8],
})


@pytest.mark.parametrize(
    "kwargs",
    [
        {"q_left": 0.5, "q_right": 1, "axis": 0},  # base case
        {"q_left": 0.5, "q_right": 1, "axis": None},  # test axis
        {"q_left": 0, "q_right": 1, "subset": pd.IndexSlice[2, :]},  # test subset
        {"q_left": 0.5, "axis": 0},  # test no high
        {"q_right": 1, "subset": pd.IndexSlice[2, :], "axis": 1},  # test no low
        {"q_left": 0.5, "axis": 0, "props": "background-color: yellow"},  # tst prop
    ],
)
def test_pandas_test_example_highlight_quantile(kwargs):
    assert_patched_styler(
        pandas_test_example_df,
        lambda styler: styler.highlight_quantile(**kwargs),
        2,
        2
    )


def test_for_new_parameters():
    assert_style_func_parameters(
        df.style.highlight_quantile,
        ['axis', 'subset', 'color', 'q_left', 'q_right', 'interpolation', 'inclusive', 'props']
    )
