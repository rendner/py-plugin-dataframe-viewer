import pandas as pd

from cms_rendner_sdfv.base.constants import CELL_MAX_STR_LEN, DESCRIBE_COL_MAX_STR_LEN
from cms_rendner_sdfv.base.types import TableFrame, TableFrameCell, TableFrameColumn, TableFrameLegend
from cms_rendner_sdfv.pandas.frame.frame_context import FrameContext
from cms_rendner_sdfv.pandas.shared.types import FilterCriteria
from tests.helpers.asserts.assert_table_frames import assert_table_frames


def test_truncate_cells():
    df = pd.DataFrame.from_dict({'A': ['ab' * CELL_MAX_STR_LEN]})
    ctx = FrameContext(df)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert len(actual.cells[0][0].value) == CELL_MAX_STR_LEN


def test_truncate_column_describe():
    df = pd.DataFrame.from_dict({'A': ['ab' * DESCRIBE_COL_MAX_STR_LEN]})
    ctx = FrameContext(df)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert len(actual.columns[0].describe.get('top')) == DESCRIBE_COL_MAX_STR_LEN


def test_index_int():
    df = pd.DataFrame.from_dict({
        0: [0, 1, 2],
        1: [3, 4, 5],
    })
    ctx = FrameContext(df)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert_table_frames(
        actual,
        TableFrame(
            index_labels=[['0'], ['1'], ['2']],
            columns=[
                TableFrameColumn(dtype='int64', labels=['0']),
                TableFrameColumn(dtype='int64', labels=['1']),
            ],
            cells=[
                [TableFrameCell(value='0'), TableFrameCell(value='3')],
                [TableFrameCell(value='1'), TableFrameCell(value='4')],
                [TableFrameCell(value='2'), TableFrameCell(value='5')],
            ],
        )
    )


def test_index_string():
    df = pd.DataFrame.from_dict({
        'col_0': [0, 1, 2],
        'col_1': [3, 4, 5],
    })
    ctx = FrameContext(df)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert_table_frames(
        actual,
        TableFrame(
            index_labels=[['0'], ['1'], ['2']],
            columns=[
                TableFrameColumn(dtype='int64', labels=['col_0']),
                TableFrameColumn(dtype='int64', labels=['col_1']),
            ],
            cells=[
                [TableFrameCell(value='0'), TableFrameCell(value='3')],
                [TableFrameCell(value='1'), TableFrameCell(value='4')],
                [TableFrameCell(value='2'), TableFrameCell(value='5')],
            ],
        )
    )


def test_leveled_columns():
    df = pd.DataFrame.from_dict({
        ('A', 'col_0'): [0, 1, 2],
        ('A', 'col_1'): [3, 4, 5],
        ('B', 'col_2'): [6, 7, 8],
    })
    ctx = FrameContext(df)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert_table_frames(
        actual,
        TableFrame(
            index_labels=[['0'], ['1'], ['2']],
            columns=[
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
    assert_table_frames(
        actual,
        TableFrame(
            index_labels=[['X', 'green'], ['X', 'purple'], ['Y', 'green'], ['Y', 'purple']],
            columns=[
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
    assert_table_frames(
        actual,
        TableFrame(
            index_labels=[['X', 'green'], ['X', 'purple'], ['Y', 'green'], ['Y', 'purple']],
            columns=[
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
    )


def test_multi_index_multi_columns_with_named_index_levels_and_named_column_levels():
    index = pd.MultiIndex.from_product([[2013, 2014], [1, 2]], names=['year', 'visit'])
    columns = pd.MultiIndex.from_product([['Bob', 'Guido', 'Sue'], ['HR', 'AI']], names=['subject', 'type'])
    data = [[i] * 6 for i in range(0, 4)]

    df = pd.DataFrame(data, index=index, columns=columns)

    ctx = FrameContext(df)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert_table_frames(
        actual,
        TableFrame(
            index_labels=[['2013', '1'], ['2013', '2'], ['2014', '1'], ['2014', '2']],
            columns=[
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
    )


def test_index_multi_columns_with_named_column_levels():
    columns = pd.MultiIndex.from_product([['Bob', 'Guido', 'Sue'], ['HR', 'AI']], names=['subject', 'type'])
    data = [[i] * 6 for i in range(0, 4)]

    df = pd.DataFrame(data, columns=columns)

    ctx = FrameContext(df)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert_table_frames(
        actual,
        TableFrame(
            index_labels=[['0'], ['1'], ['2'], ['3']],
            columns=[
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
    )


def test_column_describe():
    df = pd.DataFrame.from_dict({
        'categorical': pd.Categorical(['d', 'e', 'f']),
        'numeric': [1, 2, 3],
    })
    ctx = FrameContext(df)
    actual = ctx.get_table_frame_generator().generate()
    assert_table_frames(
        actual,
        TableFrame(
            index_labels=[['0'], ['1'], ['2']],
            columns=[
                TableFrameColumn(
                    dtype='category',
                    labels=['categorical'],
                    describe={
                        'count': '3',
                        'unique': '3',
                        'top': 'd',
                        'freq': '1',
                    },
                ),
                TableFrameColumn(
                    dtype='int64',
                    labels=['numeric'],
                    describe={
                        'count': '3.0',
                        'mean': '2.0',
                        'std': '1.0',
                        'min': '1.0',
                        '25%': '1.5',
                        '50%': '2.0',
                        '75%': '2.5',
                        'max': '3.0',
                    },
                ),
            ],
            cells=[
                [TableFrameCell(value='d'), TableFrameCell(value='1')],
                [TableFrameCell(value='e'), TableFrameCell(value='2')],
                [TableFrameCell(value='f'), TableFrameCell(value='3')]
            ],
        ),
        include_column_describe=True,
    )


def test_column_describe_with_filter():
    df = pd.DataFrame.from_dict({'a': [1, 2, 3, "a", "b", "c"]})
    filter_frame = df[[isinstance(x, int) for x in df['a']]]
    ctx = FrameContext(df, FilterCriteria.from_frame(filter_frame))

    actual = ctx.get_table_frame_generator().generate()
    assert_table_frames(
        actual,
        TableFrame(
            index_labels=[['0'], ['1'], ['2']],
            columns=[
                TableFrameColumn(
                    dtype='object',
                    labels=['a'],
                    describe={
                        'count': '3',
                        'unique': '3',
                        'top': '1',
                        'freq': '1',
                    },
                ),
            ],
            cells=[
                [TableFrameCell(value='1')],
                [TableFrameCell(value='2')],
                [TableFrameCell(value='3')],
            ],
        ),
        include_column_describe=True,
    )
