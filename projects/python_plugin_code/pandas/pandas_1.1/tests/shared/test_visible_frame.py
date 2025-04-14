import numpy as np
import pandas as pd

from cms_rendner_sdfv.base.constants import COL_STATISTIC_ENTRY_MAX_STR_LEN
from cms_rendner_sdfv.pandas.shared.value_formatter import ValueFormatter
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


def test_row_labels_at():
    vf = VisibleFrame(source_frame=df)
    assert vf.row_labels_at(0) == [0]
    assert vf.row_labels_at(1) == [1]

    mvf = VisibleFrame(source_frame=multi_df)
    assert mvf.row_labels_at(0) == ['x', 'a']
    assert mvf.row_labels_at(1) == ['x', 'b']


def test_to_source_frame_cell_coordinates():
    vf = VisibleFrame(source_frame=df)
    assert vf.to_source_frame_cell_coordinates(0, 0) == (0, 0)


def test_get_column_indices():
    vf = VisibleFrame(source_frame=multi_df)

    actual = vf.get_column_indices()
    assert actual == list(range(len(multi_df.columns)))


def test_get_column_statistics():
    vf = VisibleFrame(
        source_frame=pd.DataFrame.from_dict({
            'categorical': pd.Categorical(['d', 'e', 'f']),
            'numeric': [1, 2, 3],
        }))

    formatter = ValueFormatter()
    actual_categorical = vf.get_column_statistics(0, formatter)
    actual_numeric = vf.get_column_statistics(1, formatter)

    assert actual_categorical == {
        'count': '3',
        'unique': '3',
        'top': 'f', # in later pandas versions top is 'd' (maybe a bug in pandas 1.1)
        'freq': '1',
    }

    assert actual_numeric == {
        'count': '3.0',
        'mean': '2.0',
        'std': '1.0',
        'min': '1.0',
        '25%': '1.5',
        '50%': '2.0',
        '75%': '2.5',
        'max': '3.0',
    }


def test_truncate_column_statistics():
    vf = VisibleFrame(source_frame=pd.DataFrame.from_dict({'A': ['ab' * COL_STATISTIC_ENTRY_MAX_STR_LEN]}))
    actual = vf.get_column_statistics(0, ValueFormatter())
    assert len(actual.get('top')) == COL_STATISTIC_ENTRY_MAX_STR_LEN
