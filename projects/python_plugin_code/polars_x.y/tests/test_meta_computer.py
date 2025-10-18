import polars as pl

from cms_rendner_sdfv.base.types import CellMeta
from cms_rendner_sdfv.polars.frame_context import MetaComputer


def test_with_values_including_nan():
    df = pl.from_dict({'a': [1, 2, float("nan")]})

    mc = MetaComputer(df)

    actual = mc.compute_cell_meta(0, df.item(0, 0))
    assert actual == CellMeta.min().pack()

    actual = mc.compute_cell_meta(0, df.item(1, 0))
    assert actual == CellMeta.max().pack()

    actual = mc.compute_cell_meta(0, df.item(2, 0))
    assert actual == CellMeta.nan().pack()


def test_with_nan():
    df = pl.from_dict({'a': [float("nan")]})

    mc = MetaComputer(df)

    actual = mc.compute_cell_meta(0, df.item(0, 0))
    assert actual == CellMeta.nan().pack()


def test_with_none():
    df = pl.from_dict({'a': [None]})

    mc = MetaComputer(df)

    actual = mc.compute_cell_meta(0, df.item(0, 0))
    assert actual is None


def test_with_booleans():
    df = pl.from_dict({'a': [True]})

    mc = MetaComputer(df)

    actual = mc.compute_cell_meta(0, df.item(0, 0))
    assert actual is None


def test_with_complex():
    df = pl.from_dict({'a': [2+3j]})

    mc = MetaComputer(df)

    actual = mc.compute_cell_meta(0, df.item(0, 0))
    assert actual is None
    # dtype for columns with complex numbers in polars ise 'object' and therefore not numeric
    assert not df.get_column('a').dtype.is_numeric()


def test_with_string():
    df = pl.from_dict({'a': ["123"]})

    mc = MetaComputer(df)

    actual = mc.compute_cell_meta(0, df.item(0, 0))
    assert actual is None
