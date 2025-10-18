import numpy as np
import pytest
from pandas import DataFrame, MultiIndex

from cms_rendner_sdfv.base.types import SortConfig
from cms_rendner_sdfv.pandas.frame.frame_context import FrameContext

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
        sort_config: SortConfig,
):
    # create: expected
    sorted_df = df.sort_values(by=[df.columns[i] for i in sort_config.column_idx], ascending=sort_config.ascending)
    expected_ctx = FrameContext(sorted_df)
    expected_chunk_data = expected_ctx.get_chunk_data_generator().generate()

    # create: actual
    actual_ctx = FrameContext(df)
    actual_ctx.apply_sort(sort_config)
    actual_chunk_data = actual_ctx.get_chunk_data_generator().generate_by_combining_chunks(
        rows_per_chunk=rows_per_chunk,
        cols_per_chunk=cols_per_chunk,
    )

    assert actual_chunk_data == expected_chunk_data


@pytest.mark.parametrize(
    "sort_config", [
        SortConfig([0], [False]),
        SortConfig([0, 2], [True, False]),
        SortConfig([0, 2], [True, True]),
        SortConfig([0, 2], [False, False]),
        SortConfig([4, 2, 3], [False, True, False]),
    ]
)
def test_sorting_by_multiple_columns(sort_config):
    _assert_frame_sorting(
        df,
        2,
        2,
        sort_config,
    )


@pytest.mark.parametrize("axis", [None, "index", "columns"])
@pytest.mark.parametrize("sort_config", [SortConfig([0], [False])])
def test_sorting_with_multi_df(axis, sort_config):
    _assert_frame_sorting(
        multi_df,
        2,
        2,
        sort_config,
    )
