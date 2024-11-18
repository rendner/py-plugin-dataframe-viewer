import numpy as np
import pandas as pd
import pytest

from cms_rendner_sdfv.base.types import TableFrame, TableFrameCell, TableStructureColumnInfo, \
    TableStructureLegend, TableStructureColumn
from cms_rendner_sdfv.pandas.shared.types import FilterCriteria
from cms_rendner_sdfv.pandas.styler.patched_styler import PatchedStyler
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext
from cms_rendner_sdfv.pandas.styler.style_functions_validator import StyleFunctionsValidator
from tests.helpers.asserts.assert_patched_styler import assert_patched_styler

np.random.seed(123456)

midx_rows = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]], names=['rows-char', 'rows-color'])
midx_cols = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]], names=['cols-char', 'cols-color'])
multi_df = pd.DataFrame(np.arange(0, 36).reshape(6, 6), index=midx_rows, columns=midx_cols)

df = pd.DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


def test_compute_chunk_table_frame():
    actual = PatchedStyler(PatchedStylerContext(df.style), "finger-1") \
        .compute_chunk_table_frame(0, 0, 2, 2)

    assert actual == TableFrame(
        index_labels=[['0'], ['1']],
        cells=[
            [TableFrameCell(value='0'), TableFrameCell(value='5')],
            [TableFrameCell(value='1'), TableFrameCell(value='6')],
        ],
    )


def test_validate_and_compute_chunk_table_frame():
    actual = PatchedStyler(PatchedStylerContext(df.style), "finger-1") \
        .validate_and_compute_chunk_table_frame(0, 0, 2, 2)

    assert not actual.problems
    assert actual.frame == TableFrame(
        index_labels=[['0'], ['1']],
        cells=[
            [TableFrameCell(value='0'), TableFrameCell(value='5')],
            [TableFrameCell(value='1'), TableFrameCell(value='6')],
        ],
    )


def test_table_structure():
    ts = PatchedStyler(PatchedStylerContext(multi_df.style), "finger-1").get_table_structure()
    assert ts.org_rows_count == len(multi_df.index)
    assert ts.org_columns_count == len(multi_df.columns)
    assert ts.rows_count == len(multi_df.index)
    assert ts.columns_count == len(multi_df.columns)
    assert ts.fingerprint == "finger-1"
    assert ts.column_info == TableStructureColumnInfo(
        legend=TableStructureLegend(index=['rows-char', 'rows-color'], column=['cols-char', 'cols-color']),
        columns=[
            TableStructureColumn(dtype='int64', labels=['x', 'a'], id=0),
            TableStructureColumn(dtype='int64', labels=['x', 'b'], id=1),
            TableStructureColumn(dtype='int64', labels=['x', 'c'], id=2),
            TableStructureColumn(dtype='int64', labels=['y', 'a'], id=3),
            TableStructureColumn(dtype='int64', labels=['y', 'b'], id=4),
            TableStructureColumn(dtype='int64', labels=['y', 'c'], id=5)
        ])


def test_table_structure_with_str_and_int_column_names():
    d = {"B": [1], "A": [1], 101: [1], 0: [1]}
    s = pd.DataFrame.from_dict(d).style
    ts = PatchedStyler(PatchedStylerContext(s), '').get_table_structure()
    assert ts.column_info == TableStructureColumnInfo(
        legend=None,
        columns=[
            TableStructureColumn(dtype='int64', labels=['B'],   id=0),
            TableStructureColumn(dtype='int64', labels=['A'],   id=1),
            TableStructureColumn(dtype='int64', labels=['101'], id=2),
            TableStructureColumn(dtype='int64', labels=['0'],   id=3)
        ])


def test_table_structure_columns_count_hide_all_columns():
    styler = multi_df.style.hide(axis="columns", subset=multi_df.columns)
    ts = PatchedStyler(PatchedStylerContext(styler), "").get_table_structure()
    assert ts.org_columns_count == len(styler.data.columns)
    assert ts.columns_count == 0
    assert ts.column_info == TableStructureColumnInfo(columns=[], legend=None)


def test_table_structure_rows_count_hide_all_rows():
    styler = multi_df.style.hide(axis="index", subset=multi_df.index)
    ts = PatchedStyler(PatchedStylerContext(styler), "").get_table_structure()
    assert ts.org_rows_count == len(styler.data.index)
    assert ts.rows_count == 0
    assert ts.column_info == TableStructureColumnInfo(columns=[], legend=None)


def test_table_structure_diff_matches_hidden_rows_cols():
    styler = df.style \
        .hide(axis="index", subset=pd.IndexSlice[1:3]) \
        .hide(axis="columns", subset=["col_1", "col_3"])
    ts = PatchedStyler(PatchedStylerContext(styler), "").get_table_structure()

    actual_row_diff = ts.org_rows_count - ts.rows_count
    actual_col_diff = ts.org_columns_count - ts.columns_count
    assert actual_row_diff == 3
    assert actual_col_diff == 2


def test_table_structure_diff_matches_hidden_rows_cols_and_filtering():
    styler = df.style \
        .hide(axis="index", subset=pd.IndexSlice[1:3]) \
        .hide(axis="columns", subset=["col_1", "col_3"])
    filter_frame = pd.DataFrame(index=df.index[1:], columns=df.columns[1:])
    ts = PatchedStyler(
        PatchedStylerContext(styler, FilterCriteria.from_frame(filter_frame)),
        "",
    ).get_table_structure()

    actual_row_diff = ts.org_rows_count - ts.rows_count
    actual_col_diff = ts.org_columns_count - ts.columns_count
    assert actual_row_diff == 4
    assert actual_col_diff == 3


def test_table_structure_respects_configured_col_label_formatting():
    s = multi_df.style.format_index(str.upper, axis='columns')
    ps = PatchedStyler(PatchedStylerContext(s), '')

    ts = ps.get_table_structure()
    assert ts.column_info == TableStructureColumnInfo(
        legend=TableStructureLegend(index=['rows-char', 'rows-color'], column=['cols-char', 'cols-color']),
        columns=[
            TableStructureColumn(dtype='int64', labels=['X', 'A'], id=0),
            TableStructureColumn(dtype='int64', labels=['X', 'B'], id=1),
            TableStructureColumn(dtype='int64', labels=['X', 'C'], id=2),
            TableStructureColumn(dtype='int64', labels=['Y', 'A'], id=3),
            TableStructureColumn(dtype='int64', labels=['Y', 'B'], id=4),
            TableStructureColumn(dtype='int64', labels=['Y', 'C'], id=5)
        ])


def test_table_structure_respects_relabeled_cols():
    s = df.style.relabel_index(['1', '2', '3', '4', '5'], axis='columns')
    ps = PatchedStyler(PatchedStylerContext(s), '')

    ts = ps.get_table_structure()
    assert ts.column_info == TableStructureColumnInfo(
        legend=None,
        columns=[
            TableStructureColumn(dtype='int64', labels=['1'], id=0),
            TableStructureColumn(dtype='int64', labels=['2'], id=1),
            TableStructureColumn(dtype='int64', labels=['3'], id=2),
            TableStructureColumn(dtype='int64', labels=['4'], id=3),
            TableStructureColumn(dtype='int64', labels=['5'], id=4)
        ])


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
        df.shape  # single chunk
    ])
def test_render_chunk_translates_display_funcs_correct_also_with_hidden_rows_cols(
        formatter,
        rows_per_chunk,
        cols_per_chunk,
):
    assert_patched_styler(
        df,
        lambda styler: styler
        .hide(axis="index", subset=pd.IndexSlice[1:3])
        .hide(axis="columns", subset=["col_1", "col_3"])
        .format(formatter=formatter),
        rows_per_chunk,
        cols_per_chunk
    )


def test_jsonify():
    json = PatchedStyler(PatchedStylerContext(multi_df.style), "").jsonify({"a": 12, "b": (True, False)})
    assert json == '{"a": 12, "b": [true, false]}'


def test_should_not_revalidate_faulty_styling_functions():
    def is_in_validator():
        import inspect
        frame = inspect.currentframe().f_back
        while frame:
            instance = frame.f_locals.get('self', None)
            if instance is not None and instance.__class__.__name__ == StyleFunctionsValidator.__name__:
                return True
            frame = frame.f_back
        return False

    raise_in_validator = False

    def my_style_func(s):
        nonlocal raise_in_validator
        if raise_in_validator and is_in_validator():
            raise Exception("panic")
        return ['' for _ in s]

    styler = df.style.apply(my_style_func, axis='index')
    ctx = PatchedStylerContext(styler)
    ps = PatchedStyler(ctx, "")

    result = ps.validate_and_compute_chunk_table_frame(0, 0, 2, 2, False)
    assert not result.problems

    raise_in_validator = True

    result = ps.validate_and_compute_chunk_table_frame(0, 0, 2, 2, False)
    assert len(result.problems) == len(ctx.get_todo_patcher_list())

    result = ps.validate_and_compute_chunk_table_frame(0, 0, 2, 2, False)
    assert not result.problems
