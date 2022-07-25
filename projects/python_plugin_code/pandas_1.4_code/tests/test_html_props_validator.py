#  Copyright 2022 cms.rendner (Daniel Schmidt)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
from datetime import datetime
import numpy as np
import pytest
from pandas import MultiIndex, DataFrame, IndexSlice, Series
from pandas.io.formats.style import Styler

from plugin_code.html_props_validator import HTMLPropsValidator
from plugin_code.patched_styler import PatchedStyler

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


def create_validator(style: Styler) -> HTMLPropsValidator:
    return HTMLPropsValidator(PatchedStyler(style).get_context())


@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        (len(mi_df.index), len(mi_df.columns))  # single chunk
    ])
def test_multi_index_df_without_styles(rows_per_chunk: int, cols_per_chunk: int):
    styler = mi_df.style
    result = create_validator(styler).validate(rows_per_chunk, cols_per_chunk)
    assert result.actual == result.expected
    assert result.is_equal is True


@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        (len(mi_df.index), len(mi_df.columns))  # single chunk
    ])
def test_multi_index_df_hide_level_names(rows_per_chunk: int, cols_per_chunk: int):
    styler = mi_df.style.hide(names=True)
    result = create_validator(styler).validate(rows_per_chunk, cols_per_chunk)
    assert result.actual == result.expected
    assert result.is_equal is True


@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        (len(mi_df.index), len(mi_df.columns))  # single chunk
    ])
def test_multi_index_df_hide_levels(rows_per_chunk: int, cols_per_chunk: int):
    styler = mi_df.style.hide(level=1)
    result = create_validator(styler).validate(rows_per_chunk, cols_per_chunk)
    assert result.actual == result.expected
    assert result.is_equal is True


@pytest.mark.parametrize("subset", [None, df.columns.tolist(), ["col_1", "col_4"]])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        (len(df.index), len(df.columns))  # single chunk
    ])
def test_hide_columns(subset, rows_per_chunk: int, cols_per_chunk: int):
    styler = df.style.hide(axis="columns", subset=subset)
    result = create_validator(styler).validate(rows_per_chunk, cols_per_chunk)
    assert result.actual == result.expected
    assert result.is_equal is True


@pytest.mark.parametrize("subset", [None, df.index.tolist(), IndexSlice[3:4]])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        (len(df.index), len(df.columns))  # single chunk
    ])
def test_hide_index(subset, rows_per_chunk: int, cols_per_chunk: int):
    styler = df.style.hide(axis="index", subset=subset)
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
