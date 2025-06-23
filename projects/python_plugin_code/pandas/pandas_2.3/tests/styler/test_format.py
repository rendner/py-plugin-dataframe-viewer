import pandas as pd
import pytest

from cms_rendner_sdfv.base.types import Cell, CellMeta
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext
from tests.helpers.asserts.assert_patched_styler import assert_patched_styler

df = pd.DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


def test_with_subset():
    ctx = PatchedStylerContext(df.style.format('{:+.2f}', subset=pd.IndexSlice[0, ["col_2"]]))
    chunk_data = ctx.get_chunk_data_generator().generate()
    assert chunk_data.cells[0][0] == Cell(value='0', meta=CellMeta.min().pack())
    assert chunk_data.cells[0][2] == Cell(value='+10.00', meta=CellMeta.min().pack())


@pytest.mark.parametrize("subset", [None, pd.IndexSlice[2:3, ["col_2", "col_3"]]])
@pytest.mark.parametrize("formatter", [None, '{:+.2f}'])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        df.shape  # single chunk
    ])
def test_chunked(subset, formatter, rows_per_chunk, cols_per_chunk):
    assert_patched_styler(
        df,
        lambda styler:  styler.format(formatter, subset=subset),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize("na_rep, precision, decimal", [("nothing", 1, "-")])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        df.shape  # single chunk
    ])
def test_parameters(na_rep, precision, decimal, rows_per_chunk, cols_per_chunk):
    assert_patched_styler(
        df,
        lambda styler:  styler.format(formatter='{:+.2f}', na_rep=na_rep, precision=precision, decimal=decimal),
        rows_per_chunk,
        cols_per_chunk
    )
