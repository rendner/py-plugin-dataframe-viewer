import json
from typing import Any, Union

import pandas as pd

from cms_rendner_sdfv.base.table_source import AbstractTableSource
from cms_rendner_sdfv.base.types import CreateTableSourceConfig, CreateTableSourceFailure, ChunkData, \
    TableSourceKind, Cell, CreateTableSourceErrorKind, TableInfo, TableStructure, TableStructureColumnInfo, \
    TableStructureColumn
from cms_rendner_sdfv.pandas.styler.patched_styler import PatchedStyler
from cms_rendner_sdfv.pandas.styler.table_source_factory import TableSourceFactory

df = pd.DataFrame.from_dict({
    "col_0": [1, 2, 3],
    "col_1": [4, 5, 6],
})


def _create_table_source(
        data_source: Any,
        config: CreateTableSourceConfig = None,
) -> Union[AbstractTableSource, CreateTableSourceFailure]:
    result = TableSourceFactory().create(data_source, config if config is not None else CreateTableSourceConfig())
    if isinstance(result, str):
        d = json.loads(result)
        return CreateTableSourceFailure(
            error_kind=CreateTableSourceErrorKind[d['error_kind']],
            info=d['info'],
        )
    return result


def test_create_for_styler():
    table_source = _create_table_source(df.style)
    assert isinstance(table_source, PatchedStyler)
    assert table_source.get_info() == table_source.serialize(
        TableInfo(
            kind=TableSourceKind.PATCHED_STYLER.name,
            structure=TableStructure(
                org_rows_count=3,
                org_columns_count=2,
                rows_count=3,
                columns_count=2,
                fingerprint=table_source._fingerprint,
                column_info=TableStructureColumnInfo(
                    legend=None,
                    columns=[
                        TableStructureColumn(dtype="int64", labels=["col_0"], id=0),
                        TableStructureColumn(dtype="int64", labels=["col_1"], id=1),
                    ],
                ),
            )
        )
    )


def test_create_fails_on_unsupported_data_source():
    result = _create_table_source({})

    assert isinstance(result, CreateTableSourceFailure)
    assert result.error_kind == CreateTableSourceErrorKind.UNSUPPORTED_DATA_SOURCE_TYPE
    assert result.info == str(type({}))


def test_create_fails_on_invalid_fingerprint():
    failure = _create_table_source(
        df.style,
        CreateTableSourceConfig(previous_fingerprint=""),
    )

    assert isinstance(failure, CreateTableSourceFailure)
    assert failure.error_kind == CreateTableSourceErrorKind.INVALID_FINGERPRINT


def test_create_fails_on_failing_eval_filter():
    failure = _create_table_source(
        df.style,
        CreateTableSourceConfig(filter_eval_expr="xyz"),
    )

    assert isinstance(failure, CreateTableSourceFailure)
    assert failure.error_kind == CreateTableSourceErrorKind.FILTER_FRAME_EVAL_FAILED


def test_create_fails_on_wrong_filter_type():
    failure = _create_table_source(
        df.style,
        CreateTableSourceConfig(filter_eval_expr="df.style"),
    )

    assert isinstance(failure, CreateTableSourceFailure)
    assert failure.error_kind == CreateTableSourceErrorKind.FILTER_FRAME_OF_WRONG_TYPE
    assert failure.info == str(type(df.style))


def test_create_with_filter():
    table_source = _create_table_source(
        df.style,
        CreateTableSourceConfig(filter_eval_expr="df.filter(items=[1, 2], axis='index')"),
    )
    assert isinstance(table_source, PatchedStyler)
    assert table_source.get_info() == table_source.serialize(
        TableInfo(
            kind=TableSourceKind.PATCHED_STYLER.name,
            structure=TableStructure(
                org_rows_count=3,
                org_columns_count=2,
                rows_count=2,
                columns_count=2,
                fingerprint=table_source._fingerprint,
                column_info=TableStructureColumnInfo(
                    legend=None,
                    columns=[
                        TableStructureColumn(id=0, dtype='int64', labels=['col_0']),
                        TableStructureColumn(id=1, dtype='int64', labels=['col_1']),
                    ],
                )
            ),
        )
    )

    assert table_source.compute_chunk_data(
        0,
        0,
        2,
        2,
    ) == table_source.serialize(
        ChunkData(
            index_labels=[['1'], ['2']],
            cells=[
                [Cell(value='2'), Cell(value='5')],
                [Cell(value='3'), Cell(value='6')],
            ],
        )
    )


def test_filter_expr_can_resolve_local_variable():
    df2 = pd.DataFrame.from_dict({
        "col_0": [0, 1],
        "col_1": [2, 3],
    })

    # use "TableSourceFactory().create" instead of "_create_table_source"
    # otherwise, "df2" wouldn't be in th locals of callers frame
    table_source = TableSourceFactory().create(
        df2.style,
        CreateTableSourceConfig(
            filter_eval_expr="df2.filter(items=[1], axis='index')",
            filter_eval_expr_provide_frame=False,  # only required for the synthetic identifier "_df"
        ),
    )

    assert isinstance(table_source, PatchedStyler)
    assert table_source.get_info() == table_source.serialize(TableInfo(
        kind=TableSourceKind.PATCHED_STYLER.name,
        structure=TableStructure(
            org_rows_count=2,
            org_columns_count=2,
            rows_count=1,
            columns_count=2,
            fingerprint=table_source._fingerprint,
            column_info=TableStructureColumnInfo(
                legend=None,
                columns=[
                    TableStructureColumn(id=0, dtype='int64', labels=['col_0']),
                    TableStructureColumn(id=1, dtype='int64', labels=['col_1']),
                ],
            )
        )
    ))


def test_filter_expr_can_resolve_synthetic_identifier():
    df2 = pd.DataFrame.from_dict({
        "col_0": [0, 1],
        "col_1": [2, 3],
    })

    # use "TableSourceFactory().create" instead of "_create_table_source"
    # otherwise, "df2" wouldn't be in th locals of callers frame
    table_source = TableSourceFactory().create(
        df2.style,
        CreateTableSourceConfig(
            filter_eval_expr="_df.filter(items=[1], axis='index')",
            filter_eval_expr_provide_frame=True,  # required to provide the synthetic identifier "_df"
        ),
    )

    assert isinstance(table_source, PatchedStyler)
    assert table_source.get_info() == table_source.serialize(TableInfo(
        kind=TableSourceKind.PATCHED_STYLER.name,
        structure=TableStructure(
            org_rows_count=2,
            org_columns_count=2,
            rows_count=1,
            columns_count=2,
            fingerprint=table_source._fingerprint,
            column_info=TableStructureColumnInfo(
                legend=None,
                columns=[
                    TableStructureColumn(id=0, dtype='int64', labels=['col_0']),
                    TableStructureColumn(id=1, dtype='int64', labels=['col_1']),
                ],
            )
        )
    ))
