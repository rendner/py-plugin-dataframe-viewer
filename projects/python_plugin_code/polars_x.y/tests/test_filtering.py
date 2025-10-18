from typing import List

import polars as pl
import pytest

from cms_rendner_sdfv.base.types import ChunkDataResponse, Cell, CellMeta, FilterConfig, FilterResult, SortConfig, \
    TableStructure, TableStructureColumnInfo, TableStructureColumn, TextAlign
from cms_rendner_sdfv.polars.frame_context import FrameContext

df = pl.DataFrame({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


def extract_actual_column_names(ctx: FrameContext) -> List[str]:
    return [ctx.visible_frame.series_at(c).name for c in range(ctx.visible_frame.region.cols)]


def test_combined_chunks_do_not_include_filtered_out_values():
    ctx = FrameContext(df)
    ctx.apply_filter(FilterConfig(expr="df.select(pl.col('col_1')).filter(pl.col('col_1').is_between(1, 7))"))

    actual_chunk_data = ctx.get_chunk_data_generator().generate_by_combining_chunks(rows_per_chunk=1, cols_per_chunk=1)
    assert actual_chunk_data == ChunkDataResponse(
        cells=[
            [Cell(value='5', meta=CellMeta.min().pack())],
            [Cell(value='6', meta=CellMeta(cmap_value=25000).pack())],
            [Cell(value='7', meta=CellMeta(cmap_value=50000).pack())],
        ],
    )


def test_filter_by_columns():
    ctx = FrameContext(df)
    ctx.apply_filter(FilterConfig(expr="df.select([pl.col('col_1'), pl.col('col_4')])"))

    expected_columns = ['col_1', 'col_4']
    actual_columns = extract_actual_column_names(ctx)
    assert list(actual_columns) == list(expected_columns)

    expected_org_indices = [1, 4]
    actual_org_indices = ctx.visible_frame.get_column_indices()
    assert actual_org_indices == expected_org_indices


def test_filter_with_empty_filter():
    ctx = FrameContext(df)
    result = ctx.apply_filter(FilterConfig(expr="pl.DataFrame()"))

    assert result.table_structure == TableStructure(
        org_rows_count=5,
        org_columns_count=5,
        rows_count=0,
        columns_count=0,
        column_info=TableStructureColumnInfo(
            columns=[],
            legend=None,
        ),
    )


def test_filter_expr_can_resolve_local_variable():
    # used in the filter query
    col = pl.col('col_0')

    ctx = FrameContext(df)
    result = ctx.apply_filter(FilterConfig(expr="df.select(col).filter(col.is_between(1, 2))"))

    assert result.table_structure == TableStructure(
        org_rows_count=5,
        org_columns_count=5,
        rows_count=2,
        columns_count=1,
        column_info=TableStructureColumnInfo(
            legend=None,
            columns=[
                TableStructureColumn(id=0, dtype='Int64', labels=['col_0'], text_align=TextAlign.RIGHT),
            ],
        )
    )

    actual_chunk_data = ctx.get_chunk_data_generator().generate_by_combining_chunks(2, 2)
    assert actual_chunk_data == ChunkDataResponse(
        cells=[
            [Cell(value='1', meta=CellMeta(cmap_value=25000).pack())],
            [Cell(value='2', meta=CellMeta(cmap_value=50000).pack())],
        ],
    )


def test_filter_expr_can_resolve_synthetic_identifier():
    ctx = FrameContext(df)
    result = ctx.apply_filter(FilterConfig(
        expr="_df.select(pl.col('col_0')).filter(pl.col('col_0').is_between(1, 2))",
        synthetic_identifier_used=True,  # required to provide the synthetic identifier "_df"
    ))

    assert result.table_structure == TableStructure(
        org_rows_count=5,
        org_columns_count=5,
        rows_count=2,
        columns_count=1,
        column_info=TableStructureColumnInfo(
            legend=None,
            columns=[
                TableStructureColumn(id=0, dtype='Int64', labels=['col_0'], text_align=TextAlign.RIGHT),
            ],
        )
    )

    actual_chunk_data = ctx.get_chunk_data_generator().generate_by_combining_chunks(2, 2)
    assert actual_chunk_data == ChunkDataResponse(
        cells=[
            [Cell(value='1', meta=CellMeta(cmap_value=25000).pack())],
            [Cell(value='2', meta=CellMeta(cmap_value=50000).pack())],
        ],
    )


def test_can_filter_out_columns():
    ctx = FrameContext(df)
    result = ctx.apply_filter(FilterConfig(expr="pl.from_dict({'col_0': [0, 1]})"))

    assert result.table_structure == TableStructure(
        org_rows_count=5,
        org_columns_count=5,
        rows_count=2,
        columns_count=1,
        column_info=TableStructureColumnInfo(
            legend=None,
            columns=[
                TableStructureColumn(id=0, dtype='Int64', labels=['col_0'], text_align=TextAlign.RIGHT),
            ],
        )
    )

    actual_chunk_data = ctx.get_chunk_data_generator().generate_by_combining_chunks(2, 2)
    assert actual_chunk_data == ChunkDataResponse(
        cells=[
            [Cell(value='0', meta=CellMeta.min().pack())],
            [Cell(value='1', meta=CellMeta(cmap_value=25000).pack())],
        ],
    )


def test_non_matching_filter_returns_empty_table_source():
    ctx = FrameContext(df)
    result = ctx.apply_filter(FilterConfig(expr="pl.from_dict({'4': [0, 1]})"))

    assert result.table_structure == TableStructure(
        org_rows_count=5,
        org_columns_count=5,
        rows_count=0,
        columns_count=0,
        column_info=TableStructureColumnInfo(
            columns=[],
            legend=None,
        ),
    )


def test_filter_with_sorting_updates_sorting():
    ctx = FrameContext(df)
    ctx.apply_sort(
        SortConfig(
            column_idx=[0, 2],
            ascending=[True, True],
        )
    )
    result = ctx.apply_filter(FilterConfig(expr="df.select([pl.col('col_0'), pl.col('col_4')])"))

    assert result == FilterResult(
        sort=SortConfig(column_idx=[0], ascending=[True]),
        table_structure=TableStructure(
            org_rows_count=5,
            org_columns_count=5,
            rows_count=5,
            columns_count=2,
            column_info=TableStructureColumnInfo(
                columns=[
                    TableStructureColumn(dtype='Int64', labels=['col_0'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='Int64', labels=['col_4'], id=4, text_align=TextAlign.RIGHT),
                ],
                legend=None,
            ),
        ),
    )


def test_filter_fails_and_reverts_to_original_state():
    ctx = FrameContext(df)
    ctx.apply_sort(
        SortConfig(
            column_idx=[0, 2],
            ascending=[True, True],
        )
    )

    # force an internal error
    ctx.apply_sort = None

    msg = "'NoneType' object is not callable"
    with pytest.raises(TypeError, match=msg):
        ctx.apply_filter(FilterConfig(expr="df.select([pl.col('col_0'), pl.col('col_4')])"))

    # expect the unfiltered state
    assert ctx.get_table_structure() == TableStructure(
        org_rows_count=5,
        org_columns_count=5,
        rows_count=5,
        columns_count=5,
        column_info=TableStructureColumnInfo(
            columns=[
                    TableStructureColumn(dtype='Int64', labels=['col_0'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='Int64', labels=['col_1'], id=1, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='Int64', labels=['col_2'], id=2, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='Int64', labels=['col_3'], id=3, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='Int64', labels=['col_4'], id=4, text_align=TextAlign.RIGHT),
                ],
            legend=None,
        ),
    )
