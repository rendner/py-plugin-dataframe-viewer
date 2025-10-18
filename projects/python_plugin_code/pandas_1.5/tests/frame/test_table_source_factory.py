from typing import Any

import pandas as pd

from cms_rendner_sdfv.base.exceptions import TableSourceException, TableSourceErrorKind
from cms_rendner_sdfv.base.serialization import ResultJsonString, ExceptionJsonString
from cms_rendner_sdfv.base.temp import TEMP_VARS
from cms_rendner_sdfv.base.types import CreateTableSourceConfig, ChunkDataResponse, \
    TableSourceKind, Cell, TableStructureColumn, TableStructureLegend, TableInfo, \
    TableStructure, TableStructureColumnInfo, Region, CellMeta, TextAlign
from cms_rendner_sdfv.pandas.frame.table_source import TableSource
from cms_rendner_sdfv.pandas.frame.table_source_factory import TableSourceFactory

df_dict = {
    "col_0": [1, 2, 3],
    "col_1": [4, 5, 6],
}

df = pd.DataFrame.from_dict(df_dict)


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
                        TableStructureColumn(dtype="int64", labels=["col_0"], id=0, text_align=TextAlign.RIGHT),
                        TableStructureColumn(dtype="int64", labels=["col_1"], id=1, text_align=TextAlign.RIGHT),
                    ],
                ),
            )
        )
    )


def test_create_for_dict_orient_tight():
    tight_dict = {
        'index': [('a', 'b'), ('a', 'c')],
        'columns': [('x', 1), ('y', 2)],
        'data': [[1, 3], [2, 4]],
        'index_names': ['n1', 'n2'],
        'column_names': ['z1', 'z2'],
    }
    table_source_info = _create_table_source(tight_dict)

    assert table_source_info == ResultJsonString.from_value(
        TableInfo(
            kind=TableSourceKind.TABLE_SOURCE.name,
            structure=TableStructure(
                org_rows_count=2,
                org_columns_count=2,
                rows_count=2,
                columns_count=2,
                column_info=TableStructureColumnInfo(
                    legend=TableStructureLegend(index=['n1', 'n2'], column=['z1', 'z2']),
                    columns=[
                        TableStructureColumn(id=0, dtype='int64', labels=['x', '1'], text_align=TextAlign.RIGHT),
                        TableStructureColumn(id=1, dtype='int64', labels=['y', '2'], text_align=TextAlign.RIGHT),
                    ],
                )
            ),
        )
    )

    table_source = get_created_table_source()

    assert table_source.compute_chunk_data(
        Region(0, 0, 2, 2)
    ) == ResultJsonString.from_value(
        ChunkDataResponse(
            row_headers=[['a', 'b'], ['a', 'c']],
            cells=[
                [
                    Cell(value='1', meta=CellMeta.min().pack()),
                    Cell(value='3', meta=CellMeta.min().pack()),
                ],
                [
                    Cell(value='2', meta=CellMeta.max().pack()),
                    Cell(value='4', meta=CellMeta.max().pack()),
                ]
            ],
        )
    )


def test_create_for_dict_orient_columns():
    table_source_info = _create_table_source(df_dict)

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
                        TableStructureColumn(id=0, dtype='int64', labels=['col_0'], text_align=TextAlign.RIGHT),
                        TableStructureColumn(id=1, dtype='int64', labels=['col_1'], text_align=TextAlign.RIGHT),
                    ],
                )
            ),
        )
    )

    table_source = get_created_table_source()

    assert table_source.compute_chunk_data(
        Region(0, 0, 3, 2)
    ) == ResultJsonString.from_value(
        ChunkDataResponse(
            row_headers=[["0"], ["1"], ["2"]],
            cells=[
                [
                    Cell(value='1', meta=CellMeta.min().pack()),
                    Cell(value='4', meta=CellMeta.min().pack()),
                ],
                [
                    Cell(value='2', meta=CellMeta(cmap_value=50000).pack()),
                    Cell(value='5', meta=CellMeta(cmap_value=50000).pack()),
                ],
                [
                    Cell(value='3', meta=CellMeta.max().pack()),
                    Cell(value='6', meta=CellMeta.max().pack()),
                ]
            ],
        )
    )


def test_create_for_dict_orient_index():
    table_source_info = _create_table_source(
        df_dict,
        CreateTableSourceConfig(data_source_transform_hint="DictKeysAsRows"),
    )

    assert table_source_info == ResultJsonString.from_value(
        TableInfo(
            kind=TableSourceKind.TABLE_SOURCE.name,
            structure=TableStructure(
                org_rows_count=2,
                org_columns_count=3,
                rows_count=2,
                columns_count=3,
                column_info=TableStructureColumnInfo(
                    legend=None,
                    columns=[
                        TableStructureColumn(id=0, dtype='int64', labels=['0'], text_align=TextAlign.RIGHT),
                        TableStructureColumn(id=1, dtype='int64', labels=['1'], text_align=TextAlign.RIGHT),
                        TableStructureColumn(id=2, dtype='int64', labels=['2'], text_align=TextAlign.RIGHT),
                    ],
                )
            ),
        )
    )

    table_source = get_created_table_source()

    assert table_source.compute_chunk_data(
        Region(0, 0, 2, 3)
    ) == ResultJsonString.from_value(
        ChunkDataResponse(
            row_headers=[['col_0'], ['col_1']],
            cells=[
                [
                    Cell(value='1', meta=CellMeta.min().pack()),
                    Cell(value='2', meta=CellMeta.min().pack()),
                    Cell(value='3', meta=CellMeta.min().pack()),
                ],
                [
                    Cell(value='4', meta=CellMeta.max().pack()),
                    Cell(value='5', meta=CellMeta.max().pack()),
                    Cell(value='6', meta=CellMeta.max().pack()),
                ],
            ],
        )
    )


def test_create_for_dict_with_scalars_should_not_raise():
    d = {"a": 1}

    table_source_info = _create_table_source(d, CreateTableSourceConfig(data_source_transform_hint="DictKeysAsRows"))

    assert table_source_info == ResultJsonString.from_value(
        TableInfo(
            kind=TableSourceKind.TABLE_SOURCE.name,
            structure=TableStructure(
                org_rows_count=1,
                org_columns_count=1,
                rows_count=1,
                columns_count=1,
                column_info=TableStructureColumnInfo(
                    legend=None,
                    columns=[
                        TableStructureColumn(id=0, dtype='int64', labels=['0'], text_align=TextAlign.RIGHT),
                    ],
                )
            ),
        )
    )

    table_source = get_created_table_source()

    assert table_source.compute_chunk_data(
        Region(0, 0, 1, 1)
    ) == ResultJsonString.from_value(
        ChunkDataResponse(
            row_headers=[['a']],
            cells=[
                [
                    Cell(value='1', meta=CellMeta.min_max().pack()),
                ],
            ],
        )
    )


def test_create_fails_on_unsupported_data_source():
    exc = TableSourceException(
        error_kind=TableSourceErrorKind.UNSUPPORTED_DATA_SOURCE_TYPE,
        info=str(type([])),
    )

    assert _create_table_source([]) == ExceptionJsonString.from_value(exc)
