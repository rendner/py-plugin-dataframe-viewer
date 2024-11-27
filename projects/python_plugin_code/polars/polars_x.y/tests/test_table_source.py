import polars as pl

from cms_rendner_sdfv.base.types import TableStructureColumnInfo, TableStructureColumn, \
    TableStructure, TableSourceKind, TableInfo, TableFrame, TableFrameCell
from cms_rendner_sdfv.polars.frame_context import FrameContext
from cms_rendner_sdfv.polars.table_source import TableSource

df = pl.DataFrame(
    {
        "0": [0, 1, 2],
        "1": [3, 4, 5],
    }
)


def test_compute_chunk_table_frame():
    ts = TableSource(FrameContext(df), "finger-1")
    actual = ts.compute_chunk_table_frame(0, 0, 2, 2)

    assert actual == ts.serialize(TableFrame(
        index_labels=None,
        cells=[
            [TableFrameCell(value='0', css=None), TableFrameCell(value='3', css=None)],
            [TableFrameCell(value='1', css=None), TableFrameCell(value='4', css=None)],
        ],
    ))


def test_table_info():
    ts = TableSource(FrameContext(df), "finger-1")

    assert ts.get_info() == ts.serialize(
        TableInfo(
            kind=TableSourceKind.TABLE_SOURCE.name,
            structure=TableStructure(
                org_rows_count=df.height,
                org_columns_count=df.width,
                rows_count=df.height,
                columns_count=df.width,
                fingerprint="finger-1",
                column_info=TableStructureColumnInfo(
                    legend=None,
                    columns=[
                        TableStructureColumn(dtype='Int64', labels=['0'], id=0),
                        TableStructureColumn(dtype='Int64', labels=['1'], id=1)
                    ],
                )
            ),
        )
    )

