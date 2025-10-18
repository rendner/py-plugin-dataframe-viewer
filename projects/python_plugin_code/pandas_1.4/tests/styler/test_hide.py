import numpy as np
import pandas as pd
import pytest

from tests.helpers.asserts.assert_style_func_parameters import assert_style_func_parameters
from tests.helpers.asserts.assert_patched_styler import assert_patched_styler

np.random.seed(123456)


df = pd.DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})

midx = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]])
multi_df = pd.DataFrame(np.random.randn(6, 6), index=midx, columns=midx)


@pytest.mark.parametrize("subset", [None, df.columns.tolist(), ["col_1", "col_4"]])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        df.shape  # single chunk
    ])
def test_hide_columns_chunked_with_style(subset, rows_per_chunk, cols_per_chunk):
    assert_patched_styler(
        df,
        # the max value is hidden if subset is != None ("col_4")
        # so the chunked result should also not include the highlighted value
        lambda styler: styler.highlight_max(axis=None).hide(axis="columns", subset=subset),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize("subset", [None, df.columns.tolist(), ["col_1", "col_4"]])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        df.shape  # single chunk
    ])
def test_hide_columns_chunked(subset, rows_per_chunk, cols_per_chunk):
    assert_patched_styler(
        df,
        lambda styler: styler.hide(axis="columns").hide(axis="columns", subset=subset),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize("subset", [None, df.index.tolist(), pd.IndexSlice[3:4]])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        df.shape  # single chunk
    ])
def test_hide_index_with_style(subset, rows_per_chunk, cols_per_chunk):
    assert_patched_styler(
        df,
        # the max value (located in "col_4") is hidden if subset is != None
        # so the chunked result should also not include the highlighted value
        lambda styler: styler.highlight_max(axis=None).hide(axis="index", subset=subset),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize("subset", [None, df.index.tolist(), pd.IndexSlice[3:4]])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        df.shape  # single chunk
    ])
def test_hide_index_chunked(subset, rows_per_chunk, cols_per_chunk):
    assert_patched_styler(
        df,
        lambda styler: styler.hide(axis="index").hide(axis="index", subset=subset),
        rows_per_chunk,
        cols_per_chunk
    )


def test_hide_index_and_columns_chunked():
    assert_patched_styler(
        df,
        lambda styler: styler.hide(axis="columns").hide(axis="index"),
        2,
        2
    )


def test_deprecated_hide_index():
    assert_patched_styler(
        df,
        lambda styler: styler.hide_index(),
        2,
        2
    )


def test_deprecated_hide_columns():
    assert_patched_styler(
        df,
        lambda styler: styler.hide_columns(),
        2,
        2
    )


@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        multi_df.shape  # single chunk
    ])
def test_multi_hide_index_retain_values(rows_per_chunk, cols_per_chunk):
    assert_patched_styler(
        multi_df,
        lambda styler: styler.hide(),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        multi_df.shape  # single chunk
    ])
def test_multi_hide_specific_rows_retain_index(rows_per_chunk, cols_per_chunk):
    assert_patched_styler(
        multi_df,
        lambda styler: styler.hide(subset=(slice(None), ["a", "c"])),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        multi_df.shape  # single chunk
    ])
def test_multi_hide_specific_rows_and_index_through_chaining(rows_per_chunk, cols_per_chunk):
    assert_patched_styler(
        multi_df,
        lambda styler: styler.hide(subset=(slice(None), ["a", "c"])).hide(),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        multi_df.shape  # single chunk
    ])
def test_multi_hide_specific_level(rows_per_chunk, cols_per_chunk):
    assert_patched_styler(
        multi_df,
        lambda styler: styler.hide(level=1),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        multi_df.shape  # single chunk
    ])
def test_multi_hide_index_level_names(rows_per_chunk, cols_per_chunk):
    my_multi_df = multi_df.copy()
    my_multi_df.index.names = ["lev0", "lev1"]
    assert_patched_styler(
        my_multi_df,
        lambda styler: styler.hide(names=True),
        rows_per_chunk,
        cols_per_chunk
    )


def test_for_new_parameters():
    assert_style_func_parameters(
        df.style.hide,
        ['axis', 'subset', 'level', 'names']
    )
