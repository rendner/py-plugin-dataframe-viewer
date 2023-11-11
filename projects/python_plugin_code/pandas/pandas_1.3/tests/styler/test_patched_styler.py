import numpy as np
import pandas as pd
import pytest
from pandas import DataFrame, MultiIndex

from cms_rendner_sdfv.pandas.shared.types import FilterCriteria
from cms_rendner_sdfv.pandas.styler.patched_styler import PatchedStyler
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext
from cms_rendner_sdfv.pandas.styler.types import StyleFunctionInfo
from tests.helpers.asserts.assert_patched_styler import assert_patched_styler

np.random.seed(123456)

midx = MultiIndex.from_product([["x", "y"], ["a", "b", "c"]])
df = DataFrame(np.random.randn(6, 6), index=midx, columns=midx)
df.index.names = ["lev0", "lev1"]

other_df = pd.DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


def test_table_structure():
    ts = PatchedStyler(PatchedStylerContext(df.style), "finger-1").get_table_structure()
    assert ts.org_rows_count == len(df.index)
    assert ts.org_columns_count == len(df.columns)
    assert ts.rows_count == len(df.index)
    assert ts.columns_count == len(df.columns)
    assert ts.fingerprint == "finger-1"


def test_table_structure_columns_count_hide_all_columns():
    styler = df.style.hide_columns(subset=df.columns)
    ts = PatchedStyler(PatchedStylerContext(styler), "").get_table_structure()
    assert ts.org_columns_count == len(styler.data.columns)
    assert ts.columns_count == 0


def test_table_structure_rows_count_hide_all_rows():
    styler = df.style.hide_index(subset=df.index)
    ts = PatchedStyler(PatchedStylerContext(styler), "").get_table_structure()
    assert ts.org_rows_count == len(styler.data.index)
    assert ts.rows_count == 0


def test_table_structure_diff_matches_hidden_rows_cols():
    styler = other_df.style\
        .hide_index(subset=pd.IndexSlice[1:3])\
        .hide_columns(subset=["col_1", "col_3"])
    ts = PatchedStyler(PatchedStylerContext(styler), "").get_table_structure()

    actual_row_diff = ts.org_rows_count - ts.rows_count
    actual_col_diff = ts.org_columns_count - ts.columns_count
    assert actual_row_diff == 3
    assert actual_col_diff == 2


def test_table_structure_diff_matches_hidden_rows_cols_and_filtering():
    styler = other_df.style\
        .hide_index(subset=pd.IndexSlice[1:3])\
        .hide_columns(subset=["col_1", "col_3"])
    filter_frame = DataFrame(index=other_df.index[1:], columns=other_df.columns[1:])
    ts = PatchedStyler(
        PatchedStylerContext(styler, FilterCriteria.from_frame(filter_frame)),
        "",
    ).get_table_structure()

    actual_row_diff = ts.org_rows_count - ts.rows_count
    actual_col_diff = ts.org_columns_count - ts.columns_count
    assert actual_row_diff == 4
    assert actual_col_diff == 3


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
        other_df.shape  # single chunk
    ])
def test_render_chunk_translates_display_funcs_correct_also_with_hidden_rows_cols(
        formatter,
        rows_per_chunk,
        cols_per_chunk,
):
    assert_patched_styler(
        other_df,
        lambda styler: styler
            .hide_index(pd.IndexSlice[1:3])
            .hide_columns(["col_1", "col_3"])
            .format(formatter=formatter),
        rows_per_chunk,
        cols_per_chunk
    )


def test_get_style_function_info_no_styles():
    styler = df.style
    info = PatchedStyler(PatchedStylerContext(styler), "").get_style_function_info()
    assert len(info) == 0


def test_get_style_function_info():
    styler = df.style.bar().highlight_min(axis='columns').applymap(lambda x: "color: red")
    info = PatchedStyler(PatchedStylerContext(styler), "").get_style_function_info()
    assert len(info) == 3
    assert info[0] == StyleFunctionInfo(
        index=0,
        qname='Styler._bar',
        resolved_name='_bar',
        axis='0',
        is_chunk_parent_requested=False,
        is_apply=True,
        is_pandas_builtin=True,
        is_supported=False,
    )
    assert info[1] == StyleFunctionInfo(
        index=1,
        qname='_highlight_value',
        resolved_name='highlight_min',
        axis='columns',
        is_chunk_parent_requested=False,
        is_apply=True,
        is_pandas_builtin=True,
        is_supported=True,
    )
    assert info[2] == StyleFunctionInfo(
        index=2,
        qname='test_get_style_function_info.<locals>.<lambda>',
        resolved_name='<lambda>',
        axis='',
        is_chunk_parent_requested=False,
        is_apply=False,
        is_pandas_builtin=False,
        is_supported=True,
    )


def test_jsonify():
    json = PatchedStyler(PatchedStylerContext(df.style), "").jsonify({"a": 12, "b": (True, False)})
    assert json == '{"a": 12, "b": [true, false]}'


def test_get_org_indices_of_visible_columns():
    ps = PatchedStyler(PatchedStylerContext(df.style), "")

    num_cols = 3
    actual = ps.get_org_indices_of_visible_columns(0, num_cols)
    assert actual == list(range(0, num_cols))
