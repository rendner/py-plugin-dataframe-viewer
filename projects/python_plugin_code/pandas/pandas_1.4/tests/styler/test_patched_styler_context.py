import pandas as pd
from pandas import DataFrame

from cms_rendner_sdfv.base.types import Region
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
    assert actual == [1, 4]


def test_styled_chunk_uses_formatting_from_org_styler():
    ctx = PatchedStylerContext(df.style.format('{:+.2f}', subset=pd.IndexSlice[0, ["col_2"]]))
    styled_chunk = ctx.compute_styled_chunk(Region.with_frame_shape(df.shape))
    assert styled_chunk.cell_value_at(0, 0) == '0'
    assert styled_chunk.cell_value_at(0, 2) == '+2.00'


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


def test_column_name_completion_with_filter():
    ctx = PatchedStylerContext(df2.style, FilterCriteria.from_frame(df2[['A', 'B']]))
    completer = ctx.get_column_name_completer()

    assert completer.get_variants(df2, False, '') == [f'"{v}"' for v in df2.columns.values]
    assert completer.get_variants(df2, False, 'A') == ['"A"', '"AB"', '"ABC"']
    assert completer.get_variants(df2, False, 'AB') == ['"AB"', '"ABC"']
    assert completer.get_variants(df2, False, 'ABC') == ['"ABC"']
    assert completer.get_variants(df2, False, 'B') == ['"B"']
    assert completer.get_variants(df2, False, 'X') == []


def test_column_name_completion_with_prefix():
    ctx = PatchedStylerContext(df2.style)
    completer = ctx.get_column_name_completer()

    assert completer.get_variants(df2, False, '') == [f'"{v}"' for v in df2.columns.values]
    assert completer.get_variants(df2, False, 'A') == ['"A"', '"AB"', '"ABC"']
    assert completer.get_variants(df2, False, 'AB') == ['"AB"', '"ABC"']
    assert completer.get_variants(df2, False, 'ABC') == ['"ABC"']
    assert completer.get_variants(df2, False, 'B') == ['"B"']
    assert completer.get_variants(df2, False, 'X') == []


def test_column_name_completion_for_synthetic_identifier():
    ctx = PatchedStylerContext(df2.style)
    completer = ctx.get_column_name_completer()

    assert completer.get_variants(None, True, '') == [f'"{v}"' for v in df2.columns.values]
    assert completer.get_variants(None, True, 'A') == ['"A"', '"AB"', '"ABC"']
    assert completer.get_variants(None, True, 'AB') == ['"AB"', '"ABC"']
    assert completer.get_variants(None, True, 'ABC') == ['"ABC"']
    assert completer.get_variants(None, True, 'B') == ['"B"']
    assert completer.get_variants(None, True, 'X') == []

    assert completer.get_variants(None, False, 'A') == []
