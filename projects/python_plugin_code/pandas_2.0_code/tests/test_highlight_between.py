#  Copyright 2023 cms.rendner (Daniel Schmidt)
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
import pandas as pd
import pytest

from tests.helpers.assert_style_func_parameters import assert_style_func_parameters
from tests.helpers.asserts.assert_styler import create_and_assert_patched_styler

df = pd.DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


@pytest.mark.parametrize("axis", [None, 0, 1])
@pytest.mark.parametrize("subset", [None, pd.IndexSlice[2:3, ["col_2", "col_3"]]])
@pytest.mark.parametrize("color", [None, "pink"])
@pytest.mark.parametrize("props", [None, "font-weight: bold;"])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        (len(df.index), len(df.columns))  # single chunk
    ])
def test_chunked(axis, subset, color, props, rows_per_chunk, cols_per_chunk):
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.highlight_between(axis=axis, subset=subset, color=color, props=props),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize("axis", [None, 0, 1, "index", "columns"])
@pytest.mark.parametrize("subset", [None, pd.IndexSlice[2:3, ["col_2", "col_3"]]])
def test_can_handle_axis_values(axis, subset):
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.highlight_between(axis=axis, subset=subset),
        2,
        2
    )


@pytest.mark.parametrize("subset", [
    2,  # reduce to row
    "col_2",  # reduce to column
    (2, "col_2"),  # reduce to scalar
])
def test_frame_can_handle_reducing_subset(subset):
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.highlight_between(axis=None, subset=subset),
        2,
        2
    )


# https://pandas.pydata.org/pandas-docs/stable/reference/api/pandas.io.formats.style.Styler.highlight_between.html
pandas_api_example_df = pd.DataFrame({
    'One': [1.2, 1.6, 1.5],
    'Two': [2.9, 2.1, 2.5],
    'Three': [3.1, 3.2, 3.8],
})


def test_pandas_api_example_1():
    create_and_assert_patched_styler(
        pandas_api_example_df,
        lambda styler: styler.highlight_between(left=2.1, right=2.9),
        2,
        2
    )


def test_pandas_api_example_2():
    create_and_assert_patched_styler(
        pandas_api_example_df,
        lambda styler: styler.highlight_between(left=[1.4, 2.4, 3.4], right=[1.6, 2.6, 3.6], axis=1),
        2,
        2
    )


def test_pandas_api_example_3():
    create_and_assert_patched_styler(
        pandas_api_example_df,
        lambda styler: styler.highlight_between(left=[[2, 2, 3], [2, 2, 3], [3, 3, 3]], right=3.5, axis=None),
        2,
        2
    )


pandas_test_example_df = pd.DataFrame({
    'One': [1.2, 1.6, 1.5],
    'Two': [2.9, 2.1, 2.5],
    'Three': [3.1, 3.2, 3.8],
})


# https://github.com/pandas-dev/pandas/blob/v1.3.0/pandas/tests/io/formats/style/test_highlight.py#L81-L101
@pytest.mark.parametrize(
    "kwargs",
    [
        {"left": 0, "right": 1},  # test basic range
        {"left": 0, "right": 1, "props": "background-color: yellow"},  # test props
        {"left": -100, "right": 100, "subset": pd.IndexSlice[[0, 1], :]},  # test subset
        {"left": 0, "subset": pd.IndexSlice[[0, 1], :]},  # test no right
        {"right": 1},  # test no left
        {"left": [0, 0, 11], "axis": 0},  # test left as sequence
        {"left": pd.DataFrame({"A": [0, 0, 11], "B": [1, 1, 11]}), "axis": None},  # axis
        {"left": 0, "right": [0, 0, 1], "axis": 1},  # test sequence right
    ],
)
def test_pandas_test_example_highlight_between(kwargs):
    create_and_assert_patched_styler(
        pandas_test_example_df,
        lambda styler: styler.highlight_between(**kwargs),
        2,
        2
    )


def test_for_new_parameters():
    assert_style_func_parameters(
        df.style.highlight_between,
        ['axis', 'subset', 'color', 'left', 'right', 'inclusive', 'props']
    )
