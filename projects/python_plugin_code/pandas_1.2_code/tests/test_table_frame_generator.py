from plugin_code.table_frame_generator import TableFrameGenerator, TableFrame, TableFrameLegend, TableFrameCell
from plugin_code.patched_styler_context import PatchedStylerContext

import pandas as pd


def test_index_int():
    df = pd.DataFrame.from_dict({
        0: [0, 1, 2],
        1: [3, 4, 5],
    })
    ps_ctx = PatchedStylerContext(df.style)
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['0'], ['1'], ['2']],
        column_labels=[['0'], ['1']],
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
    ps_ctx = PatchedStylerContext(df.style)
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['0'], ['1'], ['2']],
        column_labels=[['col_0'], ['col_1']],
        cells=[
            [TableFrameCell(value='0'), TableFrameCell(value='3')],
            [TableFrameCell(value='1'), TableFrameCell(value='4')],
            [TableFrameCell(value='2'), TableFrameCell(value='5')],
        ],
    )


def test_leveled_columns():
    df = pd.DataFrame.from_dict({
        ('A', 'col_0'): [0, 1, 2],
        ('A', 'col_1'): [3, 4, 5],
        ('B', 'col_2'): [6, 7, 8],
    })
    ps_ctx = PatchedStylerContext(df.style)
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['0'], ['1'], ['2']],
        column_labels=[['A', 'col_0'], ['A', 'col_1'], ['B', 'col_2']],
        cells=[
            [TableFrameCell(value='0'), TableFrameCell(value='3'), TableFrameCell(value='6')],
            [TableFrameCell(value='1'), TableFrameCell(value='4'), TableFrameCell(value='7')],
            [TableFrameCell(value='2'), TableFrameCell(value='5'), TableFrameCell(value='8')],
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

    ps_ctx = PatchedStylerContext(df.style)
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['X', 'green'], ['X', 'purple'], ['Y', 'green'], ['Y', 'purple']],
        column_labels=[['col_0'], ['col_1']],
        legend=TableFrameLegend(index=['char', 'color'], column=[]),
        cells=[
            [TableFrameCell(value='0'), TableFrameCell(value='4')],
            [TableFrameCell(value='1'), TableFrameCell(value='5')],
            [TableFrameCell(value='2'), TableFrameCell(value='6')],
            [TableFrameCell(value='3'), TableFrameCell(value='7')],
        ],
    )


def test_multi_index_with_named_index_levels_and_leveled_columns():
    df = pd.DataFrame.from_dict({
        ('A', 'col_0'): [0, 1, 2, 3],
        ('B', 'col_1'): [4, 5, 6, 7],
    })
    chars = ['X', 'Y']
    colors = ['green', 'purple']
    df.index = pd.MultiIndex.from_product([chars, colors], names=['char', 'color'])

    ps_ctx = PatchedStylerContext(df.style)
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['X', 'green'], ['X', 'purple'], ['Y', 'green'], ['Y', 'purple']],
        column_labels=[['A', 'col_0'], ['B', 'col_1']],
        legend=TableFrameLegend(index=['char', 'color'], column=[]),
        cells=[
            [TableFrameCell(value='0'), TableFrameCell(value='4')],
            [TableFrameCell(value='1'), TableFrameCell(value='5')],
            [TableFrameCell(value='2'), TableFrameCell(value='6')],
            [TableFrameCell(value='3'), TableFrameCell(value='7')],
        ],
    )


def test_multi_index_multi_columns_with_named_index_levels_and_named_column_levels():
    index = pd.MultiIndex.from_product([[2013, 2014], [1, 2]], names=['year', 'visit'])
    columns = pd.MultiIndex.from_product([['Bob', 'Guido', 'Sue'], ['HR', 'AI']], names=['subject', 'type'])
    data = [[i] * 6 for i in range(0, 4)]

    df = pd.DataFrame(data, index=index, columns=columns)

    ps_ctx = PatchedStylerContext(df.style)
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['2013', '1'], ['2013', '2'], ['2014', '1'], ['2014', '2']],
        column_labels=[['Bob', 'HR'], ['Bob', 'AI'], ['Guido', 'HR'], ['Guido', 'AI'], ['Sue', 'HR'], ['Sue', 'AI']],
        legend=TableFrameLegend(index=['year', 'visit'], column=['subject', 'type']),
        cells=[[TableFrameCell(value=f'{i}')] * 6 for i in range(0, 4)],
    )


def test_index_multi_columns_with_named_column_levels():
    columns = pd.MultiIndex.from_product([['Bob', 'Guido', 'Sue'], ['HR', 'AI']], names=['subject', 'type'])
    data = [[i] * 6 for i in range(0, 4)]

    df = pd.DataFrame(data, columns=columns)

    ps_ctx = PatchedStylerContext(df.style)
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['0'], ['1'], ['2'], ['3']],
        column_labels=[['Bob', 'HR'], ['Bob', 'AI'], ['Guido', 'HR'], ['Guido', 'AI'], ['Sue', 'HR'], ['Sue', 'AI']],
        legend=TableFrameLegend(index=[], column=['subject', 'type']),
        cells=[[TableFrameCell(value=f'{i}')] * 6 for i in range(0, 4)],
    )


def test_hide_first_and_last_column():
    cols = pd.Index([i for i in range(4)], name='col_name')
    idx = pd.Index([i for i in range(4)], name='idx_name')
    data = [[i] * 4 for i in range(0, 4)]
    df = pd.DataFrame(data, index=idx, columns=cols)

    ps_ctx = PatchedStylerContext(df.style.hide_columns(df.columns[1:-1]))
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['0'], ['1'], ['2'], ['3']],
        column_labels=[['0'], ['3']],
        legend=TableFrameLegend(index=['idx_name'], column=['col_name']),
        cells=[[TableFrameCell(value=f'{i}')] * 2 for i in range(0, 4)],
    )


def test_hide_index_headers():
    df = pd.DataFrame([[i] * 4 for i in range(0, 4)])

    ps_ctx = PatchedStylerContext(df.style.hide_index())
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[],
        column_labels=[['0'], ['1'], ['2'], ['3']],
        cells=[[TableFrameCell(value=f'{i}')] * 4 for i in range(0, 4)],
    )


def test_highlight_max():
    df = pd.DataFrame.from_dict({
        0: [0, 1, 2],
        1: [3, 4, 5],
    })
    ps_ctx = PatchedStylerContext(df.style.highlight_max(color="red"))

    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['0'], ['1'], ['2']],
        column_labels=[['0'], ['1']],
        cells=[
            [TableFrameCell(value='0'), TableFrameCell(value='3')],
            [TableFrameCell(value='1'), TableFrameCell(value='4')],
            [
                # note: extra space in front of color name (bug in pandas 1.2)
                TableFrameCell(value='2', css={'background-color': ' red'}),
                TableFrameCell(value='5', css={'background-color': ' red'}),
            ],
        ],
    )
