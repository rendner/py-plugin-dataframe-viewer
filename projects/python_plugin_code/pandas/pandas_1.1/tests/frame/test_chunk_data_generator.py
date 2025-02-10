import pandas as pd

from cms_rendner_sdfv.base.constants import CELL_MAX_STR_LEN
from cms_rendner_sdfv.base.types import ChunkData, Cell
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
    assert actual == ChunkData(
        index_labels=[['0'], ['1'], ['2']],
        cells=[
            [Cell(value='0'), Cell(value='3')],
            [Cell(value='1'), Cell(value='4')],
            [Cell(value='2'), Cell(value='5')],
        ],
    )


def test_index_string():
    df = pd.DataFrame.from_dict({
        'col_0': [0, 1, 2],
        'col_1': [3, 4, 5],
    })
    ctx = FrameContext(df)
    actual = ctx.get_chunk_data_generator().generate_by_combining_chunks(2, 2)
    assert actual == ChunkData(
        index_labels=[['0'], ['1'], ['2']],
        cells=[
            [Cell(value='0'), Cell(value='3')],
            [Cell(value='1'), Cell(value='4')],
            [Cell(value='2'), Cell(value='5')],
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
    assert actual == ChunkData(
        index_labels=[['X', 'green'], ['X', 'purple'], ['Y', 'green'], ['Y', 'purple']],
        cells=[
            [Cell(value='0'), Cell(value='4')],
            [Cell(value='1'), Cell(value='5')],
            [Cell(value='2'), Cell(value='6')],
            [Cell(value='3'), Cell(value='7')],
        ],
    )
