import pandas as pd

from cms_rendner_sdfv.base.constants import CELL_MAX_STR_LEN
from cms_rendner_sdfv.base.types import ChunkDataResponse, Cell, CellMeta
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext


def test_truncate_cells():
    df = pd.DataFrame.from_dict({'A': ['ab' * CELL_MAX_STR_LEN]})
    ctx = PatchedStylerContext(df.style)
    actual = ctx.get_chunk_data_generator().generate_by_combining_chunks(2, 2)
    assert len(actual.cells[0][0].value) == CELL_MAX_STR_LEN


def test_index_int():
    df = pd.DataFrame.from_dict({
        0: [0, 1, 2],
        1: [3, 4, 5],
    })
    ctx = PatchedStylerContext(df.style)
    actual = ctx.get_chunk_data_generator().generate_by_combining_chunks(2, 2)
    assert actual == ChunkDataResponse(
        row_headers=[['0'], ['1'], ['2']],
        cells=[
            [
                Cell(value='0', meta=CellMeta.min().pack()),
                Cell(value='3', meta=CellMeta.min().pack()),
            ],
            [
                Cell(value='1', meta=CellMeta(cmap_value=50000).pack()),
                Cell(value='4', meta=CellMeta(cmap_value=50000).pack()),
            ],
            [
                Cell(value='2', meta=CellMeta.max().pack()),
                Cell(value='5', meta=CellMeta.max().pack()),
            ],
        ],
    )


def test_index_string():
    df = pd.DataFrame.from_dict({
        'col_0': [0, 1, 2],
        'col_1': [3, 4, 5],
    })
    ctx = PatchedStylerContext(df.style)
    actual = ctx.get_chunk_data_generator().generate_by_combining_chunks(2, 2)
    assert actual == ChunkDataResponse(
        row_headers=[['0'], ['1'], ['2']],
        cells=[
            [
                Cell(value='0', meta=CellMeta.min().pack()),
                Cell(value='3', meta=CellMeta.min().pack()),
            ],
            [
                Cell(value='1', meta=CellMeta(cmap_value=50000).pack()),
                Cell(value='4', meta=CellMeta(cmap_value=50000).pack()),
            ],
            [
                Cell(value='2', meta=CellMeta.max().pack()),
                Cell(value='5', meta=CellMeta.max().pack()),
            ],
        ],
    )


def test_multi_index_index_with_named_index_levels():
    df = pd.DataFrame.from_dict({
        'col_0': [0, 1, 2, 3],
        'col_1': [4, 5, 6, 7],
    })
    chars = ['X', 'Y']
    colors = ['green', 'purple']
    df.index = pd.MultiIndex.from_product([chars, colors], names=['char', 'color'])

    ctx = PatchedStylerContext(df.style)
    actual = ctx.get_chunk_data_generator().generate_by_combining_chunks(2, 2)
    assert actual == ChunkDataResponse(
        row_headers=[['X', 'green'], ['X', 'purple'], ['Y', 'green'], ['Y', 'purple']],
        cells=[
            [
                Cell(value='0', meta=CellMeta.min().pack()),
                Cell(value='4', meta=CellMeta.min().pack()),
            ],
            [
                Cell(value='1', meta=CellMeta(cmap_value=33333).pack()),
                Cell(value='5', meta=CellMeta(cmap_value=33333).pack()),
            ],
            [
                Cell(value='2', meta=CellMeta(cmap_value=66666).pack()),
                Cell(value='6', meta=CellMeta(cmap_value=66666).pack()),
            ],
            [
                Cell(value='3', meta=CellMeta.max().pack()),
                Cell(value='7', meta=CellMeta.max().pack()),
            ],
        ],
    )


def test_hide_index_level_names():
    midx = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]])
    data = [[i] * 6 for i in range(6)]
    df = pd.DataFrame(data, index=midx, columns=midx)
    df.index.names = ["lev0", "lev1"]

    ctx = PatchedStylerContext(df.style.hide(axis='index', names=False))
    actual = ctx.get_chunk_data_generator().generate_by_combining_chunks(2, 2)
    assert actual == ChunkDataResponse(
        row_headers=None,
        cells=[
            [Cell(value='0', meta=CellMeta.min().pack())] * 6,
            [Cell(value='1', meta=CellMeta(cmap_value=20000).pack())] * 6,
            [Cell(value='2', meta=CellMeta(cmap_value=40000).pack())] * 6,
            [Cell(value='3', meta=CellMeta(cmap_value=60000).pack())] * 6,
            [Cell(value='4', meta=CellMeta(cmap_value=80000).pack())] * 6,
            [Cell(value='5', meta=CellMeta.max().pack())] * 6,
        ],
    )


def test_hide_index_headers():
    df = pd.DataFrame([[i] * 4 for i in range(4)])

    ctx = PatchedStylerContext(df.style.hide(axis='index'))
    actual = ctx.get_chunk_data_generator().generate_by_combining_chunks(2, 2)
    assert actual == ChunkDataResponse(
        row_headers=None,
        cells=[
            [Cell(value='0', meta=CellMeta.min().pack())] * 4,
            [Cell(value='1', meta=CellMeta(cmap_value=33333).pack())] * 4,
            [Cell(value='2', meta=CellMeta(cmap_value=66666).pack())] * 4,
            [Cell(value='3', meta=CellMeta.max().pack())] * 4,
        ],
    )


def test_hide_specific_level():
    midx = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]])
    data = [[i] * 6 for i in range(6)]
    df = pd.DataFrame(data, index=midx, columns=midx)

    ctx = PatchedStylerContext(df.style.hide(level=1))
    actual = ctx.get_chunk_data_generator().generate_by_combining_chunks(2, 2)
    assert actual == ChunkDataResponse(
        row_headers=[['x'], ['x'], ['x'], ['y'], ['y'], ['y']],
        cells=[
            [Cell(value='0', meta=CellMeta.min().pack())] * 6,
            [Cell(value='1', meta=CellMeta(cmap_value=20000).pack())] * 6,
            [Cell(value='2', meta=CellMeta(cmap_value=40000).pack())] * 6,
            [Cell(value='3', meta=CellMeta(cmap_value=60000).pack())] * 6,
            [Cell(value='4', meta=CellMeta(cmap_value=80000).pack())] * 6,
            [Cell(value='5', meta=CellMeta.max().pack())] * 6,
        ],
    )


def test_highlight_max():
    df = pd.DataFrame.from_dict({
        0: [0, 1, 2],
        1: [3, 4, 5],
    })
    ctx = PatchedStylerContext(df.style.highlight_max(color="red"))

    actual = ctx.get_chunk_data_generator().generate_by_combining_chunks(2, 2)
    assert actual == ChunkDataResponse(
        row_headers=[['0'], ['1'], ['2']],
        cells=[
            [
                Cell(value='0', meta=CellMeta.min().pack()),
                Cell(value='3', meta=CellMeta.min().pack()),
            ],
            [
                Cell(value='1', meta=CellMeta(cmap_value=50000).pack()),
                Cell(value='4', meta=CellMeta(cmap_value=50000).pack()),
            ],
            [
                Cell(value='2', meta=CellMeta.max(background_color='red').pack()),
                Cell(value='5', meta=CellMeta.max(background_color='red').pack()),
            ],
        ],
    )


def test_generate_ignores_max_elements_option():
    with pd.option_context("styler.render.max_elements", 1):
        df = pd.DataFrame.from_dict({
            0: [0, 1, 2],
        })

        ctx = PatchedStylerContext(df.style)
        actual = ctx.get_chunk_data_generator().generate()
        assert actual == ChunkDataResponse(
            row_headers=[['0'], ['1'], ['2']],
            cells=[
                [Cell(value='0', meta=CellMeta.min().pack())],
                [Cell(value='1', meta=CellMeta(cmap_value=50000).pack())],
                [Cell(value='2', meta=CellMeta.max().pack())],
            ],
        )


def test_generate_ignores_max_rows_option():
    with pd.option_context("styler.render.max_rows", 1):
        df = pd.DataFrame.from_dict({
            0: [0, 1, 2],
        })

        ctx = PatchedStylerContext(df.style)
        actual = ctx.get_chunk_data_generator().generate()
        assert actual == ChunkDataResponse(
            row_headers=[['0'], ['1'], ['2']],
            cells=[
                [Cell(value='0', meta=CellMeta.min().pack())],
                [Cell(value='1', meta=CellMeta(cmap_value=50000).pack())],
                [Cell(value='2', meta=CellMeta.max().pack())],
            ],
        )


def test_generate_ignores_max_columns_option():
    with pd.option_context("styler.render.max_columns", 1):
        df = pd.DataFrame.from_dict({
            0: [0],
            1: [1],
            2: [2],
        })

        ctx = PatchedStylerContext(df.style)
        actual = ctx.get_chunk_data_generator().generate()
        assert actual == ChunkDataResponse(
            row_headers=[['0']],
            cells=[[
                Cell(value='0', meta=CellMeta.min_max().pack()),
                Cell(value='1', meta=CellMeta.min_max().pack()),
                Cell(value='2', meta=CellMeta.min_max().pack()),
            ]],
        )
