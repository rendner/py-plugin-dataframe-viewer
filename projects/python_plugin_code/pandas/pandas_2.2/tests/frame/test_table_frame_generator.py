import pandas as pd
from pandas import option_context

from cms_rendner_sdfv.base.types import TableFrame, TableFrameCell, TableFrameColumn, TableFrameLegend
from cms_rendner_sdfv.pandas.frame.frame_context import FrameContext


def test_index_int():
    df = pd.DataFrame.from_dict({
        0: [0, 1, 2],
        1: [3, 4, 5],
    })
    ctx = FrameContext(df)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['0'], ['1'], ['2']],
        column_labels=[
            TableFrameColumn(dtype='int64', labels=['0']),
            TableFrameColumn(dtype='int64', labels=['1']),
        ],
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
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['0'], ['1'], ['2']],
        column_labels=[
            TableFrameColumn(dtype='int64', labels=['col_0']),
            TableFrameColumn(dtype='int64', labels=['col_1']),
        ],
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
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['0'], ['1'], ['2']],
        column_labels=[
            TableFrameColumn(dtype='int64', labels=['A', 'col_0']),
            TableFrameColumn(dtype='int64', labels=['A', 'col_1']),
            TableFrameColumn(dtype='int64', labels=['B', 'col_2']),
        ],
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
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['X', 'green'], ['X', 'purple'], ['Y', 'green'], ['Y', 'purple']],
        column_labels=[
            TableFrameColumn(dtype='int64', labels=['col_0']),
            TableFrameColumn(dtype='int64', labels=['col_1']),
        ],
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
    }).astype({('B', 'col_1'): 'float32'})
    chars = ['X', 'Y']
    colors = ['green', 'purple']
    df.index = pd.MultiIndex.from_product([chars, colors], names=['char', 'color'])

    ctx = FrameContext(df)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['X', 'green'], ['X', 'purple'], ['Y', 'green'], ['Y', 'purple']],
        column_labels=[
            TableFrameColumn(dtype='int64', labels=['A', 'col_0']),
            TableFrameColumn(dtype='float32', labels=['B', 'col_1']),
        ],
        legend=TableFrameLegend(index=['char', 'color'], column=[]),
        cells=[
            [TableFrameCell(value='0'), TableFrameCell(value='4.000000')],
            [TableFrameCell(value='1'), TableFrameCell(value='5.000000')],
            [TableFrameCell(value='2'), TableFrameCell(value='6.000000')],
            [TableFrameCell(value='3'), TableFrameCell(value='7.000000')],
        ],
    )


def test_multi_index_multi_columns_with_named_index_levels_and_named_column_levels():
    index = pd.MultiIndex.from_product([[2013, 2014], [1, 2]], names=['year', 'visit'])
    columns = pd.MultiIndex.from_product([['Bob', 'Guido', 'Sue'], ['HR', 'AI']], names=['subject', 'type'])
    data = [[i] * 6 for i in range(0, 4)]

    df = pd.DataFrame(data, index=index, columns=columns)

    ctx = FrameContext(df)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['2013', '1'], ['2013', '2'], ['2014', '1'], ['2014', '2']],
        column_labels=[
            TableFrameColumn(dtype='int64', labels=['Bob', 'HR']),
            TableFrameColumn(dtype='int64', labels=['Bob', 'AI']),
            TableFrameColumn(dtype='int64', labels=['Guido', 'HR']),
            TableFrameColumn(dtype='int64', labels=['Guido', 'AI']),
            TableFrameColumn(dtype='int64', labels=['Sue', 'HR']),
            TableFrameColumn(dtype='int64', labels=['Sue', 'AI']),
        ],
        legend=TableFrameLegend(index=['year', 'visit'], column=['subject', 'type']),
        cells=[[TableFrameCell(value=f'{i}')] * 6 for i in range(0, 4)],
    )


def test_index_multi_columns_with_named_column_levels():
    columns = pd.MultiIndex.from_product([['Bob', 'Guido', 'Sue'], ['HR', 'AI']], names=['subject', 'type'])
    data = [[i] * 6 for i in range(0, 4)]

    df = pd.DataFrame(data, columns=columns)

    ctx = FrameContext(df)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['0'], ['1'], ['2'], ['3']],
        column_labels=[
            TableFrameColumn(dtype='int64', labels=['Bob', 'HR']),
            TableFrameColumn(dtype='int64', labels=['Bob', 'AI']),
            TableFrameColumn(dtype='int64', labels=['Guido', 'HR']),
            TableFrameColumn(dtype='int64', labels=['Guido', 'AI']),
            TableFrameColumn(dtype='int64', labels=['Sue', 'HR']),
            TableFrameColumn(dtype='int64', labels=['Sue', 'AI']),
        ],
        legend=TableFrameLegend(index=[], column=['subject', 'type']),
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
    table_generator = ctx.get_table_frame_generator()
    actual = table_generator.generate_by_combining_chunks(rows_per_chunk=1, cols_per_chunk=1)
    assert actual == table_generator.generate_by_combining_chunks(2, 2)


def test_generate_ignores_max_elements_option():
    with option_context("styler.render.max_elements", 1):
        df = pd.DataFrame.from_dict({
            0: [0, 1, 2],
        })

        ctx = FrameContext(df)
        actual = ctx.get_table_frame_generator().generate()
        assert actual == TableFrame(
            index_labels=[['0'], ['1'], ['2']],
            column_labels=[TableFrameColumn(dtype='int64', labels=['0'])],
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

        ctx = FrameContext(df)
        actual = ctx.get_table_frame_generator().generate()
        assert actual == TableFrame(
            index_labels=[['0'], ['1'], ['2']],
            column_labels=[TableFrameColumn(dtype='int64', labels=['0'])],
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

        ctx = FrameContext(df)
        actual = ctx.get_table_frame_generator().generate()
        assert actual == TableFrame(
            index_labels=[['0']],
            column_labels=[
                TableFrameColumn(dtype='int64', labels=['0']),
                TableFrameColumn(dtype='int64', labels=['1']),
                TableFrameColumn(dtype='int64', labels=['2']),
            ],
            cells=[[
                TableFrameCell(value='0'),
                TableFrameCell(value='1'),
                TableFrameCell(value='2'),
            ]],
        )
