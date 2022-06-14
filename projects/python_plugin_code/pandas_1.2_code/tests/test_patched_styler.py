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
from pandas import MultiIndex, DataFrame, Series

from plugin_code.patched_styler import PatchedStyler, TableStructure, StyleFunctionDetails
from tests.helpers.asserts.assert_styler import create_and_assert_patched_styler

np.random.seed(123456)

midx = MultiIndex.from_product([["x", "y"], ["a", "b", "c"]])
df = DataFrame(np.random.randn(6, 6), index=midx, columns=midx)
df.index.names = ["lev0", "lev1"]


def test_table_structure_columns_count():
    ts = PatchedStyler(df.style).get_table_structure()
    assert ts == TableStructure(
        rows_count=6,
        columns_count=6,
        row_levels_count=2,
        column_levels_count=2,
        hide_row_header=False,
        hide_column_header=False,
    )

def test_table_structure_hide_row_header():
    styler = df.style.hide_index()
    ts = PatchedStyler(styler).get_table_structure()
    assert ts.hide_row_header is True
    assert ts.hide_column_header is False


def test_table_structure_columns_count_hide_all_columns():
    styler = df.style.hide_columns(subset=df.columns)
    ts = PatchedStyler(styler).get_table_structure()
    assert ts.columns_count == 0
    assert ts.hide_column_header is False


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


def test_get_style_function_details_df_no_styles():
    styler = df.style
    details = PatchedStyler(styler).get_style_function_details()
    assert len(details) == 0


def test_get_style_function_details_df():
    styler = df.style.bar().highlight_min(axis='columns').applymap(lambda x: "color: red")
    details = PatchedStyler(styler).get_style_function_details()
    assert len(details) == 3
    assert details[0] == StyleFunctionDetails(
        index=0,
        qname='Styler._bar',
        resolved_name='_bar',
        axis='0',
        is_chunk_parent_requested=False,
        is_apply=True,
        is_pandas_builtin=True,
        is_supported=False,
    )
    assert details[1] == StyleFunctionDetails(
        index=1,
        qname='Styler._highlight_extrema',
        resolved_name='highlight_min',
        axis='columns',
        is_chunk_parent_requested=False,
        is_apply=True,
        is_pandas_builtin=True,
        is_supported=True,
    )
    assert details[2] == StyleFunctionDetails(
        index=2,
        qname='test_get_style_function_details_df.<locals>.<lambda>',
        resolved_name='<lambda>',
        axis='',
        is_chunk_parent_requested=False,
        is_apply=False,
        is_pandas_builtin=False,
        is_supported=True,
    )
