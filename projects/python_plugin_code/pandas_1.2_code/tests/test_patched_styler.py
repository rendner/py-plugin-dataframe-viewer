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
import pandas as pd
import numpy as np
import pytest
from pandas import MultiIndex, DataFrame

from plugin_code.patched_styler import PatchedStyler
from tests.helpers.asserts.assert_styler import create_and_assert_patched_styler

np.random.seed(123456)

midx = MultiIndex.from_product([["x", "y"], ["a", "b", "c"]])
df = DataFrame(np.random.randn(6, 6), index=midx, columns=midx)
df.index.names = ["lev0", "lev1"]


def test_table_structure_hide_row_header():
    styler = df.style.hide_index()
    ts = PatchedStyler(styler).get_table_structure()
    assert ts.hide_row_header is True
    assert ts.hide_column_header is False


def test_table_structure_columns_count():
    ts = PatchedStyler(df.style).get_table_structure()
    assert ts.columns_count == 6


def test_table_structure_rows_count():
    ts = PatchedStyler(df.style).get_table_structure()
    assert ts.rows_count == 6


def test_table_structure_columns_count_hide_all_columns():
    styler = df.style.hide_columns(subset=df.columns)
    ts = PatchedStyler(styler).get_table_structure()
    assert ts.columns_count == 0
    assert ts.hide_column_header is False


def test_table_structure_column_level_count():
    ts = PatchedStyler(df.style).get_table_structure()
    assert ts.column_levels_count == 2


def test_table_structure_row_level_count():
    ts = PatchedStyler(df.style).get_table_structure()
    assert ts.row_levels_count == 2


other_df = pd.DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


class FakeFormatterDict(dict):
    def get(self, key):
        return self.formatter

    @staticmethod
    def formatter(x):
        return x


@pytest.mark.parametrize("formatter", [FakeFormatterDict(), lambda x: x])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        (len(df.index), len(df.columns))  # single chunk
    ])
def test_render_chunk_translates_display_funcs_correct_also_with_hidden_cols(
        formatter,
        rows_per_chunk,
        cols_per_chunk,
):
    create_and_assert_patched_styler(
        other_df,
        lambda styler: styler
            .hide_columns(["col_1", "col_3"])
            .format(formatter=formatter),
        rows_per_chunk,
        cols_per_chunk
    )
