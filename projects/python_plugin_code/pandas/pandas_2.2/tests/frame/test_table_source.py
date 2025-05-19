import numpy as np
import pandas as pd

from cms_rendner_sdfv.base.types import ChunkDataResponse, Cell, TableStructure, TableStructureColumnInfo, \
    TableStructureColumn, TableInfo, TableSourceKind, Region, CellMeta, TextAlign
from cms_rendner_sdfv.pandas.frame.frame_context import FrameContext
from cms_rendner_sdfv.pandas.frame.table_source import TableSource

np.random.seed(123456)

midx_rows = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]], names=['rows-char', 'rows-color'])
midx_cols = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]], names=['cols-char', 'cols-color'])
multi_df = pd.DataFrame(np.arange(0, 36).reshape(6, 6), index=midx_rows, columns=midx_cols)


def test_compute_chunk_data():
    ts = TableSource(FrameContext(multi_df), "finger-1")
    actual = ts.compute_chunk_data(Region(0, 0, 2, 2))

    assert actual == ts.serialize(ChunkDataResponse(
        row_headers=[['x', 'a'], ['x', 'b']],
        cells=[
            [
                Cell(value='0', meta=CellMeta.min().pack()),
                Cell(value='1', meta=CellMeta.min().pack()),
            ],
            [
                Cell(value='6', meta=CellMeta(cmap_value=20000).pack()),
                Cell(value='7', meta=CellMeta(cmap_value=20000).pack()),
            ],
        ],
    ))


def test_compute_chunk_data_with_multiindex():
    midx = pd.MultiIndex.from_product([[("A", "B"), "y"], ["a", "b", "c"]])
    my_df = pd.DataFrame(np.arange(0, 36).reshape(6, 6), index=midx, columns=midx)
    ts = TableSource(FrameContext(my_df), "finger-1")
    actual = ts.compute_chunk_data(Region(0, 0, 2, 2))

    assert actual == ts.serialize(ChunkDataResponse(
        row_headers=[['(A, B)', 'a'], ['(A, B)', 'b']],
        cells=[
            [
                Cell(value='0', meta=CellMeta.min().pack()),
                Cell(value='1', meta=CellMeta.min().pack()),
            ],
            [
                Cell(value='6', meta=CellMeta(cmap_value=20000).pack()),
                Cell(value='7', meta=CellMeta(cmap_value=20000).pack()),
            ],
        ],
    ))


def test_table_info_with_different_column_types():
    my_df = pd.DataFrame.from_dict({
        'a': [1],
        'b': [1.0],
        'c': [1j],
        'd': ['a'],
        'e': [True],
        'f': [np.datetime64('2025-12-01')],
    })

    ts = TableSource(FrameContext(my_df), "finger-1")

    assert ts.get_info() == ts.serialize(
        TableInfo(
            kind=TableSourceKind.TABLE_SOURCE.name,
            structure=TableStructure(
                org_rows_count=len(my_df.index),
                org_columns_count=len(my_df.columns),
                rows_count=len(my_df.index),
                columns_count=len(my_df.columns),
                fingerprint="finger-1",
                column_info=TableStructureColumnInfo(
                    legend=None,
                    columns=[
                        TableStructureColumn(dtype='int64', labels=['a'], id=0, text_align=TextAlign.RIGHT),
                        TableStructureColumn(dtype='float64', labels=['b'], id=1, text_align=TextAlign.RIGHT),
                        TableStructureColumn(dtype='complex128', labels=['c'], id=2, text_align=TextAlign.RIGHT),
                        TableStructureColumn(dtype='string', labels=['d'], id=3),
                        TableStructureColumn(dtype='bool', labels=['e'], id=4),
                        TableStructureColumn(dtype='datetime64[ns]', labels=['f'], id=5),
                    ],
                )
            ),
        )
    )


def test_table_info_with_none_leveled_column_names():
    d = {
        ("A", "B"): [1, 2, 3],
        "B": [1, 2, 3],
        "A": [1, 2, 3],
        101: [4, 5, 6],
        0: [9, 9, 9],
    }
    my_df = pd.DataFrame.from_dict(d)
    ts = TableSource(FrameContext(my_df), "finger-1")
    assert ts.get_info() == ts.serialize(TableInfo(
        kind=TableSourceKind.TABLE_SOURCE.name,
        structure=TableStructure(
            org_rows_count=len(my_df.index),
            org_columns_count=len(my_df.columns),
            rows_count=len(my_df.index),
            columns_count=len(my_df.columns),
            fingerprint="finger-1",
            column_info=TableStructureColumnInfo(
                legend=None,
                columns=[
                    TableStructureColumn(dtype='int64', labels=['(A, B)'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['B'], id=1, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['A'], id=2, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['101'], id=3, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['0'], id=4, text_align=TextAlign.RIGHT),
                ],
            )
        ),
    ))


def test_table_info_with_leveled_column_names():
    midx = pd.MultiIndex.from_product([[("A", "B"), "y"], ["a", "b", "c"]])
    my_df = pd.DataFrame(np.arange(0, 36).reshape(6, 6), index=midx, columns=midx)
    ts = TableSource(FrameContext(my_df), "finger-1")
    assert ts.get_info() == ts.serialize(TableInfo(
        kind=TableSourceKind.TABLE_SOURCE.name,
        structure=TableStructure(
            org_rows_count=len(my_df.index),
            org_columns_count=len(my_df.columns),
            rows_count=len(my_df.index),
            columns_count=len(my_df.columns),
            fingerprint="finger-1",
            column_info=TableStructureColumnInfo(
                legend=None,
                columns=[
                    TableStructureColumn(dtype='int64', labels=['(A, B)', 'a'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['(A, B)', 'b'], id=1, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['(A, B)', 'c'], id=2, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['y', 'a'], id=3, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['y', 'b'], id=4, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['y', 'c'], id=5, text_align=TextAlign.RIGHT),
                ],
            )
        ),
    ))
