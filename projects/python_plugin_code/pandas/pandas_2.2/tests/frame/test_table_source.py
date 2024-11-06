import numpy as np
import pandas as pd

from cms_rendner_sdfv.base.types import TableFrame, TableFrameColumn, TableFrameCell, TableStructureColumnInfo, \
    TableStructureLegend, TableStructureColumn, TableFrameLegend
from cms_rendner_sdfv.pandas.frame.frame_context import FrameContext
from cms_rendner_sdfv.pandas.frame.table_source import TableSource
from tests.helpers.asserts.assert_table_frames import assert_table_frames

np.random.seed(123456)

midx_rows = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]], names=['rows-char', 'rows-color'])
midx_cols = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]], names=['cols-char', 'cols-color'])
multi_df = pd.DataFrame(np.arange(0, 36).reshape(6, 6), index=midx_rows, columns=midx_cols)


def test_compute_chunk_table_frame():
    actual = TableSource(FrameContext(multi_df), "finger-1").compute_chunk_table_frame(0, 0, 2, 2)
    assert_table_frames(
        actual,
        TableFrame(
            legend=TableFrameLegend(index=['rows-char', 'rows-color'], column=['cols-char', 'cols-color']),
            index_labels=[['x', 'a'], ['x', 'b']],
            columns=[
                TableFrameColumn(dtype='int64', labels=['x', 'a'], describe=None),
                TableFrameColumn(dtype='int64', labels=['x', 'b'], describe=None),
            ],
            cells=[
                [TableFrameCell(value='0', css=None), TableFrameCell(value='1', css=None)],
                [TableFrameCell(value='6', css=None), TableFrameCell(value='7', css=None)],
            ],
        ))


def test_table_structure():
    ts = TableSource(FrameContext(multi_df), "finger-1").get_table_structure()
    assert ts.org_rows_count == len(multi_df.index)
    assert ts.org_columns_count == len(multi_df.columns)
    assert ts.rows_count == len(multi_df.index)
    assert ts.columns_count == len(multi_df.columns)
    assert ts.fingerprint == "finger-1"
    assert ts.column_info == TableStructureColumnInfo(
        legend=TableStructureLegend(index=['rows-char', 'rows-color'], column=['cols-char', 'cols-color']),
        columns=[
            TableStructureColumn(dtype='int64', labels=['x', 'a'], id=0),
            TableStructureColumn(dtype='int64', labels=['x', 'b'], id=1),
            TableStructureColumn(dtype='int64', labels=['x', 'c'], id=2),
            TableStructureColumn(dtype='int64', labels=['y', 'a'], id=3),
            TableStructureColumn(dtype='int64', labels=['y', 'b'], id=4),
            TableStructureColumn(dtype='int64', labels=['y', 'c'], id=5)
        ])


def test_table_structure_with_str_and_int_column_names():
    d = {"B": [1], "A": [1], 101: [1], 0: [1]}
    df = pd.DataFrame.from_dict(d)
    ts = TableSource(FrameContext(df), "finger-1").get_table_structure()
    assert ts.column_info == TableStructureColumnInfo(
        legend=None,
        columns=[
            TableStructureColumn(dtype='int64', labels=['B'],   id=0),
            TableStructureColumn(dtype='int64', labels=['A'],   id=1),
            TableStructureColumn(dtype='int64', labels=['101'], id=2),
            TableStructureColumn(dtype='int64', labels=['0'],   id=3)
        ])


def test_jsonify():
    json = TableSource(FrameContext(multi_df), "").jsonify({"a": 12, "b": (True, False)})
    assert json == '{"a": 12, "b": [true, false]}'


