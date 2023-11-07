from pandas import option_context

from cms_rendner_sdfv.pandas.styler.table_frame_generator import TableFrameGenerator, TableFrame, TableFrameLegend, TableFrameCell
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext

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


def test_hide_column_headers():
    cols = pd.Index([i for i in range(4)], name='col_name')
    idx = pd.Index([i for i in range(4)], name='idx_name')
    data = [[i] * 4 for i in range(0, 4)]
    df = pd.DataFrame(data, index=idx, columns=cols)

    ps_ctx = PatchedStylerContext(df.style.hide(axis='columns'))
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['0'], ['1'], ['2'], ['3']],
        column_labels=[],
        legend=TableFrameLegend(index=['idx_name'], column=[]),
        cells=[[TableFrameCell(value=f'{i}')] * 4 for i in range(0, 4)],
    )


def test_hide_index_level_names():
    midx = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]])
    data = [[i] * 6 for i in range(0, 6)]
    df = pd.DataFrame(data, index=midx, columns=midx)
    df.index.names = ["lev0", "lev1"]

    ps_ctx = PatchedStylerContext(df.style.hide(axis='index', names=False))
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[],
        column_labels=[['x', 'a'], ['x', 'b'], ['x', 'c'], ['y', 'a'], ['y', 'b'], ['y', 'c']],
        cells=[[TableFrameCell(value=f'{i}')] * 6 for i in range(0, 6)],
    )


def test_hide_index_headers():
    df = pd.DataFrame([[i] * 4 for i in range(0, 4)])

    ps_ctx = PatchedStylerContext(df.style.hide(axis='index'))
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[],
        column_labels=[['0'], ['1'], ['2'], ['3']],
        cells=[[TableFrameCell(value=f'{i}')] * 4 for i in range(0, 4)],
    )


def test_hide_index_and_column_headers():
    df = pd.DataFrame([[i] * 4 for i in range(0, 4)])

    ps_ctx = PatchedStylerContext(df.style.hide(axis='index').hide(axis='columns'))
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[],
        column_labels=[],
        cells=[[TableFrameCell(value=f'{i}')] * 4 for i in range(0, 4)],
    )


def test_hide_specific_level():
    midx = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]])
    data = [[i] * 6 for i in range(0, 6)]
    df = pd.DataFrame(data, index=midx, columns=midx)

    ps_ctx = PatchedStylerContext(df.style.hide(level=1))
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['x'], ['x'], ['x'], ['y'], ['y'], ['y']],
        column_labels=[['x', 'a'], ['x', 'b'], ['x', 'c'], ['y', 'a'], ['y', 'b'], ['y', 'c']],
        cells=[[TableFrameCell(value=f'{i}')] * 6 for i in range(0, 6)],
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
                TableFrameCell(value='2', css={'background-color': 'red'}),
                TableFrameCell(value='5', css={'background-color': 'red'}),
            ],
        ],
    )


def test_generate_ignores_max_elements_option():
    with option_context("styler.render.max_elements", 1):
        df = pd.DataFrame.from_dict({
            0: [0, 1, 2],
        })

        ps_ctx = PatchedStylerContext(df.style)
        actual = TableFrameGenerator(ps_ctx).generate()
        assert actual == TableFrame(
            index_labels=[['0'], ['1'], ['2']],
            column_labels=[['0']],
            cells=[
                [TableFrameCell(value='0')],
                [TableFrameCell(value='1')],
                [TableFrameCell(value='2')],
            ],
        )


def test_generate_ignores_max_rows_option():
    with option_context("styler.render.max_rows", 1):
        df = pd.DataFrame.from_dict({
            0: [0, 1, 2],
        })

        ps_ctx = PatchedStylerContext(df.style)
        actual = TableFrameGenerator(ps_ctx).generate()
        assert actual == TableFrame(
            index_labels=[['0'], ['1'], ['2']],
            column_labels=[['0']],
            cells=[
                [TableFrameCell(value='0')],
                [TableFrameCell(value='1')],
                [TableFrameCell(value='2')],
            ],
        )


def test_generate_ignores_max_columns_option():
    with option_context("styler.render.max_columns", 1):
        df = pd.DataFrame.from_dict({
            0: [0],
            1: [1],
            2: [2],
        })

        ps_ctx = PatchedStylerContext(df.style)
        actual = TableFrameGenerator(ps_ctx).generate()
        assert actual == TableFrame(
            index_labels=[['0']],
            column_labels=[['0'], ['1'], ['2']],
            cells=[[
                TableFrameCell(value='0'),
                TableFrameCell(value='1'),
                TableFrameCell(value='2'),
            ]],
        )