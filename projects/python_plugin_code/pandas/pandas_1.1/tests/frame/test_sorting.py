import numpy as np
import pytest
from pandas import DataFrame, MultiIndex
from typing import List

from cms_rendner_sdfv.pandas.frame.frame_context import FrameContext
from tests.helpers.asserts.assert_table_frames import assert_table_frames

df = DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})

midx = MultiIndex.from_product([["x", "y"], ["a", "b", "c"]])
multi_df = DataFrame(np.arange(0, 36).reshape(6, 6), index=midx, columns=midx)


def _assert_frame_sorting(
        df: DataFrame,
        rows_per_chunk: int,
        cols_per_chunk: int,
        sort_by_column_index: List[int],
        sort_ascending: List[bool],
):
    # create: expected
    sorted_df = df.sort_values(by=[df.columns[i] for i in sort_by_column_index], ascending=sort_ascending)
    expected_ctx = FrameContext(sorted_df)
    expected_frame = expected_ctx.get_table_frame_generator().generate()

    # create: actual
    actual_ctx = FrameContext(df)
    actual_ctx.set_sort_criteria(sort_by_column_index, sort_ascending)
    actual_frame = actual_ctx.get_table_frame_generator().generate_by_combining_chunks(
        rows_per_chunk=rows_per_chunk,
        cols_per_chunk=cols_per_chunk,
    )

    assert_table_frames(actual_frame, expected_frame)


@pytest.mark.parametrize(
    "sort_by, ascending", [
        ([0], [False]),
        ([0, 2], [True, False]),
        ([0, 2], [True, True]),
        ([0, 2], [False, False]),
        ([4, 2, 3], [False, True, False]),
    ]
)
def test_sorting_by_multiple_columns(sort_by, ascending):
    _assert_frame_sorting(
        df,
        2,
        2,
        sort_by,
        ascending,
    )


@pytest.mark.parametrize("axis", [None, "index", "columns"])
@pytest.mark.parametrize("sort_by, ascending", [([0], [False])])
def test_sorting_with_multi_df(axis, sort_by, ascending):
    _assert_frame_sorting(
        multi_df,
        2,
        2,
        sort_by,
        ascending,
    )
