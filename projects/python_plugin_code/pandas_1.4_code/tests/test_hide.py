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
import pytest

from tests.helpers.asserts.assert_styler import create_and_assert_patched_styler

df = pd.DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


@pytest.mark.parametrize("subset", [None, df.columns.tolist(), ["col_1", "col_4"]])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        (len(df.index), len(df.columns))  # single chunk
    ])
def test_hide_columns_chunked_with_style(subset, rows_per_chunk, cols_per_chunk):
    create_and_assert_patched_styler(
        df,
        # the max value is hidden if subset is != None ("col_4")
        # so the chunked table should also not include the highlighted value
        lambda styler: styler.highlight_max(axis=None).hide(axis="columns", subset=subset),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize("subset", [None, df.columns.tolist(), ["col_1", "col_4"]])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        (len(df.index), len(df.columns))  # single chunk
    ])
def test_hide_columns_chunked(subset, rows_per_chunk, cols_per_chunk):
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.hide(axis="columns").hide(axis="columns", subset=subset),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize("subset", [None, df.index.tolist(), pd.IndexSlice[3:4]])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        (len(df.index), len(df.columns))  # single chunk
    ])
def test_hide_index_with_style(subset, rows_per_chunk, cols_per_chunk):
    create_and_assert_patched_styler(
        df,
        # the max value (located in "col_4") is hidden if subset is != None
        # so the chunked table should also not include the highlighted value
        lambda styler: styler.highlight_max(axis=None).hide(axis="index", subset=subset),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize("subset", [None, df.index.tolist(), pd.IndexSlice[3:4]])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        (len(df.index), len(df.columns))  # single chunk
    ])
def test_hide_index_chunked(subset, rows_per_chunk, cols_per_chunk):
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.hide(axis="index").hide(axis="index", subset=subset),
        rows_per_chunk,
        cols_per_chunk
    )


def test_hide_index_and_columns_chunked():
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.hide(axis="columns").hide(axis="index"),
        2,
        2
    )
