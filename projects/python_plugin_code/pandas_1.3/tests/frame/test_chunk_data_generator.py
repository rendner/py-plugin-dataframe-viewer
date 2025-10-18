import pandas as pd

from cms_rendner_sdfv.base.constants import CELL_MAX_STR_LEN
from cms_rendner_sdfv.base.types import ChunkDataResponse, Cell, CellMeta
from cms_rendner_sdfv.pandas.frame.frame_context import FrameContext


def test_truncate_cells():
    df = pd.DataFrame.from_dict({'A': ['ab' * CELL_MAX_STR_LEN]})
    ctx = FrameContext(df)
    actual = ctx.get_chunk_data_generator().generate_by_combining_chunks(2, 2)
    assert len(actual.cells[0][0].value) == CELL_MAX_STR_LEN


def test_index_int():
    df = pd.DataFrame.from_dict({
        0: [0, 1, 2],
        1: [3, 4, 5],
    })
    ctx = FrameContext(df)
    actual = ctx.get_chunk_data_generator().generate_by_combining_chunks(2, 2)
    assert actual == ChunkDataResponse(
        row_headers=[['0'], ['1'], ['2']],
        cells=[
            [
                Cell(value='0', meta=CellMeta.min().pack()),
                Cell(value='3', meta=CellMeta.min().pack()),
            ],
            [
                Cell(value='1',  meta=CellMeta(cmap_value=50000).pack()),
                Cell(value='4',  meta=CellMeta(cmap_value=50000).pack()),
            ],
            [
                Cell(value='2',  meta=CellMeta.max().pack()),
                Cell(value='5',  meta=CellMeta.max().pack()),
            ],
        ],
    )


def test_index_string():
    df = pd.DataFrame.from_dict({
        'col_0': [0, 1, 2],
        'col_1': [3, 4, 5],
    })
    ctx = FrameContext(df)
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

    ctx = FrameContext(df)
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


def test_generate_ignores_max_elements_option():
    with pd.option_context("styler.render.max_elements", 1):
        df = pd.DataFrame.from_dict({
            0: [0, 1, 2],
        })

        ctx = FrameContext(df)
        actual = ctx.get_chunk_data_generator().generate()
        assert actual == ChunkDataResponse(
            row_headers=[['0'], ['1'], ['2']],
            cells=[
                [Cell(value='0', meta=CellMeta.min().pack())],
                [Cell(value='1', meta=CellMeta(cmap_value=50000).pack())],
                [Cell(value='2', meta=CellMeta.max().pack())],
            ],
        )
