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

    ctx = PatchedStylerContext(my_df.style.background_gradient())
    actual = ctx.get_chunk_data_generator().generate()

    assert actual.cells == [
        [
            Cell(value='0', css={'background-color': '#fff7fb', 'color': '#000000'}),
            Cell(value='3', css={'background-color': '#fff7fb', 'color': '#000000'}),
        ],
        [
            Cell(value='1', css={'background-color': '#73a9cf', 'color': '#000000'}),
            Cell(value='4', css={'background-color': '#73a9cf', 'color': '#000000'}),
        ],
        [
            Cell(value='2', css={'background-color': '#023858', 'color': '#f1f1f1'}),
            Cell(value='5', css={'background-color': '#023858', 'color': '#f1f1f1'}),
        ],
    ]


@pytest.mark.parametrize("axis", [None, 0, 1])
@pytest.mark.parametrize("subset", [None, pd.IndexSlice[2:3, ["col_2", "col_3"]]])
@pytest.mark.parametrize("vmin, vmax", [(None, None), (5, 10)])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        df.shape  # single chunk
    ])
def test_chunked(axis, subset, vmin, vmax, rows_per_chunk, cols_per_chunk):
    assert_patched_styler(
        df,
        lambda styler: styler.background_gradient(axis=axis, subset=subset, vmin=vmin, vmax=vmax),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize("axis", [None, 0, 1, "index", "columns"])
@pytest.mark.parametrize("subset", [None, pd.IndexSlice[2:3, ["col_2", "col_3"]]])
def test_can_handle_axis_values(axis, subset):
    assert_patched_styler(
        df,
        lambda styler: styler.background_gradient(axis=axis, subset=subset),
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
        lambda styler: styler.background_gradient(axis=None, subset=subset),
        2,
        2
    )
