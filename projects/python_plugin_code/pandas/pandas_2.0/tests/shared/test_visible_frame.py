import numpy as np
import pandas as pd

from cms_rendner_sdfv.pandas.shared.visible_frame import VisibleFrame

df_dict = {
    "col_0": [4, 4, 4, 1, 4],
    "col_1": ["1", "4", "4", "1", "2"],
}

df = pd.DataFrame.from_dict(df_dict)

midx_rows = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]], names=['rows-char', 'rows-color'])
midx_cols = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]], names=['cols-char', 'cols-color'])
multi_df = pd.DataFrame(np.arange(0, 36).reshape(6, 6), index=midx_rows, columns=midx_cols)


def test_index_names():
    vf = VisibleFrame(source_frame=df)
    assert vf.index_names == [None]

    mvf = VisibleFrame(source_frame=multi_df)
    assert mvf.index_names == ['rows-char', 'rows-color']


def test_column_names():
    vf = VisibleFrame(source_frame=df)
    assert vf.column_names == [None]

    mvf = VisibleFrame(source_frame=multi_df)
    assert mvf.column_names == ['cols-char', 'cols-color']


def test_cell_value_at():
    vf = VisibleFrame(source_frame=df)
    assert vf.cell_value_at(0, 0) == 4
    assert vf.cell_value_at(0, 1) == "1"


def test_column_at():
    vf = VisibleFrame(source_frame=df)
    assert vf.column_at(0) == "col_0"
    assert vf.column_at(1) == "col_1"

    mvf = VisibleFrame(source_frame=multi_df)
    assert mvf.column_at(0) == ('x', 'a')
    assert mvf.column_at(1) == ('x', 'b')


def test_index_at():
    vf = VisibleFrame(source_frame=df)
    assert vf.index_at(0) == 0
    assert vf.index_at(1) == 1

    mvf = VisibleFrame(source_frame=multi_df)
    assert mvf.index_at(0) == ('x', 'a')
    assert mvf.index_at(1) == ('x', 'b')


def test_get_column_info():
    vf = VisibleFrame(source_frame=df)

    info = vf.get_column_info(0)
    assert str(info.dtype) == "int64"

    info = vf.get_column_info(1)
    assert str(info.dtype) == "object"


def test_to_source_frame_cell_coordinates():
    vf = VisibleFrame(source_frame=df)
    assert vf.to_source_frame_cell_coordinates(0, 0) == (0, 0)


def test_get_column_indices():
    vf = VisibleFrame(source_frame=multi_df)

    actual = vf.get_column_indices()
    assert actual == list(range(len(multi_df.columns)))
