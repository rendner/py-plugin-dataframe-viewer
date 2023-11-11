import pandas as pd
from pandas import option_context

from cms_rendner_sdfv.base.types import TableFrame, TableFrameCell
from cms_rendner_sdfv.pandas.frame.frame_context import FrameContext
from cms_rendner_sdfv.pandas.frame.table_frame_generator import TableFrameGenerator


def test_index_int():
    df = pd.DataFrame.from_dict({
        0: [0, 1, 2],
        1: [3, 4, 5],
    })
    ctx = FrameContext(df)
    actual = TableFrameGenerator(ctx).generate_by_combining_chunks(2, 2)
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
    ctx = FrameContext(df)
    actual = TableFrameGenerator(ctx).generate_by_combining_chunks(2, 2)
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
    ctx = FrameContext(df)
    actual = TableFrameGenerator(ctx).generate_by_combining_chunks(2, 2)
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

    ctx = FrameContext(df)
    actual = TableFrameGenerator(ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['X', 'green'], ['X', 'purple'], ['Y', 'green'], ['Y', 'purple']],
        column_labels=[['col_0'], ['col_1']],
        legend=None,
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

    ctx = FrameContext(df)
    actual = TableFrameGenerator(ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['X', 'green'], ['X', 'purple'], ['Y', 'green'], ['Y', 'purple']],
        column_labels=[['A', 'col_0'], ['B', 'col_1']],
        legend=None,
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

    ctx = FrameContext(df)
    actual = TableFrameGenerator(ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['2013', '1'], ['2013', '2'], ['2014', '1'], ['2014', '2']],
        column_labels=[['Bob', 'HR'], ['Bob', 'AI'], ['Guido', 'HR'], ['Guido', 'AI'], ['Sue', 'HR'], ['Sue', 'AI']],
        legend=None,
        cells=[[TableFrameCell(value=f'{i}')] * 6 for i in range(0, 4)],
    )


def test_index_multi_columns_with_named_column_levels():
    columns = pd.MultiIndex.from_product([['Bob', 'Guido', 'Sue'], ['HR', 'AI']], names=['subject', 'type'])
    data = [[i] * 6 for i in range(0, 4)]

    df = pd.DataFrame(data, columns=columns)

    ctx = FrameContext(df)
    actual = TableFrameGenerator(ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['0'], ['1'], ['2'], ['3']],
        column_labels=[['Bob', 'HR'], ['Bob', 'AI'], ['Guido', 'HR'], ['Guido', 'AI'], ['Sue', 'HR'], ['Sue', 'AI']],
        legend=None,
        cells=[[TableFrameCell(value=f'{i}')] * 6 for i in range(0, 4)],
    )


def test_generate_by_combining_chunks():
    df = pd.DataFrame.from_dict({
        ('A', 'col_0'): [0, 1, 2, 3],
        ('B', 'col_1'): [4, 5, 6, 7],
        ('B', 'col_2'): [4, 5, 6, 7],
    })
    chars = ['X', 'Y']
    colors = ['green', 'purple']
    df.index = pd.MultiIndex.from_product([chars, colors], names=['char', 'color'])

    ctx = FrameContext(df)
    table_generator = TableFrameGenerator(ctx)
    actual = table_generator.generate_by_combining_chunks(rows_per_chunk=1, cols_per_chunk=1)
    assert actual == table_generator.generate_by_combining_chunks(2, 2)


def test_generate_ignores_max_elements_option():
    with option_context("styler.render.max_elements", 1):
        df = pd.DataFrame.from_dict({
            0: [0, 1, 2],
        })

        ctx = FrameContext(df)
        actual = TableFrameGenerator(ctx).generate()
        assert actual == TableFrame(
            index_labels=[['0'], ['1'], ['2']],
            column_labels=[['0']],
            cells=[
                [TableFrameCell(value='0')],
                [TableFrameCell(value='1')],
                [TableFrameCell(value='2')],
            ],
        )
