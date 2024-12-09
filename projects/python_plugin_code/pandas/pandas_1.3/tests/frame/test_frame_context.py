import numpy as np
import pandas as pd
from pandas import DataFrame

from cms_rendner_sdfv.base.types import TableStructureColumnInfo, TableStructureLegend, TableStructureColumn, \
    TableStructure, NestedCompletionVariant, CompletionVariant
from cms_rendner_sdfv.pandas.frame.frame_context import FrameContext
from cms_rendner_sdfv.pandas.shared.types import FilterCriteria

df = DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [1, 2, 3, 4, 5],
    "col_2": [2, 3, 4, 5, 6],
    "col_3": [3, 4, 5, 6, 7],
    "col_4": [4, 5, 6, 7, 8],
})

df2 = DataFrame.from_dict({
    "A": [0, 1, 2, 3, 4],
    "AB": [1, 2, 3, 4, 5],
    "ABC": [2, 3, 4, 5, 6],
    "B": [3, 4, 5, 6, 7],
})


np.random.seed(123456)

midx_rows = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]], names=['rows-char', 'rows-color'])
midx_cols = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]], names=['cols-char', 'cols-color'])
multi_df = pd.DataFrame(np.arange(0, 36).reshape(6, 6), index=midx_rows, columns=midx_cols)


def test_previous_sort_criteria_does_not_affect_later_sort_criteria():
    ctx = FrameContext(df)
    ctx.set_sort_criteria(sort_by_column_index=[0], sort_ascending=[True])
    index_after_first_sort = ctx.visible_frame.to_frame(ctx.visible_frame.region).index

    ctx.set_sort_criteria(sort_by_column_index=[0, 1], sort_ascending=[False, False])
    # assert to ensure test setup is correct
    index_in_between = ctx.visible_frame.to_frame(ctx.visible_frame.region).index
    assert list(index_after_first_sort) != list(index_in_between)

    ctx.set_sort_criteria(sort_by_column_index=[0], sort_ascending=[True])
    index_after_last_sort = ctx.visible_frame.to_frame(ctx.visible_frame.region).index
    assert list(index_after_first_sort) == list(index_after_last_sort)


def test_filter_is_respected():
    ctx = FrameContext(df, FilterCriteria.from_frame(df.filter(items=[1], axis='index')))
    table_structure = ctx.get_table_structure("")

    assert table_structure.rows_count == 1
    assert table_structure.org_rows_count == 5


def test_column_name_completion_variants():
    ctx = FrameContext(df2)

    assert ctx.get_column_name_completion_variants(source=df, is_synthetic_df=False) == [
        CompletionVariant(fq_type='builtins.str', value='col_0'),
        CompletionVariant(fq_type='builtins.str', value='col_1'),
        CompletionVariant(fq_type='builtins.str', value='col_2'),
        CompletionVariant(fq_type='builtins.str', value='col_3'),
        CompletionVariant(fq_type='builtins.str', value='col_4')
    ]

    assert ctx.get_column_name_completion_variants(source=df2, is_synthetic_df=False) == [
        CompletionVariant(fq_type='builtins.str', value='A'),
        CompletionVariant(fq_type='builtins.str', value='AB'),
        CompletionVariant(fq_type='builtins.str', value='ABC'),
        CompletionVariant(fq_type='builtins.str', value='B')
    ]

    assert ctx.get_column_name_completion_variants(source=None, is_synthetic_df=True) == [
        CompletionVariant(fq_type='builtins.str', value='A'),
        CompletionVariant(fq_type='builtins.str', value='AB'),
        CompletionVariant(fq_type='builtins.str', value='ABC'),
        CompletionVariant(fq_type='builtins.str', value='B')
    ]

    assert ctx.get_column_name_completion_variants(source=None, is_synthetic_df=False) == []


def test_column_name_completion_variants_with_tuples():
    ctx = FrameContext(multi_df)

    assert ctx.get_column_name_completion_variants(source=multi_df, is_synthetic_df=False) == [
        NestedCompletionVariant(
            fq_type='builtins.tuple',
            children=[
                CompletionVariant(fq_type='builtins.str', value='x'),
                CompletionVariant(fq_type='builtins.str', value='a'),
            ],
        ),
        NestedCompletionVariant(
            fq_type='builtins.tuple',
            children=[
                CompletionVariant(fq_type='builtins.str', value='x'),
                CompletionVariant(fq_type='builtins.str', value='b'),
            ],
        ),
        NestedCompletionVariant(
            fq_type='builtins.tuple',
            children=[
                CompletionVariant(fq_type='builtins.str', value='x'),
                CompletionVariant(fq_type='builtins.str', value='c'),
            ],
        ),
        NestedCompletionVariant(
            fq_type='builtins.tuple',
            children=[
                CompletionVariant(fq_type='builtins.str', value='y'),
                CompletionVariant(fq_type='builtins.str', value='a'),
            ],
        ),
        NestedCompletionVariant(
            fq_type='builtins.tuple',
            children=[
                CompletionVariant(fq_type='builtins.str', value='y'),
                CompletionVariant(fq_type='builtins.str', value='b'),
            ],
        ),
        NestedCompletionVariant(
            fq_type='builtins.tuple',
            children=[
                CompletionVariant(fq_type='builtins.str', value='y'),
                CompletionVariant(fq_type='builtins.str', value='c'),
            ],
        ),
    ]


def test_table_structure():
    ts = FrameContext(multi_df).get_table_structure(fingerprint="finger-1")
    assert ts == TableStructure(
        org_rows_count=len(multi_df.index),
        org_columns_count=len(multi_df.columns),
        rows_count=len(multi_df.index),
        columns_count=len(multi_df.columns),
        fingerprint="finger-1",
        column_info=TableStructureColumnInfo(
            legend=TableStructureLegend(index=['rows-char', 'rows-color'], column=['cols-char', 'cols-color']),
            columns=[
                TableStructureColumn(dtype='int64', labels=['x', 'a'], id=0),
                TableStructureColumn(dtype='int64', labels=['x', 'b'], id=1),
                TableStructureColumn(dtype='int64', labels=['x', 'c'], id=2),
                TableStructureColumn(dtype='int64', labels=['y', 'a'], id=3),
                TableStructureColumn(dtype='int64', labels=['y', 'b'], id=4),
                TableStructureColumn(dtype='int64', labels=['y', 'c'], id=5)
            ]
        )
    )


def test_table_structure_column_info_with_str_and_int_column_names():
    d = {"B": [1], "A": [1], 101: [1], 0: [1]}
    ts = FrameContext(pd.DataFrame.from_dict(d)).get_table_structure("finger-1")
    assert ts.column_info == TableStructureColumnInfo(
        legend=None,
        columns=[
            TableStructureColumn(dtype='int64', labels=['B'], id=0),
            TableStructureColumn(dtype='int64', labels=['A'], id=1),
            TableStructureColumn(dtype='int64', labels=['101'], id=2),
            TableStructureColumn(dtype='int64', labels=['0'], id=3)
        ])
