from typing import List

import polars as pl

from cms_rendner_sdfv.base.types import ChunkData, Cell
from cms_rendner_sdfv.polars.frame_context import FrameContext

df = pl.DataFrame({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


def extract_actual_column_names(ctx: FrameContext) -> List[str]:
    chunk = ctx.visible_frame.get_chunk()
    return [chunk.series_at(c).name for c in range(chunk.region.cols)]


def test_combined_chunks_do_not_include_filtered_out_values():
    filter_frame = df.select(pl.col('col_1')).filter(pl.col('col_1').is_between(1, 7))
    ctx = FrameContext(df, filter_frame)

    actual_chunk_data = ctx.get_chunk_data_generator().generate_by_combining_chunks(rows_per_chunk=1, cols_per_chunk=1)
    assert actual_chunk_data == ChunkData(
        index_labels=None,
        cells=[
            [Cell(value='5')],
            [Cell(value='6')],
            [Cell(value='7')],
        ],
    )


def test_filter_by_columns():
    filter_frame = df.select([pl.col('col_1'), pl.col('col_4')])
    ctx = FrameContext(df, filter_frame)

    expected_columns = filter_frame.columns
    actual_columns = extract_actual_column_names(ctx)
    assert list(actual_columns) == list(expected_columns)

    expected_org_indices = [1, 4]
    actual_org_indices = ctx.visible_frame.get_column_indices()
    assert actual_org_indices == expected_org_indices


def test_filter_with_empty_filter():
    filter_frame = pl.DataFrame()
    ctx = FrameContext(df, filter_frame)

    ts = ctx.get_table_structure('')
    assert ts.rows_count == 0
    assert ts.columns_count == 0

    actual_org_indices = ctx.visible_frame.get_column_indices()
    assert actual_org_indices == []
