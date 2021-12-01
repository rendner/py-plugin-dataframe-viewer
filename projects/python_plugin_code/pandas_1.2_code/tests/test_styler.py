#  Copyright 2021 cms.rendner (Daniel Schmidt)
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
import numpy as np
import pytest
from pandas import DataFrame

from tests.helpers.asserts.assert_styler import create_and_assert_patched_styler
from tests.helpers.custom_styler_functions import highlight_even_numbers

df_non_unique_cols = pd.DataFrame(
    data=np.arange(1, 10).reshape(3, 3),
    columns=["a", "b", "a"]
)
df_non_unique_idx = pd.DataFrame(
    data=np.arange(1, 10).reshape(3, 3),
    columns=["a", "b", "c"],
    index=["x", "y", "x"]
)
df_non_unique_cols_idx = pd.DataFrame(
    data=np.arange(1, 10).reshape(3, 3),
    columns=["a", "b", "a"],
    index=["x", "y", "x"]
)
df = pd.DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})

'''
These tests are vital. If they fail, the patchers can no longer work.
'''


@pytest.mark.parametrize("my_df", [
    df_non_unique_cols,
    df_non_unique_idx,
    df_non_unique_cols_idx,
])
def test_raise_non_unique_key_error(my_df: DataFrame):
    msg = "style is not supported for non-unique indices."
    with pytest.raises(ValueError, match=msg):
        # noinspection PyStatementEffect
        my_df.style


@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        (len(df.index), len(df.columns))  # single chunk
    ])
def test_use_with_export_from_same_frame_containing_builtin_styler(rows_per_chunk, cols_per_chunk):
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.use(df.style.highlight_max().export()),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        (len(df.index), len(df.columns))  # single chunk
    ])
def test_use_with_export_from_duplicated_frame_containing_builtin_styler(rows_per_chunk, cols_per_chunk):
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.use(df.copy().style.highlight_max().export()),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        (len(df.index), len(df.columns))  # single chunk
    ])
def test_use_with_export_from_another_frame_containing_apply_styler(rows_per_chunk, cols_per_chunk):
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.use(df.copy().style.apply(highlight_even_numbers).export()),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        (len(df.index), len(df.columns))  # single chunk
    ])
def test_use_with_export_from_another_frame_containing_applymap_styler(rows_per_chunk, cols_per_chunk):
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.use(df.copy().style.applymap(highlight_even_numbers).export()),
        rows_per_chunk,
        cols_per_chunk
    )
