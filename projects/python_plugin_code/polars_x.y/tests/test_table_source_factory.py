from typing import Any

import polars as pl

from cms_rendner_sdfv.base.exceptions import TableSourceException, TableSourceErrorKind
from cms_rendner_sdfv.base.serialization import ResultJsonString, ExceptionJsonString
from cms_rendner_sdfv.base.temp import TEMP_VARS
from cms_rendner_sdfv.base.types import CreateTableSourceConfig, ChunkDataResponse, \
    TableSourceKind, Cell, TableInfo, TableStructure, TableStructureColumnInfo, \
    TableStructureColumn, Region, CellMeta, TextAlign
from cms_rendner_sdfv.polars.table_source import TableSource
from cms_rendner_sdfv.polars.table_source_factory import TableSourceFactory

df_dict = {
    "0": [0, 1, 2],
    "1": [3, 4, 5],
}

df = pl.from_dict(df_dict)


def _create_table_source(
        data_source: Any,
        config: CreateTableSourceConfig = None,
):
    return TableSourceFactory().create(
        "temp_var_slot_id-1",
        data_source,
        config if config is not None else CreateTableSourceConfig(),
    )


def get_created_table_source() -> TableSource:
    return TEMP_VARS['temp_var_slot_id-1']


def test_create_for_frame():
    table_source_info = _create_table_source(df)
    assert table_source_info == ResultJsonString.from_value(
        TableInfo(
            kind=TableSourceKind.TABLE_SOURCE.name,
            structure=TableStructure(
                org_rows_count=3,
                org_columns_count=2,
                rows_count=3,
                columns_count=2,
                column_info=TableStructureColumnInfo(
                    legend=None,
                    columns=[
                        TableStructureColumn(dtype="Int64", labels=["0"], id=0, text_align=TextAlign.RIGHT),
                        TableStructureColumn(dtype="Int64", labels=["1"], id=1, text_align=TextAlign.RIGHT),
                    ],
                ),
            )
        )
    )


def test_create_for_dict():
    table_source_info = _create_table_source(df_dict)

    expected_row_count = 3
    expected_col_count = 2

    assert table_source_info == ResultJsonString.from_value(
        TableInfo(
            kind=TableSourceKind.TABLE_SOURCE.name,
            structure=TableStructure(
                org_rows_count=expected_row_count,
                org_columns_count=expected_col_count,
                rows_count=expected_row_count,
                columns_count=expected_col_count,
                column_info=TableStructureColumnInfo(
                    legend=None,
                    columns=[
                        TableStructureColumn(id=0, dtype='Int64', labels=['0'], text_align=TextAlign.RIGHT),
                        TableStructureColumn(id=1, dtype='Int64', labels=['1'], text_align=TextAlign.RIGHT),
                    ],
                )
            ),
        )
    )

    table_source = get_created_table_source()

    assert table_source.compute_chunk_data(
        Region(0, 0, expected_row_count, expected_col_count),
    ) == ResultJsonString.from_value(
        ChunkDataResponse(
            cells=[
                [
                    Cell(value='0', meta=CellMeta.min().pack()),
                    Cell(value='3', meta=CellMeta.min().pack()),
                ],
                [
                    Cell(value='1', meta=CellMeta(cmap_value=50000).pack()),
                    Cell(value='4', meta=CellMeta(cmap_value=50000).pack()),
                ],
                [
                    Cell(value='2', meta=CellMeta.max().pack()),
                    Cell(value='5', meta=CellMeta.max().pack()),
                ]
            ],
        )
    )


def test_create_fails_on_unsupported_data_source():
    exc = TableSourceException(
        error_kind=TableSourceErrorKind.UNSUPPORTED_DATA_SOURCE_TYPE,
        info=str(type([])),
    )

    assert _create_table_source([]) == ExceptionJsonString.from_value(exc)


def test_create_fails_on_unsupported_data_source__for_lazy_frames():
    exc = TableSourceException(
        error_kind=TableSourceErrorKind.UNSUPPORTED_DATA_SOURCE_TYPE,
        info=str(type(pl.LazyFrame())),
    )

    assert _create_table_source(pl.LazyFrame()) == ExceptionJsonString.from_value(exc)

