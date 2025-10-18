from typing import Any

import pandas as pd

from cms_rendner_sdfv.base.exceptions import TableSourceException, TableSourceErrorKind
from cms_rendner_sdfv.base.serialization import ResultJsonString, ExceptionJsonString
from cms_rendner_sdfv.base.types import CreateTableSourceConfig, \
    TableSourceKind, TableInfo, TableStructure, TableStructureColumnInfo, \
    TableStructureColumn, TextAlign
from cms_rendner_sdfv.pandas.styler.patched_styler_factory import PatchedStylerFactory

df = pd.DataFrame.from_dict({
    "col_0": [1, 2, 3],
    "col_1": [4, 5, 6],
})


def _create_table_source(
        data_source: Any,
        config: CreateTableSourceConfig = None,
):
    return PatchedStylerFactory().create(
        "temp_var_slot_id-1",
        data_source,
        config if config is not None else CreateTableSourceConfig(),
    )


def test_create_for_styler():
    table_source_info = _create_table_source(df.style)
    assert table_source_info == ResultJsonString.from_value(
        TableInfo(
            kind=TableSourceKind.PATCHED_STYLER.name,
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


def test_create_fails_on_unsupported_data_source():
    exc = TableSourceException(
        error_kind=TableSourceErrorKind.UNSUPPORTED_DATA_SOURCE_TYPE,
        info=str(type({})),
    )

    assert _create_table_source({}) == ExceptionJsonString.from_value(exc)
