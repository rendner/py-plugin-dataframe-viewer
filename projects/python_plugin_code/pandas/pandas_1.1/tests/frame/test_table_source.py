from pandas import DataFrame

from cms_rendner_sdfv.base.types import TableFrame, TableFrameColumn, TableFrameCell
from cms_rendner_sdfv.pandas.frame.frame_context import FrameContext
from cms_rendner_sdfv.pandas.frame.table_source import TableSource
from tests.helpers.asserts.assert_table_frames import assert_table_frames

df = DataFrame.from_dict({
        0: [0, 1, 2],
        1: [3, 4, 5],
    })


def test_compute_chunk_table_frame():
    actual = TableSource(FrameContext(df), "finger-1").compute_chunk_table_frame(0, 0, 2, 2)
    assert_table_frames(
        actual,
        TableFrame(
            index_labels=[['0'], ['1']],
            columns=[
                TableFrameColumn(dtype='int64', labels=['0']),
                TableFrameColumn(dtype='int64', labels=['1']),
            ],
            cells=[
                [TableFrameCell(value='0'), TableFrameCell(value='3')],
                [TableFrameCell(value='1'), TableFrameCell(value='4')],
            ],
        ))


def test_table_structure():
    ts = TableSource(FrameContext(df), "finger-1").get_table_structure()
    assert ts.org_rows_count == len(df.index)
    assert ts.org_columns_count == len(df.columns)
    assert ts.rows_count == len(df.index)
    assert ts.columns_count == len(df.columns)
    assert ts.fingerprint == "finger-1"


def test_jsonify():
    json = TableSource(FrameContext(df), "").jsonify({"a": 12, "b": (True, False)})
    assert json == '{"a": 12, "b": [true, false]}'


def test_get_org_indices_of_visible_columns():
    ts = TableSource(FrameContext(df), "")

    num_cols = 3
    actual = ts.get_org_indices_of_visible_columns(0, num_cols)
    assert actual == list(range(0, num_cols))
