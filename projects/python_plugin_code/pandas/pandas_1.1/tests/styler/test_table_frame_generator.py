import pandas as pd

from cms_rendner_sdfv.base.constants import CELL_MAX_STR_LEN
from cms_rendner_sdfv.base.types import TableFrame, TableFrameCell
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext


def test_truncate_cells():
    df = pd.DataFrame.from_dict({'A': ['ab' * CELL_MAX_STR_LEN]})
    ctx = PatchedStylerContext(df.style)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert len(actual.cells[0][0].value) == CELL_MAX_STR_LEN


def test_index_int():
    df = pd.DataFrame.from_dict({
        0: [0, 1, 2],
        1: [3, 4, 5],
    })
    ctx = PatchedStylerContext(df.style)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['0'], ['1'], ['2']],
        cells=[
            [TableFrameCell(value='0'), TableFrameCell(value='3')],
            [TableFrameCell(value='1'), TableFrameCell(value='4')],
            [TableFrameCell(value='2'), TableFrameCell(value='5')],
        ],
    )


def test_index_string():
    df = pd.DataFrame.from_dict({
        'col_0': [0, 1, 2],
        'col_1': [3, 4, 5],
    })
    ctx = PatchedStylerContext(df.style)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['0'], ['1'], ['2']],
        cells=[
            [TableFrameCell(value='0'), TableFrameCell(value='3')],
            [TableFrameCell(value='1'), TableFrameCell(value='4')],
            [TableFrameCell(value='2'), TableFrameCell(value='5')],
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
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['X', 'green'], ['X', 'purple'], ['Y', 'green'], ['Y', 'purple']],
        cells=[
            [TableFrameCell(value='0'), TableFrameCell(value='4')],
            [TableFrameCell(value='1'), TableFrameCell(value='5')],
            [TableFrameCell(value='2'), TableFrameCell(value='6')],
            [TableFrameCell(value='3'), TableFrameCell(value='7')],
        ],
    )


def test_hide_index_headers():
    df = pd.DataFrame([[i] * 4 for i in range(4)])

    ctx = PatchedStylerContext(df.style.hide_index())
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[],
        cells=[[TableFrameCell(value=f'{i}')] * 4 for i in range(4)],
    )


def test_highlight_max():
    df = pd.DataFrame.from_dict({
        0: [0, 1, 2],
        1: [3, 4, 5],
    })
    ctx = PatchedStylerContext(df.style.highlight_max(color="red"))

    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['0'], ['1'], ['2']],
        cells=[
            [TableFrameCell(value='0'), TableFrameCell(value='3')],
            [TableFrameCell(value='1'), TableFrameCell(value='4')],
            [
                TableFrameCell(value='2', css={'background-color': 'red'}),
                TableFrameCell(value='5', css={'background-color': 'red'}),
            ],
        ],
    )
