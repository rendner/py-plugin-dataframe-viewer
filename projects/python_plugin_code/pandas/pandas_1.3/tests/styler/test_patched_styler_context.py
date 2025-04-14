import numpy as np
import pandas as pd
from pandas import DataFrame

from cms_rendner_sdfv.base.types import CompletionVariant, NestedCompletionVariant, Cell, CellMeta
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext, FilterCriteria

df = DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [1, 2, 3, 4, 5],
    "col_2": [2, 3, 4, 5, 6],
    "col_3": [3, 4, 5, 6, 7],
    "col_4": [4, 5, 6, 7, 8],
})

df2 = DataFrame.from_dict({
    "A": [0, 1, 2, 3, 4],
    "AB": [1, 2, 3, 4, 5],
    "ABC": [2, 3, 4, 5, 6],
    "B": [3, 4, 5, 6, 7],
})

np.random.seed(123456)

midx_rows = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]], names=['rows-char', 'rows-color'])
midx_cols = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]], names=['cols-char', 'cols-color'])
multi_df = pd.DataFrame(np.arange(0, 36).reshape(6, 6), index=midx_rows, columns=midx_cols)


def test_previous_sort_criteria_does_not_affect_later_sort_criteria():
    ctx = PatchedStylerContext(df.style)
    ctx.set_sort_criteria(sort_by_column_index=[0], sort_ascending=[True])
    index_after_first_sort = ctx.visible_frame.to_frame(ctx.visible_frame.region).index

    ctx.set_sort_criteria(sort_by_column_index=[0, 1], sort_ascending=[False, False])
    # assert to ensure test setup is correct
    index_in_between = ctx.visible_frame.to_frame(ctx.visible_frame.region).index
    assert list(index_after_first_sort) != list(index_in_between)

    ctx.set_sort_criteria(sort_by_column_index=[0], sort_ascending=[True])
    index_after_last_sort = ctx.visible_frame.to_frame(ctx.visible_frame.region).index
    assert list(index_after_first_sort) == list(index_after_last_sort)


def test_get_org_indices_of_visible_columns():
    ctx = PatchedStylerContext(df.style)

    actual = ctx.visible_frame.get_column_indices()
    assert actual == [0, 1, 2, 3, 4]


def test_get_org_indices_of_visible_columns_with_filter():
    # include only two columns of org df - but change the order of these columns
    fc = FilterCriteria.from_frame(df[['col_4', 'col_1']])
    ctx = PatchedStylerContext(df.style, fc)

    actual = ctx.visible_frame.get_column_indices()
    assert list(actual) == [1, 4]


def test_styled_chunk_uses_formatting_from_org_styler():
    ctx = PatchedStylerContext(df.style.format('{:+.2f}', subset=pd.IndexSlice[0, ["col_2"]]))
    chunk_data = ctx.get_chunk_data_generator().generate()
    assert chunk_data.cells[0][0] == Cell(value='0', meta=CellMeta.min().pack())
    assert chunk_data.cells[0][2] == Cell(value='+2.00', meta=CellMeta.min().pack())


def test_detects_supported_pandas_style_funcs():
    styler = df.style \
        .background_gradient() \
        .highlight_between() \
        .highlight_min() \
        .highlight_max() \
        .highlight_null() \
        .highlight_quantile() \
        .text_gradient() \
        .set_properties()

    ctx = PatchedStylerContext(styler)

    assert len(ctx.get_todo_patcher_list()) == len(styler._todo)


def test_detects_not_supported_pandas_style_funcs():
    styler = df.style.bar()

    ctx = PatchedStylerContext(styler)

    assert len(ctx.get_todo_patcher_list()) == 0


def test_column_name_completion_variants():
    ctx = PatchedStylerContext(df2.style)

    assert ctx.get_column_name_completion_variants(source=df, is_synthetic_df=False) == [
        CompletionVariant(fq_type='builtins.str', value='col_0'),
        CompletionVariant(fq_type='builtins.str', value='col_1'),
        CompletionVariant(fq_type='builtins.str', value='col_2'),
        CompletionVariant(fq_type='builtins.str', value='col_3'),
        CompletionVariant(fq_type='builtins.str', value='col_4')
    ]

    assert ctx.get_column_name_completion_variants(source=df2, is_synthetic_df=False) == [
        CompletionVariant(fq_type='builtins.str', value='A'),
        CompletionVariant(fq_type='builtins.str', value='AB'),
        CompletionVariant(fq_type='builtins.str', value='ABC'),
        CompletionVariant(fq_type='builtins.str', value='B')
    ]

    assert ctx.get_column_name_completion_variants(source=None, is_synthetic_df=True) == [
        CompletionVariant(fq_type='builtins.str', value='A'),
        CompletionVariant(fq_type='builtins.str', value='AB'),
        CompletionVariant(fq_type='builtins.str', value='ABC'),
        CompletionVariant(fq_type='builtins.str', value='B')
    ]

    assert ctx.get_column_name_completion_variants(source=None, is_synthetic_df=False) == []


def test_column_name_completion_variants_includes_columns_hidden_in_styler():
    # test is added to document that hidden columns are not respected
    ctx = PatchedStylerContext(df.style.hide_columns(["col_1"]))

    assert ctx.get_column_name_completion_variants(source=df, is_synthetic_df=False) == [
        CompletionVariant(fq_type='builtins.str', value='col_0'),
        CompletionVariant(fq_type='builtins.str', value='col_1'),
        CompletionVariant(fq_type='builtins.str', value='col_2'),
        CompletionVariant(fq_type='builtins.str', value='col_3'),
        CompletionVariant(fq_type='builtins.str', value='col_4')
    ]


def test_column_name_completion_variants_with_tuples():
    ctx = PatchedStylerContext(multi_df.style)

    assert ctx.get_column_name_completion_variants(source=multi_df, is_synthetic_df=False) == [
        NestedCompletionVariant(
            fq_type='builtins.tuple',
            children=[
                CompletionVariant(fq_type='builtins.str', value='x'),
                CompletionVariant(fq_type='builtins.str', value='a'),
            ],
        ),
        NestedCompletionVariant(
            fq_type='builtins.tuple',
            children=[
                CompletionVariant(fq_type='builtins.str', value='x'),
                CompletionVariant(fq_type='builtins.str', value='b'),
            ],
        ),
        NestedCompletionVariant(
            fq_type='builtins.tuple',
            children=[
                CompletionVariant(fq_type='builtins.str', value='x'),
                CompletionVariant(fq_type='builtins.str', value='c'),
            ],
        ),
        NestedCompletionVariant(
            fq_type='builtins.tuple',
            children=[
                CompletionVariant(fq_type='builtins.str', value='y'),
                CompletionVariant(fq_type='builtins.str', value='a'),
            ],
        ),
        NestedCompletionVariant(
            fq_type='builtins.tuple',
            children=[
                CompletionVariant(fq_type='builtins.str', value='y'),
                CompletionVariant(fq_type='builtins.str', value='b'),
            ],
        ),
        NestedCompletionVariant(
            fq_type='builtins.tuple',
            children=[
                CompletionVariant(fq_type='builtins.str', value='y'),
                CompletionVariant(fq_type='builtins.str', value='c'),
            ],
        ),
    ]
