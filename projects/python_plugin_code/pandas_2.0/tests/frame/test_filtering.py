import pytest
from pandas import DataFrame

from cms_rendner_sdfv.base.types import ChunkDataResponse, Cell, CellMeta, FilterConfig, SortConfig, FilterResult, \
    TableStructure, TableStructureColumnInfo, TableStructureColumn, TextAlign
from cms_rendner_sdfv.pandas.frame.frame_context import FrameContext

df = DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


def test_combined_chunks_do_not_include_filtered_out_values():
    ctx = FrameContext(df)
    ctx.apply_filter(FilterConfig(expr="df.filter(items=[1, 3, 4], axis='index')"))

    actual_chunk_data = ctx.get_chunk_data_generator().generate_by_combining_chunks(rows_per_chunk=2, cols_per_chunk=2)
    assert actual_chunk_data == ChunkDataResponse(
        row_headers=[['1'], ['3'], ['4']],
        cells=[
            [
                Cell(value='1', meta=CellMeta(cmap_value=25000).pack()),
                Cell(value='6', meta=CellMeta(cmap_value=25000).pack()),
                Cell(value='11', meta=CellMeta(cmap_value=25000).pack()),
                Cell(value='16', meta=CellMeta(cmap_value=25000).pack()),
                Cell(value='21', meta=CellMeta(cmap_value=25000).pack()),
            ],
            [
                Cell(value='3', meta=CellMeta(cmap_value=75000).pack()),
                Cell(value='8', meta=CellMeta(cmap_value=75000).pack()),
                Cell(value='13', meta=CellMeta(cmap_value=75000).pack()),
                Cell(value='18', meta=CellMeta(cmap_value=75000).pack()),
                Cell(value='23', meta=CellMeta(cmap_value=75000).pack()),
            ],
            [
                Cell(value='4', meta=CellMeta.max().pack()),
                Cell(value='9', meta=CellMeta.max().pack()),
                Cell(value='14', meta=CellMeta.max().pack()),
                Cell(value='19', meta=CellMeta.max().pack()),
                Cell(value='24', meta=CellMeta.max().pack()),
            ]
        ],
    )


# https://pandas.pydata.org/docs/getting_started/intro_tutorials/03_subset_data.html


def test_filter_by_columns():
    ctx = FrameContext(df)
    result = ctx.apply_filter(FilterConfig(expr="df[['col_0', 'col_4']]"))

    assert result == FilterResult(
        sort=SortConfig(),
        table_structure=TableStructure(
            org_rows_count=5,
            org_columns_count=5,
            rows_count=5,
            columns_count=2,
            column_info=TableStructureColumnInfo(
                columns=[
                    TableStructureColumn(dtype='int64', labels=['col_0'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_4'], id=4, text_align=TextAlign.RIGHT),
                ],
                legend=None,
            ),
        ),
    )


def test_filter_by_rows():
    ctx = FrameContext(df)
    result = ctx.apply_filter(FilterConfig(expr="df[df['col_0'] < 3]"))

    expected_row_idx = [0, 1, 2]
    actual_row_idx = ctx.visible_frame.to_frame(ctx.visible_frame.region).index
    assert list(actual_row_idx) == expected_row_idx

    assert result == FilterResult(
        sort=SortConfig(),
        table_structure=TableStructure(
            org_rows_count=5,
            org_columns_count=5,
            rows_count=3,
            columns_count=5,
            column_info=TableStructureColumnInfo(
                columns=[
                    TableStructureColumn(dtype='int64', labels=['col_0'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_1'], id=1, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_2'], id=2, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_3'], id=3, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_4'], id=4, text_align=TextAlign.RIGHT),
                ],
                legend=None,
            ),
        ),
    )


def test_filter_by_rows_and_columns():
    ctx = FrameContext(df)
    result = ctx.apply_filter(FilterConfig(expr="df.loc[df['col_0'] < 3, ['col_0', 'col_4']]"))

    expected_row_idx = [0, 1, 2]
    actual_row_idx = ctx.visible_frame.to_frame(ctx.visible_frame.region).index
    assert list(actual_row_idx) == expected_row_idx

    assert result == FilterResult(
        sort=SortConfig(),
        table_structure=TableStructure(
            org_rows_count=5,
            org_columns_count=5,
            rows_count=3,
            columns_count=2,
            column_info=TableStructureColumnInfo(
                columns=[
                    TableStructureColumn(dtype='int64', labels=['col_0'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_4'], id=4, text_align=TextAlign.RIGHT),
                ],
                legend=None,
            ),
        ),
    )


def test_filter_with_empty_data_frame():
    ctx = FrameContext(df)
    result = ctx.apply_filter(FilterConfig(expr="DataFrame()"))

    assert result == FilterResult(
        sort=SortConfig(),
        table_structure=TableStructure(
            org_rows_count=5,
            org_columns_count=5,
            rows_count=0,
            columns_count=0,
            column_info=TableStructureColumnInfo(
                columns=[],
                legend=None,
            ),
        ),
    )


def test_filter_with_non_existing_rows_and_columns():
    ctx = FrameContext(df)
    result = ctx.apply_filter(FilterConfig(expr="DataFrame(index=[7, 8, 9], columns=['col_7', 'col_8', 'col_9'])"))

    assert result == FilterResult(
        sort=SortConfig(),
        table_structure=TableStructure(
            org_rows_count=5,
            org_columns_count=5,
            rows_count=0,
            columns_count=0,
            column_info=TableStructureColumnInfo(
                columns=[],
                legend=None,
            ),
        ),
    )


def test_filtered_frame_keeps_index_and_column_order():
    ctx = FrameContext(df)
    # The filter has the same cols and rows as df, but in reversed order.
    # Therefore, the filter doesn't filter out any row or col.
    ctx.apply_filter(FilterConfig(expr="df.copy().iloc[::-1, ::-1]"))

    actual = ctx.visible_frame.to_frame(ctx.visible_frame.region)
    assert list(actual.index) == list(df.index)
    assert list(actual.columns) == list(df.columns)


def test_filter_with_df_filter():
    ctx = FrameContext(df)
    result = ctx.apply_filter(FilterConfig(expr="df.filter(items=['col_0', 'col_4'])"))

    assert result == FilterResult(
        sort=SortConfig(),
        table_structure=TableStructure(
            org_rows_count=5,
            org_columns_count=5,
            rows_count=5,
            columns_count=2,
            column_info=TableStructureColumnInfo(
                columns=[
                    TableStructureColumn(dtype='int64', labels=['col_0'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_4'], id=4, text_align=TextAlign.RIGHT),
                ],
                legend=None,
            ),
        ),
    )


def test_filter_with_empty_str():
    ctx = FrameContext(df)
    ctx.apply_filter(FilterConfig(expr=""))

    actual = ctx.visible_frame.to_frame(ctx.visible_frame.region)
    assert list(actual.index) == list(df.index)
    assert list(actual.columns) == list(df.columns)


def test_filter_with_sorting_updates_sorting():
    ctx = FrameContext(df)
    ctx.apply_sort(
        SortConfig(
            column_idx=[
                df.columns.get_loc("col_0"),
                df.columns.get_loc("col_2"),
            ],
            ascending=[True, True],
        )
    )
    result = ctx.apply_filter(FilterConfig(expr="df.filter(items=['col_0', 'col_4'])"))

    assert result == FilterResult(
        sort=SortConfig(column_idx=[0], ascending=[True]),
        table_structure=TableStructure(
            org_rows_count=5,
            org_columns_count=5,
            rows_count=5,
            columns_count=2,
            column_info=TableStructureColumnInfo(
                columns=[
                    TableStructureColumn(dtype='int64', labels=['col_0'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_4'], id=4, text_align=TextAlign.RIGHT),
                ],
                legend=None,
            ),
        ),
    )


def test_filter_fails_and_reverts_to_original_state():
    ctx = FrameContext(df)
    ctx.apply_sort(
        SortConfig(
            column_idx=[
                df.columns.get_loc("col_0"),
                df.columns.get_loc("col_2"),
            ],
            ascending=[True, True],
        )
    )

    # force an internal error
    ctx.apply_sort = None

    msg = "'NoneType' object is not callable"
    with pytest.raises(TypeError, match=msg):
        ctx.apply_filter(FilterConfig(expr="df.filter(items=['col_0', 'col_4'])"))

    # expect the unfiltered state
    assert ctx.get_table_structure() == TableStructure(
        org_rows_count=5,
        org_columns_count=5,
        rows_count=5,
        columns_count=5,
        column_info=TableStructureColumnInfo(
            columns=[
                    TableStructureColumn(dtype='int64', labels=['col_0'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_1'], id=1, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_2'], id=2, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_3'], id=3, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_4'], id=4, text_align=TextAlign.RIGHT),
                ],
            legend=None,
        ),
    )
