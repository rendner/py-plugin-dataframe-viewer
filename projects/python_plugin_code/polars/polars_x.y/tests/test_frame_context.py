import polars as pl

from cms_rendner_sdfv.polars.frame_context import FrameContext

df = pl.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [1, 2, 3, 4, 5],
    "col_2": [2, 3, 4, 5, 6],
    "col_3": [3, 4, 5, 6, 7],
    "col_4": [4, 5, 6, 7, 8],
})


def test_previous_sort_criteria_does_not_affect_later_sort_criteria():
    ctx = FrameContext(df)
    ctx.set_sort_criteria(sort_by_column_index=[0], sort_ascending=[True])
    index_after_first_sort = list(ctx.visible_frame.row_idx_iter())

    ctx.set_sort_criteria(sort_by_column_index=[0, 1], sort_ascending=[False, False])
    # assert to ensure test setup is correct
    index_in_between = list(ctx.visible_frame.row_idx_iter())
    assert index_after_first_sort != index_in_between

    ctx.set_sort_criteria(sort_by_column_index=[0], sort_ascending=[True])
    index_after_last_sort = list(ctx.visible_frame.row_idx_iter())
    assert index_after_first_sort == index_after_last_sort
