from datetime import datetime

import numpy as np
import pytest
from pandas import DataFrame, MultiIndex, Series
from pandas.io.formats.style import Styler

from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext
from tests.helpers.chunk_data_validator import ChunkDataValidator

np.random.seed(123456)

midx = MultiIndex.from_product([["x", "y"], ["a", "b", "c"]])
mi_df = DataFrame(np.random.randn(6, 6), index=midx, columns=midx)
mi_df.index.names = ["lev0", "lev1"]

df = DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


def create_validator(style: Styler) -> ChunkDataValidator:
    return ChunkDataValidator.with_context(PatchedStylerContext(style))


@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        mi_df.shape  # single chunk
    ])
def test_multi_index_df_without_styles(rows_per_chunk: int, cols_per_chunk: int):
    styler = mi_df.style
    result = create_validator(styler).validate(rows_per_chunk, cols_per_chunk)
    assert result.actual == result.expected
    assert result.is_equal is True


@pytest.mark.parametrize("subset", [df.columns.tolist(), ["col_1", "col_4"]])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        df.shape  # single chunk
    ])
def test_hide_columns(subset, rows_per_chunk: int, cols_per_chunk: int):
    styler = df.style.hide_columns(subset=subset)
    result = create_validator(styler).validate(rows_per_chunk, cols_per_chunk)
    assert result.actual == result.expected
    assert result.is_equal is True


@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        df.shape  # single chunk
    ])
def test_hide_index(rows_per_chunk: int, cols_per_chunk: int):
    styler = df.style.hide_index()
    result = create_validator(styler).validate(rows_per_chunk, cols_per_chunk)
    assert result.actual == result.expected
    assert result.is_equal is True


def test_too_large_parameters():
    styler = df.style
    result = create_validator(styler).validate(len(df.index) * 2, len(df.columns) * 2)
    assert result.actual == result.expected
    assert result.is_equal is True


def test_multiple_style_functions_with_subset():
    styler = df.style.highlight_max(subset=(2, "col_2")).highlight_min(subset="col_2")
    result = create_validator(styler).validate(2, 2)
    assert result.actual == result.expected
    assert result.is_equal is True


def test_non_string_values():
    my_df = DataFrame.from_dict({
        'date': [datetime.fromisoformat(x) for x in ['2022-06-10', '2022-07-01', '2022-10-12']],
        'int': [2, 3, 4],
        'tuple': [(0, 0), (0, 1), (0, 2)],
        'dict_tuple_keys': [{(0, 0): 'a'}, {(0, 1): 'b'}, {(0, 2): 'c'}],
    })
    styler = my_df.style
    result = create_validator(styler).validate(1, 1)
    assert result.actual == result.expected
    assert result.is_equal is True


def test_empty_df():
    empty_df = DataFrame()
    styler = empty_df.style
    result = create_validator(styler).validate(1, 1)
    assert result.actual == result.expected
    assert result.is_equal is True


def test_no_chunk_compatible_styling_func():
    def do_strange_things(series: Series):
        colors = np.random.randint(0, 0xFFFFFF, len(series))
        return [f'background-color: {c}' for c in colors]

    styler = df.style.apply(do_strange_things, axis="index")
    result = create_validator(styler).validate(2, 2)
    assert result.actual != result.expected
    assert result.is_equal is False
