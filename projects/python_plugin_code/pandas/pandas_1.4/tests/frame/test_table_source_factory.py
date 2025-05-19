import json
from typing import Any, Union

import pandas as pd

from cms_rendner_sdfv.base.table_source import AbstractTableSource
from cms_rendner_sdfv.base.types import CreateTableSourceConfig, CreateTableSourceFailure, ChunkDataResponse, \
    TableSourceKind, Cell, CreateTableSourceErrorKind, TableStructureColumn, TableStructureLegend, TableInfo, \
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
) -> Union[AbstractTableSource, CreateTableSourceFailure]:
    result = TableSourceFactory().create(data_source, config if config is not None else CreateTableSourceConfig())
    if isinstance(result, str):
        d = json.loads(result)
        return CreateTableSourceFailure(
            error_kind=CreateTableSourceErrorKind[d['error_kind']],
            info=d['info'],
        )
    return result


def test_create_for_frame():
    table_source = _create_table_source(df)
    assert isinstance(table_source, TableSource)


def test_create_for_dict_orient_tight():
    tight_dict = {
        'index': [('a', 'b'), ('a', 'c')],
        'columns': [('x', 1), ('y', 2)],
        'data': [[1, 3], [2, 4]],
        'index_names': ['n1', 'n2'],
        'column_names': ['z1', 'z2'],
    }
    table_source = _create_table_source(tight_dict)
    assert isinstance(table_source, TableSource)

    expected_row_count = 2
    expected_col_count = 2

    assert table_source.get_info() == table_source.serialize(
        TableInfo(
            kind=TableSourceKind.TABLE_SOURCE.name,
            structure=TableStructure(
                org_rows_count=expected_row_count,
                org_columns_count=expected_col_count,
                rows_count=expected_row_count,
                columns_count=expected_col_count,
                fingerprint=table_source._fingerprint,
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

    assert table_source.compute_chunk_data(
        Region(0, 0, expected_row_count, expected_col_count)
    ) == table_source.serialize(
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
    table_source = _create_table_source(df_dict)
    assert isinstance(table_source, TableSource)

    expected_row_count = 3
    expected_col_count = 2

    assert table_source.get_info() == table_source.serialize(
        TableInfo(
            kind=TableSourceKind.TABLE_SOURCE.name,
            structure=TableStructure(
                org_rows_count=expected_row_count,
                org_columns_count=expected_col_count,
                rows_count=expected_row_count,
                columns_count=expected_col_count,
                fingerprint=table_source._fingerprint,
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

    assert table_source.compute_chunk_data(
        Region(0, 0, expected_row_count, expected_col_count)
    ) == table_source.serialize(
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
    table_source = _create_table_source(
        df_dict,
        CreateTableSourceConfig(data_source_transform_hint="DictKeysAsRows"),
    )
    assert isinstance(table_source, TableSource)

    expected_row_count = 2
    expected_col_count = 3

    assert table_source.get_info() == table_source.serialize(
        TableInfo(
            kind=TableSourceKind.TABLE_SOURCE.name,
            structure=TableStructure(
                org_rows_count=expected_row_count,
                org_columns_count=expected_col_count,
                rows_count=expected_row_count,
                columns_count=expected_col_count,
                fingerprint=table_source._fingerprint,
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

    assert table_source.compute_chunk_data(
        Region(0, 0, expected_row_count, expected_col_count)
    ) == table_source.serialize(
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
    table_source = _create_table_source(d)
    assert isinstance(table_source, TableSource)

    table_source = _create_table_source(d, CreateTableSourceConfig(data_source_transform_hint="DictKeysAsRows"))
    assert isinstance(table_source, TableSource)


def test_create_fails_on_unsupported_data_source():
    failure = _create_table_source([])

    assert isinstance(failure, CreateTableSourceFailure)
    assert failure.error_kind == CreateTableSourceErrorKind.UNSUPPORTED_DATA_SOURCE_TYPE
    assert failure.info == str(type([]))


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
        CreateTableSourceConfig(filter_eval_expr="df.filter(items=[1, 2], axis='index')"),
    )
    assert isinstance(table_source, TableSource)

    assert table_source.get_info() == table_source.serialize(
        TableInfo(
            kind=TableSourceKind.TABLE_SOURCE.name,
            structure=TableStructure(
                org_rows_count=3,
                org_columns_count=2,
                rows_count=2,
                columns_count=2,
                fingerprint=table_source._fingerprint,
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

    assert table_source.compute_chunk_data(
        Region(0, 0, 2, 2)
    ) == table_source.serialize(
        ChunkDataResponse(
            row_headers=[['1'], ['2']],
            cells=[
                [
                    Cell(value='2', meta=CellMeta(cmap_value=50000).pack()),
                    Cell(value='5', meta=CellMeta(cmap_value=50000).pack()),
                ],
                [
                    Cell(value='3', meta=CellMeta.max().pack()),
                    Cell(value='6', meta=CellMeta.max().pack()),
                ],
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
        df2,
        CreateTableSourceConfig(
            filter_eval_expr="df2.filter(items=[1], axis='index')",
            filter_eval_expr_provide_frame=False,  # only required for the synthetic identifier "_df"
        ),
    )

    assert isinstance(table_source, TableSource)
    assert table_source.get_info() == table_source.serialize(TableInfo(
        kind=TableSourceKind.TABLE_SOURCE.name,
        structure=TableStructure(
            org_rows_count=2,
            org_columns_count=2,
            rows_count=1,
            columns_count=2,
            fingerprint=table_source._fingerprint,
            column_info=TableStructureColumnInfo(
                legend=None,
                columns=[
                    TableStructureColumn(id=0, dtype='int64', labels=['col_0'], text_align=TextAlign.RIGHT),
                    TableStructureColumn(id=1, dtype='int64', labels=['col_1'], text_align=TextAlign.RIGHT),
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
        df2,
        CreateTableSourceConfig(
            filter_eval_expr="_df.filter(items=[1], axis='index')",
            filter_eval_expr_provide_frame=True,  # required to provide the synthetic identifier "_df"
        ),
    )

    assert isinstance(table_source, TableSource)
    assert table_source.get_info() == table_source.serialize(TableInfo(
        kind=TableSourceKind.TABLE_SOURCE.name,
        structure=TableStructure(
            org_rows_count=2,
            org_columns_count=2,
            rows_count=1,
            columns_count=2,
            fingerprint=table_source._fingerprint,
            column_info=TableStructureColumnInfo(
                legend=None,
                columns=[
                    TableStructureColumn(id=0, dtype='int64', labels=['col_0'], text_align=TextAlign.RIGHT),
                    TableStructureColumn(id=1, dtype='int64', labels=['col_1'], text_align=TextAlign.RIGHT),
                ],
            )
        )
    ))
