import numpy as np
import pandas as pd

from cms_rendner_sdfv.pandas.shared.visible_frame import MappedVisibleFrame

df_dict = {
    "col_0": [4, 4, 4, 1, 4],
    "col_1": ["1", "4", "4", "1", "2"],
    "col_2": [1, 4, 4, 1, 2],
    "col_3": ["4", "4", "4", "1", "4"],
}

df = pd.DataFrame.from_dict(df_dict)

midx_rows = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]], names=['rows-char', 'rows-color'])
midx_cols = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]], names=['cols-char', 'cols-color'])
multi_df = pd.DataFrame(np.arange(0, 36).reshape(6, 6), index=midx_rows, columns=midx_cols)


def _create_visible_frame(df_type: str = 'default') -> MappedVisibleFrame:
    frame = df if df_type == 'default' else multi_df
    return MappedVisibleFrame(frame, visible_rows=[0, 4], visible_cols=[0, 3])


def test_index_names():
    vf = _create_visible_frame()
    assert vf.index_names == [None]

    mvf = _create_visible_frame('multi_index')
    assert mvf.index_names == ['rows-char', 'rows-color']


def test_column_names():
    vf = _create_visible_frame()
    assert vf.column_names == [None]

    mvf = _create_visible_frame('multi_index')
    assert mvf.column_names == ['cols-char', 'cols-color']


def test_cell_value_at():
    vf = _create_visible_frame()
    assert vf.cell_value_at(0, 0) == 4
    assert vf.cell_value_at(0, 1) == "4"


def test_column_at():
    vf = _create_visible_frame()
    assert vf.column_at(0) == "col_0"
    assert vf.column_at(1) == "col_3"

    mvf = _create_visible_frame('multi_index')
    assert mvf.index_at(0) == ('x', 'a')
    assert mvf.index_at(1) == ('y', 'b')


def test_index_at():
    vf = _create_visible_frame()
    assert vf.index_at(0) == 0
    assert vf.index_at(1) == 4

    mvf = _create_visible_frame('multi_index')
    assert mvf.index_at(0) == ('x', 'a')
    assert mvf.index_at(1) == ('y', 'b')


def test_to_source_frame_cell_coordinates():
    vf = _create_visible_frame()
    assert vf.to_source_frame_cell_coordinates(0, 0) == (0, 0)


def test_get_column_indices():
    vf = _create_visible_frame('multi_index')

    actual = vf.get_column_indices()
    assert actual == [0, 3]


def test_get_column_statistics():
    vf = _create_visible_frame()

    actual = vf.get_column_statistics(0)
    actual.pop('std', None)

    assert actual == {
        '25%': '4.0',
        '50%': '4.0',
        '75%': '4.0',
        'count': '2.0',
        'max': '4.0',
        'mean': '4.0',
        'min': '4.0'
    }
