import numpy as np
import pandas as pd
from pandas import DataFrame

from cms_rendner_sdfv.base.types import TableStructureColumnInfo, TableStructureLegend, TableStructureColumn, \
    TableStructure
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


def test_column_name_completion_with_filter():
    ctx = FrameContext(df2, FilterCriteria.from_frame(df2[['A', 'B']]))
    completer = ctx.get_column_name_completer()

    assert completer.get_variants(df2, False, '') == [f'"{v}"' for v in df2.columns.values]
    assert completer.get_variants(df2, False, 'A') == ['"A"', '"AB"', '"ABC"']
    assert completer.get_variants(df2, False, 'AB') == ['"AB"', '"ABC"']
    assert completer.get_variants(df2, False, 'ABC') == ['"ABC"']
    assert completer.get_variants(df2, False, 'B') == ['"B"']
    assert completer.get_variants(df2, False, 'X') == []


def test_column_name_completion_with_prefix():
    ctx = FrameContext(df2)
    completer = ctx.get_column_name_completer()

    assert completer.get_variants(df2, False, '') == [f'"{v}"' for v in df2.columns.values]
    assert completer.get_variants(df2, False, 'A') == ['"A"', '"AB"', '"ABC"']
    assert completer.get_variants(df2, False, 'AB') == ['"AB"', '"ABC"']
    assert completer.get_variants(df2, False, 'ABC') == ['"ABC"']
    assert completer.get_variants(df2, False, 'B') == ['"B"']
    assert completer.get_variants(df2, False, 'X') == []


def test_column_name_completion_for_synthetic_identifier():
    ctx = FrameContext(df2)
    completer = ctx.get_column_name_completer()

    assert completer.get_variants(None, True, '') == [f'"{v}"' for v in df2.columns.values]
    assert completer.get_variants(None, True, 'A') == ['"A"', '"AB"', '"ABC"']
    assert completer.get_variants(None, True, 'AB') == ['"AB"', '"ABC"']
    assert completer.get_variants(None, True, 'ABC') == ['"ABC"']
    assert completer.get_variants(None, True, 'B') == ['"B"']
    assert completer.get_variants(None, True, 'X') == []

    assert completer.get_variants(None, False, 'A') == []


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
