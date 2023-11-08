import json
from itertools import chain
from typing import Any, Union

import pandas as pd

from cms_rendner_sdfv.base.table_source import AbstractTableSource
from cms_rendner_sdfv.base.types import CreateTableSourceConfig, CreateTableSourceFailure, TableFrame, \
    TableSourceKind
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


def test_create_for_dict_orient_columns():
    table_source = _create_table_source(df_dict)
    assert isinstance(table_source, TableSource)
    assert table_source.get_kind() == TableSourceKind.TABLE_SOURCE

    table_frame = _get_table_frame(table_source)
    assert table_frame.column_labels == [['col_0'], ['col_1']]
    assert table_frame.index_labels == [['0'], ['1'], ['2']]
    assert table_frame.legend is None
    assert list(map(lambda c: c.value, chain(*table_frame.cells))) == ['1', '4', '2', '5', '3', '6']


def test_create_for_dict_orient_index():
    table_source = _create_table_source(
        df_dict,
        CreateTableSourceConfig(data_source_transform_hint="DictKeysAsRows"),
    )
    assert isinstance(table_source, TableSource)
    assert table_source.get_kind() == TableSourceKind.TABLE_SOURCE

    table_frame = _get_table_frame(table_source)
    assert table_frame.column_labels == [['0'], ['1'], ['2']]
    assert table_frame.index_labels == [['col_0'], ['col_1']]
    assert table_frame.legend is None
    assert list(map(lambda c: c.value, chain(*table_frame.cells))) == ['1', '2', '3', '4', '5', '6']


# https://pandas.pydata.org/docs/reference/api/pandas.DataFrame.from_dict.html
# the passed dict has to be of the form {field : array-like} or {field : dict},
# otherwise an error is raised
# https://github.com/pandas-dev/pandas/issues/12387

def test_create_for_dict_with_scalars_raises_error_as_expected():
    failure = _create_table_source({"a": 1})

    assert isinstance(failure, CreateTableSourceFailure)
    assert failure.error_kind == "EVAL_EXCEPTION"
    assert failure.info == "ValueError('If using all scalar values, you must pass an index')"


def test_create_fails_on_unsupported_data_source():
    failure = _create_table_source([])

    assert isinstance(failure, CreateTableSourceFailure)
    assert failure.error_kind == "UNSUPPORTED_DATA_SOURCE_TYPE"
    assert failure.info == str(type([]))


def test_create_fails_on_invalid_fingerprint():
    failure = _create_table_source(
        df,
        CreateTableSourceConfig(previous_fingerprint=""),
    )

    assert isinstance(failure, CreateTableSourceFailure)
    assert failure.error_kind == "INVALID_FINGERPRINT"


def test_create_fails_on_failing_eval_filter():
    failure = _create_table_source(
        df,
        CreateTableSourceConfig(filter_eval_expr="xyz"),
    )

    assert isinstance(failure, CreateTableSourceFailure)
    assert failure.error_kind == "FILTER_FRAME_EVAL_FAILED"


def test_create_fails_on_wrong_filter_type():
    failure = _create_table_source(
        df,
        CreateTableSourceConfig(filter_eval_expr="{}"),
    )

    assert isinstance(failure, CreateTableSourceFailure)
    assert failure.error_kind == "FILTER_FRAME_OF_WRONG_TYPE"
    assert failure.info == str(type({}))


def test_create_with_filter():
    table_source = _create_table_source(
        df,
        CreateTableSourceConfig(filter_eval_expr="df.filter(items=[1, 2], axis='index')"),
    )
    assert isinstance(table_source, TableSource)
    assert table_source.get_kind() == TableSourceKind.TABLE_SOURCE

    table_frame = _get_table_frame(table_source)
    assert table_frame.column_labels == [['col_0'], ['col_1']]
    assert table_frame.index_labels == [['1'], ['2']]
    assert table_frame.legend is None
    assert list(map(lambda c: c.value, chain(*table_frame.cells))) == ['2', '5', '3', '6']


def test_filter_expr_can_resolve_local_variable():
    df2 = pd.DataFrame.from_dict({
        "col_0": [0, 1],
        "col_1": [2, 3],
    })

    # use "TableSourceFactory().create" instead of "_create_table_source"
    # otherwise, "df2" wouldn't be in th locals of callers frame
    table_source = TableSourceFactory().create(
        df2,
        CreateTableSourceConfig(
            filter_eval_expr="df2.filter(items=[1], axis='index')",
            filter_eval_expr_provide_frame=False,  # only required for the synthetic identifier "_df"
        ),
    )

    assert isinstance(table_source, TableSource)
    assert table_source.get_kind() == TableSourceKind.TABLE_SOURCE

    assert table_source.get_table_structure().columns_count == 2
    assert table_source.get_table_structure().org_columns_count == 2

    assert table_source.get_table_structure().rows_count == 1
    assert table_source.get_table_structure().org_rows_count == 2


def test_filter_expr_can_resolve_synthetic_identifier():
    df2 = pd.DataFrame.from_dict({
        "col_0": [0, 1],
        "col_1": [2, 3],
    })

    # use "TableSourceFactory().create" instead of "_create_table_source"
    # otherwise, "df2" wouldn't be in th locals of callers frame
    table_source = TableSourceFactory().create(
        df2,
        CreateTableSourceConfig(
            filter_eval_expr="_df.filter(items=[1], axis='index')",
            filter_eval_expr_provide_frame=True,  # required to provide the synthetic identifier "_df"
        ),
    )

    assert isinstance(table_source, TableSource)
    assert table_source.get_kind() == TableSourceKind.TABLE_SOURCE

    assert table_source.get_table_structure().columns_count == 2
    assert table_source.get_table_structure().org_columns_count == 2

    assert table_source.get_table_structure().rows_count == 1
    assert table_source.get_table_structure().org_rows_count == 2
