import json
from typing import Any, Union

import polars as pl

from cms_rendner_sdfv.base.table_source import AbstractTableSource
from cms_rendner_sdfv.base.types import CreateTableSourceConfig, CreateTableSourceFailure, TableFrame, \
    TableSourceKind, TableFrameCell, CreateTableSourceErrorKind
from cms_rendner_sdfv.polars.table_source import TableSource
from cms_rendner_sdfv.polars.table_source_factory import TableSourceFactory

df_dict = {
    "0": [0, 1, 2],
    "1": [3, 4, 5],
}

df = pl.from_dict(df_dict)


def _convert_failure_str(failure: str) -> CreateTableSourceFailure:
    d = json.loads(failure)
    return CreateTableSourceFailure(
        error_kind=CreateTableSourceErrorKind[d['error_kind']],
        info=d['info'],
    )


def _create_table_source(
        data_source: Any,
        config: CreateTableSourceConfig = None,
) -> Union[AbstractTableSource, CreateTableSourceFailure]:
    result = TableSourceFactory().create(data_source, config if config is not None else CreateTableSourceConfig())
    if isinstance(result, str):
        return _convert_failure_str(result)
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
    assert table_frame == TableFrame(
        index_labels=None,
        cells=[
            [TableFrameCell(value='0'), TableFrameCell(value='3')],
            [TableFrameCell(value='1'), TableFrameCell(value='4')],
            [TableFrameCell(value='2'), TableFrameCell(value='5')]
        ],
    )


def test_create_fails_on_unsupported_data_source():
    failure = _create_table_source([])

    assert isinstance(failure, CreateTableSourceFailure)
    assert failure.error_kind == CreateTableSourceErrorKind.UNSUPPORTED_DATA_SOURCE_TYPE
    assert failure.info == str(type([]))


def test_create_fails_on_unsupported_data_source__for_lazy_frames():
    failure = _create_table_source(pl.LazyFrame())

    assert isinstance(failure, CreateTableSourceFailure)
    assert failure.error_kind == CreateTableSourceErrorKind.UNSUPPORTED_DATA_SOURCE_TYPE
    assert failure.info == str(type(pl.LazyFrame()))


def test_create_fails_on_invalid_fingerprint():
    failure = _create_table_source(
        df,
        CreateTableSourceConfig(previous_fingerprint=""),
    )

    assert isinstance(failure, CreateTableSourceFailure)
    assert failure.error_kind == CreateTableSourceErrorKind.INVALID_FINGERPRINT


def test_create_fails_on_failing_eval_filter():
    failure = _create_table_source(
        df,
        CreateTableSourceConfig(filter_eval_expr="xyz"),
    )

    assert isinstance(failure, CreateTableSourceFailure)
    assert failure.error_kind == CreateTableSourceErrorKind.FILTER_FRAME_EVAL_FAILED


def test_create_fails_on_wrong_filter_type():
    failure = _create_table_source(
        df,
        CreateTableSourceConfig(filter_eval_expr="{}"),
    )

    assert isinstance(failure, CreateTableSourceFailure)
    assert failure.error_kind == CreateTableSourceErrorKind.FILTER_FRAME_OF_WRONG_TYPE
    assert failure.info == str(type({}))


def test_create_with_filter():
    table_source = _create_table_source(
        df,
        CreateTableSourceConfig(filter_eval_expr="df.filter(pl.col('0').is_between(1, 3))"),
    )
    assert isinstance(table_source, TableSource)
    assert table_source.get_kind() == TableSourceKind.TABLE_SOURCE

    table_frame = _get_table_frame(table_source)
    assert table_frame == TableFrame(
        index_labels=None,
        cells=[
            [TableFrameCell(value='1'), TableFrameCell(value='4')],
            [TableFrameCell(value='2'), TableFrameCell(value='5')],
        ],
    )


def test_filter_expr_can_resolve_local_variable():
    # used in the filter query
    col = pl.col('0')

    # use "TableSourceFactory().create" instead of "_create_table_source"
    # otherwise, "col" wouldn't be in th locals of callers frame
    table_source = TableSourceFactory().create(
        df,
        CreateTableSourceConfig(filter_eval_expr="df.filter(col.is_between(1, 3))"),
    )

    assert isinstance(table_source, TableSource)
    assert table_source.get_kind() == TableSourceKind.TABLE_SOURCE

    assert table_source.get_table_structure().columns_count == 2
    assert table_source.get_table_structure().org_columns_count == 2

    assert table_source.get_table_structure().rows_count == 2
    assert table_source.get_table_structure().org_rows_count == 3


def test_filter_expr_can_resolve_synthetic_identifier():
    table_source = _create_table_source(
        df,
        CreateTableSourceConfig(
            filter_eval_expr="_df.filter(pl.col('0').is_between(1, 3))",
            filter_eval_expr_provide_frame=True, # required to provide the synthetic identifier "_df"
        ),
    )

    assert isinstance(table_source, TableSource)
    assert table_source.get_kind() == TableSourceKind.TABLE_SOURCE

    assert table_source.get_table_structure().columns_count == 2
    assert table_source.get_table_structure().org_columns_count == 2

    assert table_source.get_table_structure().rows_count == 2
    assert table_source.get_table_structure().org_rows_count == 3


def test_can_filter_out_columns():
    # used in the filter query
    df2 = pl.from_dict({"0": [0, 1]})

    # use "TableSourceFactory().create" instead of "_create_table_source"
    # otherwise, "df2" wouldn't be in th locals of callers frame
    table_source = TableSourceFactory().create(
        df,
        CreateTableSourceConfig(filter_eval_expr="df2"),
    )

    assert isinstance(table_source, TableSource)
    assert table_source.get_kind() == TableSourceKind.TABLE_SOURCE

    assert table_source.get_table_structure().columns_count == 1
    assert table_source.get_table_structure().org_columns_count == 2


def test_non_matching_filter_returns_empty_table_source():
    # used in the filter query
    df2 = pl.from_dict({"42": [0, 1]})

    # use "TableSourceFactory().create" instead of "_create_table_source"
    # otherwise, "df2" wouldn't be in th locals of callers frame
    table_source = TableSourceFactory().create(
        df,
        CreateTableSourceConfig(filter_eval_expr="df2"),
    )

    assert isinstance(table_source, TableSource)
    assert table_source.get_kind() == TableSourceKind.TABLE_SOURCE

    assert table_source.get_table_structure().columns_count == 0
    assert table_source.get_table_structure().org_columns_count == 2
