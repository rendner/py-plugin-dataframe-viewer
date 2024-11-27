import polars as pl

from cms_rendner_sdfv.base.types import TableStructureColumnInfo, TableStructureColumn, \
    TableStructure
from cms_rendner_sdfv.polars.frame_context import FrameContext

df = pl.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [1, 2, 3, 4, 5],
    "col_2": [2, 3, 4, 5, 6],
    "col_3": [3, 4, 5, 6, 7],
    "col_4": [4, 5, 6, 7, 8],
})

df2 = pl.from_dict({
    "A": [0, 1, 2, 3, 4],
    "AB": [1, 2, 3, 4, 5],
    "ABC": [2, 3, 4, 5, 6],
    "B": [3, 4, 5, 6, 7],
})


def test_previous_sort_criteria_does_not_affect_later_sort_criteria():
    ctx = FrameContext(df)
    ctx.set_sort_criteria(sort_by_column_index=[0], sort_ascending=[True])
    index_after_first_sort = list(ctx.visible_frame.row_idx_iter())

    ctx.set_sort_criteria(sort_by_column_index=[0, 1], sort_ascending=[False, False])
    # assert to ensure test setup is correct
    index_in_between = list(ctx.visible_frame.row_idx_iter())
    assert index_after_first_sort != index_in_between

    ctx.set_sort_criteria(sort_by_column_index=[0], sort_ascending=[True])
    index_after_last_sort = list(ctx.visible_frame.row_idx_iter())
    assert index_after_first_sort == index_after_last_sort


def test_filter_is_respected():
    ctx = FrameContext(df, df.filter(pl.col('col_0').is_between(1, 2)))
    table_structure = ctx.get_table_structure("")

    assert table_structure.rows_count == 2
    assert table_structure.org_rows_count == 5


def test_column_name_completion_with_filter():
    ctx = FrameContext(df2, df2.drop(["A", "B"]))
    completer = ctx.get_column_name_completer()

    assert completer.get_variants(df2, False, '') == [f'"{v}"' for v in df2.columns]
    assert completer.get_variants(df2, False, 'A') == ['"A"', '"AB"', '"ABC"']
    assert completer.get_variants(df2, False, 'AB') == ['"AB"', '"ABC"']
    assert completer.get_variants(df2, False, 'ABC') == ['"ABC"']
    assert completer.get_variants(df2, False, 'B') == ['"B"']
    assert completer.get_variants(df2, False, 'X') == []


def test_column_name_completion_with_prefix():
    ctx = FrameContext(df2)
    completer = ctx.get_column_name_completer()

    assert completer.get_variants(df2, False, '') == [f'"{v}"' for v in df2.columns]
    assert completer.get_variants(df2, False, 'A') == ['"A"', '"AB"', '"ABC"']
    assert completer.get_variants(df2, False, 'AB') == ['"AB"', '"ABC"']
    assert completer.get_variants(df2, False, 'ABC') == ['"ABC"']
    assert completer.get_variants(df2, False, 'B') == ['"B"']
    assert completer.get_variants(df2, False, 'X') == []


def test_column_name_completion_for_synthetic_identifier():
    ctx = FrameContext(df2)
    completer = ctx.get_column_name_completer()

    assert completer.get_variants(None, True, '') == [f'"{v}"' for v in df2.columns]
    assert completer.get_variants(None, True, 'A') == ['"A"', '"AB"', '"ABC"']
    assert completer.get_variants(None, True, 'AB') == ['"AB"', '"ABC"']
    assert completer.get_variants(None, True, 'ABC') == ['"ABC"']
    assert completer.get_variants(None, True, 'B') == ['"B"']
    assert completer.get_variants(None, True, 'X') == []

    assert completer.get_variants(None, False, 'A') == []


def test_table_structure():
    ts = FrameContext(df).get_table_structure(fingerprint="finger-1")
    assert ts == TableStructure(
        org_rows_count=df.height,
        org_columns_count=df.width,
        rows_count=df.height,
        columns_count=df.width,
        fingerprint="finger-1",
        column_info=TableStructureColumnInfo(
            legend=None,
            columns=[
                TableStructureColumn(dtype='Int64', labels=['col_0'], id=0),
                TableStructureColumn(dtype='Int64', labels=['col_1'], id=1),
                TableStructureColumn(dtype='Int64', labels=['col_2'], id=2),
                TableStructureColumn(dtype='Int64', labels=['col_3'], id=3),
                TableStructureColumn(dtype='Int64', labels=['col_4'], id=4),
            ]
        )
    )
