import numpy as np
from pandas import DataFrame, MultiIndex

from cms_rendner_sdfv.pandas.frame.frame_context import FrameContext
from cms_rendner_sdfv.pandas.frame.table_source import TableSource

np.random.seed(123456)

midx = MultiIndex.from_product([["x", "y"], ["a", "b", "c"]])
df = DataFrame(np.random.randn(6, 6), index=midx, columns=midx)
df.index.names = ["lev0", "lev1"]


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
