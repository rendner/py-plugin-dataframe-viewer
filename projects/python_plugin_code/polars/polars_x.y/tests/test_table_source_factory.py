import json
from typing import Any, Union

import polars as pl

from cms_rendner_sdfv.base.table_source import AbstractTableSource
from cms_rendner_sdfv.base.types import CreateTableSourceConfig, CreateTableSourceFailure, TableFrame, TableFrameColumn, \
    TableSourceKind, TableFrameCell
from cms_rendner_sdfv.polars.table_source import TableSource
from cms_rendner_sdfv.polars.table_source_factory import TableSourceFactory
from tests.helpers.asserts.assert_table_frames import assert_table_frames

df_dict = {
    "0": [0, 1, 2],
    "1": [3, 4, 5],
}

df = pl.from_dict(df_dict)


def _create_table_source(
        data_source: Any,
        config: CreateTableSourceConfig = None,
) -> Union[AbstractTableSource, CreateTableSourceFailure]:
    result = TableSourceFactory().create(data_source, config if config is not None else CreateTableSourceConfig())
    if isinstance(result, str):
        return CreateTableSourceFailure(**json.loads(result))
    return result


def _get_table_frame(table_source: AbstractTableSource) -> TableFrame:
    s = table_source.get_table_structure()
    return table_source.compute_chunk_table_frame(0, 0, s.rows_count, s.columns_count)


def test_create_for_frame():
    table_source = _create_table_source(df)
    assert isinstance(table_source, TableSource)
    assert table_source.get_kind() == TableSourceKind.TABLE_SOURCE


def test_create_for_dict():
    table_source = _create_table_source(df_dict)
    assert isinstance(table_source, TableSource)
    assert table_source.get_kind() == TableSourceKind.TABLE_SOURCE

    table_frame = _get_table_frame(table_source)
    assert_table_frames(
        table_frame,
        TableFrame(
            columns=[
                TableFrameColumn(dtype='Int64', labels=['0']),
                TableFrameColumn(dtype='Int64', labels=['1']),
            ],
            index_labels=None,
            cells=[
                [TableFrameCell(value='0'), TableFrameCell(value='3')],
                [TableFrameCell(value='1'), TableFrameCell(value='4')],
                [TableFrameCell(value='2'), TableFrameCell(value='5')]
            ],
        )
    )


def test_create_fails_on_unsupported_data_source():
    failure = _create_table_source([])

    assert isinstance(failure, CreateTableSourceFailure)
    assert failure.error_kind == "UNSUPPORTED_DATA_SOURCE_TYPE"
    assert failure.info == str(type([]))


def test_create_fails_on_unsupported_data_source__for_lazy_frames():
    failure = _create_table_source(pl.LazyFrame())

    assert isinstance(failure, CreateTableSourceFailure)
    assert failure.error_kind == "UNSUPPORTED_DATA_SOURCE_TYPE"
    assert failure.info == str(type(pl.LazyFrame()))


def test_create_fails_on_invalid_fingerprint():
    failure = _create_table_source(
        df,
        CreateTableSourceConfig(previous_fingerprint=""),
    )

    assert isinstance(failure, CreateTableSourceFailure)
    assert failure.error_kind == "INVALID_FINGERPRINT"
