import pandas as pd
from pandas import option_context

from cms_rendner_sdfv.base.types import TableFrame, TableFrameCell, TableFrameColumn, TableFrameLegend
from cms_rendner_sdfv.pandas.styler.table_frame_generator import TableFrameGenerator
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext


def test_index_int():
    df = pd.DataFrame.from_dict({
        0: [0, 1, 2],
        1: [3, 4, 5],
    })
    ps_ctx = PatchedStylerContext(df.style)
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
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
    ps_ctx = PatchedStylerContext(df.style)
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
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
    ps_ctx = PatchedStylerContext(df.style)
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
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

    ps_ctx = PatchedStylerContext(df.style)
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
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

    ps_ctx = PatchedStylerContext(df.style)
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
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

    ps_ctx = PatchedStylerContext(df.style)
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
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

    ps_ctx = PatchedStylerContext(df.style)
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
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


def test_hide_column_headers():
    cols = pd.Index([i for i in range(4)], name='col_name')
    idx = pd.Index([i for i in range(4)], name='idx_name')
    data = [[i] * 4 for i in range(0, 4)]
    df = pd.DataFrame(data, index=idx, columns=cols)

    ps_ctx = PatchedStylerContext(df.style.hide_columns())
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[['0'], ['1'], ['2'], ['3']],
        column_labels=[],
        legend=None,
        cells=[[TableFrameCell(value=f'{i}')] * 4 for i in range(0, 4)],
    )


def test_hide_index_headers():
    df = pd.DataFrame([[i] * 4 for i in range(0, 4)])

    ps_ctx = PatchedStylerContext(df.style.hide_index())
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[],
        column_labels=[
            TableFrameColumn(dtype='int64', labels=['0']),
            TableFrameColumn(dtype='int64', labels=['1']),
            TableFrameColumn(dtype='int64', labels=['2']),
            TableFrameColumn(dtype='int64', labels=['3']),
        ],
        cells=[[TableFrameCell(value=f'{i}')] * 4 for i in range(0, 4)],
    )


def test_hide_index_and_column_headers():
    df = pd.DataFrame([[i] * 4 for i in range(0, 4)])

    ps_ctx = PatchedStylerContext(df.style.hide_index().hide_columns())
    actual = TableFrameGenerator(ps_ctx).generate_by_combining_chunks(2, 2)
    assert actual == TableFrame(
        index_labels=[],
        column_labels=[],
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
        column_labels=[
            TableFrameColumn(dtype='int64', labels=['0']),
            TableFrameColumn(dtype='int64', labels=['1']),
        ],
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
            column_labels=[TableFrameColumn(dtype='int64', labels=['0'])],
            cells=[
                [TableFrameCell(value='0')],
                [TableFrameCell(value='1')],
                [TableFrameCell(value='2')],
            ],
        )
