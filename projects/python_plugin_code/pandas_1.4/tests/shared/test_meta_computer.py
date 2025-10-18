import numpy as np
import pandas as pd

from cms_rendner_sdfv.base.types import CellMeta
from cms_rendner_sdfv.pandas.shared.pandas_table_source_context import MetaComputer


def test_with_values_including_nan():
    df = pd.DataFrame.from_dict({'a': [1, 2, np.nan]})

    mc = MetaComputer(df)

    actual = mc.compute_cell_meta(0, df.iat[0, 0])
    assert actual == CellMeta.min().pack()

    actual = mc.compute_cell_meta(0, df.iat[1, 0])
    assert actual == CellMeta.max().pack()

    actual = mc.compute_cell_meta(0, df.iat[2, 0])
    assert actual == CellMeta.nan().pack()


def test_with_nan():
    df = pd.DataFrame.from_dict({'a': [np.nan, float('nan')]})

    mc = MetaComputer(df)

    actual = mc.compute_cell_meta(0, df.iat[0, 0])
    assert actual == CellMeta.nan().pack()

    actual = mc.compute_cell_meta(0, df.iat[1, 0])
    assert actual == CellMeta.nan().pack()


def test_with_none():
    df = pd.DataFrame.from_dict({'a': [None]})

    mc = MetaComputer(df)

    actual = mc.compute_cell_meta(0, df.iat[0, 0])
    assert actual is None


def test_with_booleans():
    df = pd.DataFrame.from_dict({'a': [True]})

    mc = MetaComputer(df)

    actual = mc.compute_cell_meta(0, df.iat[0, 0])
    assert actual is None


def test_with_complex():
    df = pd.DataFrame.from_dict({'a': [2+3j]})

    mc = MetaComputer(df)

    actual = mc.compute_cell_meta(0, df.iat[0, 0])
    assert actual == CellMeta.min_max().pack()


def test_with_string():
    df = pd.DataFrame.from_dict({'a': ["123"]})

    mc = MetaComputer(df)

    actual = mc.compute_cell_meta(0, df.iat[0, 0])
    assert actual is None
