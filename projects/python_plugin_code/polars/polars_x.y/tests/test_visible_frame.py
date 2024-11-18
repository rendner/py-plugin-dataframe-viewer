import polars as pl

from cms_rendner_sdfv.base.constants import DESCRIBE_COL_MAX_STR_LEN
from cms_rendner_sdfv.polars.visible_frame import VisibleFrame

df_dict = {
    "col_0": [4, 4, 4, 1, 4],
    "col_1": ["1", "4", "4", "1", "2"],
}

df = pl.from_dict(df_dict)


def test_series_at():
    vf = VisibleFrame(source_frame=df, row_idx=None, col_idx=None)
    assert vf.series_at(0).name == "col_0"
    assert vf.series_at(1).name == "col_1"


def test_get_column_indices():
    vf = VisibleFrame(source_frame=df, row_idx=None, col_idx=None)

    actual = vf.get_column_indices()
    assert actual == list(range(len(df.columns)))


def test_get_column_statistics():
    vf = VisibleFrame(
        source_frame=pl.from_dict({
            'str': ['d', 'e', 'f'],
            'numeric': [1, 2, 3],
        }),
        row_idx=None,
        col_idx=None,
    )

    actual_str = vf.get_column_statistics(0)
    actual_numeric = vf.get_column_statistics(1)

    assert actual_str == {
        'count': '3',
        'max': 'f',
        'min': 'd',
        'null_count': '0',
    }

    assert actual_numeric == {
        '25%': '2.0',
        '50%': '2.0',
        '75%': '3.0',
        'count': '3.0',
        'max': '3.0',
        'mean': '2.0',
        'min': '1.0',
        'null_count': '0.0',
        'std': '1.0'
    }


def test_truncate_column_statistics():
    vf = VisibleFrame(
        source_frame=pl.from_dict({'A': ['ab' * DESCRIBE_COL_MAX_STR_LEN]}),
        row_idx=None,
        col_idx=None,
    )
    actual = vf.get_column_statistics(0)
    assert len(actual.get('min')) == DESCRIBE_COL_MAX_STR_LEN
    assert len(actual.get('max')) == DESCRIBE_COL_MAX_STR_LEN
