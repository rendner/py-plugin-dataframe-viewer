import pandas as pd
import pytest

from cms_rendner_sdfv.base.types import Cell
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext
from tests.helpers.asserts.assert_patched_styler import assert_patched_styler

df = pd.DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, None, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, None],
})


def test_expected_cell_styling():
    my_df = pd.DataFrame.from_dict({
        0: [0, 1, 2],
        1: [None, 4, 5],
    })

    ctx = PatchedStylerContext(my_df.style.highlight_null())
    actual = ctx.get_chunk_data_generator().generate()

    assert actual.cells == [
        [
            Cell(value='0'),
            Cell(value='nan', css={'background-color': 'red'}),
        ],
        [
            Cell(value='1'),
            Cell(value='4.000000'),
        ],
        [
            Cell(value='2'),
            Cell(value='5.000000'),
        ],
    ]


@pytest.mark.parametrize("subset", [None, pd.IndexSlice[2:3, ["col_2", "col_3"]]])
@pytest.mark.parametrize("null_color", [None, "pink"])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        df.shape  # single chunk
    ])
def test_chunked(subset, null_color, rows_per_chunk, cols_per_chunk):
    assert_patched_styler(
        df,
        lambda styler: styler.highlight_null(subset=subset, null_color=null_color),
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
        lambda styler: styler.highlight_null(subset=subset),
        2,
        2
    )
