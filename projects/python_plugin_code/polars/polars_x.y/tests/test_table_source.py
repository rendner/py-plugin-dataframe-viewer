import polars as pl

from cms_rendner_sdfv.base.types import TableStructureColumnInfo, TableStructureColumn
from cms_rendner_sdfv.polars.frame_context import FrameContext
from cms_rendner_sdfv.polars.table_source import TableSource

df = pl.DataFrame(
    {
        "0": [0, 1, 2],
        "1": [3, 4, 5],
    }
)


def test_table_structure():
    ts = TableSource(FrameContext(df), "finger-1").get_table_structure()
    assert ts.org_rows_count == df.height
    assert ts.org_columns_count == df.width
    assert ts.rows_count == df.height
    assert ts.columns_count == df.width
    assert ts.fingerprint == "finger-1"
    assert ts.column_info == TableStructureColumnInfo(
        legend=None,
        columns=[
            TableStructureColumn(dtype='Int64', labels=['0'], id=0),
            TableStructureColumn(dtype='Int64', labels=['1'], id=1)
        ])


def test_jsonify():
    json = TableSource(FrameContext(df), "").jsonify({"a": 12, "b": (True, False)})
    assert json == '{"a": 12, "b": [true, false]}'
