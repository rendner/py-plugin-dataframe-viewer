import pandas as pd

from cms_rendner_sdfv.base.types import Region
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext

df = pd.DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [1, 2, 3, 4, 5],
    "col_2": [2, 3, 4, 5, 6],
    "col_3": [3, 4, 5, 6, 7],
    "col_4": [4, 5, 6, 7, 8],
})


def test_with_subset():
    styler = df.style.background_gradient(
        axis=None,
        subset=pd.IndexSlice[df.index[1:-1], df.columns[1:-1]],
    )

    ctx = PatchedStylerContext(styler)
    patcher = ctx.get_todo_patcher_list()[0]

    pd.testing.assert_frame_equal(
        patcher._TodoPatcher__org_subset_frame,
        df.loc[df.index[1:-1], df.columns[1:-1]],
    )


def test_without_subset():
    styler = df.style.background_gradient(axis=None)

    ctx = PatchedStylerContext(styler)
    patcher = ctx.get_todo_patcher_list()[0]

    pd.testing.assert_frame_equal(
        patcher._TodoPatcher__org_subset_frame,
        df,
    )


def test_patcher_for_style_func_validation__subset_and_non_intersecting_chunk():
    # style last cell of last col
    styler = df.style.background_gradient(
        axis=None,
        subset=pd.IndexSlice[df.index[-1:], df.columns[-1:]],
    )

    ctx = PatchedStylerContext(styler)
    # region is first cell of first col
    chunk_df = ctx.visible_frame.to_frame(Region(0, 0, 1, 1))

    patcher = ctx.get_todo_patcher_list()[0]
    validation_patcher = patcher.patcher_for_style_func_validation(chunk_df)

    assert validation_patcher._TodoPatcher__org_subset_frame.empty


def test_patcher_for_style_func_validation__subset_and_intersecting_chunk():
    styler = df.style.background_gradient(
        axis=None,
        subset=pd.IndexSlice[df.index[-2:], df.columns[-2:]],
    )

    ctx = PatchedStylerContext(styler)
    chunk_df = ctx.visible_frame.to_frame(Region(2, 2, 3, 3))

    patcher = ctx.get_todo_patcher_list()[0]
    validation_patcher = patcher.patcher_for_style_func_validation(chunk_df)

    pd.testing.assert_frame_equal(
        validation_patcher._TodoPatcher__org_subset_frame,
        df.loc[df.index[-2:], df.columns[-2:]],
    )


def test_patcher_for_style_func_validation__subset_and_matching_chunk():
    styler = df.style.background_gradient(
        axis=None,
        subset=pd.IndexSlice[df.index[1:-1], df.columns[1:-1]],
    )

    ctx = PatchedStylerContext(styler)
    chunk_df = ctx.visible_frame.to_frame(Region(1, 1, 3, 3))

    patcher = ctx.get_todo_patcher_list()[0]
    validation_patcher = patcher.patcher_for_style_func_validation(chunk_df)

    pd.testing.assert_frame_equal(
        validation_patcher._TodoPatcher__org_subset_frame,
        df.loc[df.index[1:-1], df.columns[1:-1]],
    )
