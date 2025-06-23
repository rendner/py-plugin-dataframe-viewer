#  Copyright 2021-2025 cms.rendner (Daniel Schmidt)
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
from dataclasses import dataclass
from typing import Callable, Optional, Union, Any

from pandas import DataFrame
from pandas.io.formats.style import Styler

from cms_rendner_sdfv.base.types import Cell, ChunkDataResponse
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext, FilterCriteria

"""
Q: How can we test that a styled chunk is correctly filtered?
    note:
        -> "filter first and style afterward" doesn't work because filtering could break expected styled output
            => filtering out min/max values results in highlighting other min/max values

    idea:
        - all cell values have to be unique
        - create a ChunkData for:
            - the styled expected result 
            - the filtered expected result
            - the styled and filtered actual result (combined from chunks)
        - convert each ChunkData into a dict with the unique cell value as key
        - iterate over dicts and ensure:
            - actual cell has the same styling as in the expected styled cells
            - actual cell has the same position as in the expected filtered cells
"""


def assert_patched_styler_filtering(
        df: DataFrame,
        init_styler_func: Callable[[Styler], None],
        rows_per_chunk: int,
        cols_per_chunk: int,
        filter_keep_items: Optional[list[Any]],
        filter_axis: Optional[Union[str, int]],
):
    # create: expected styled
    styler = df.style
    init_styler_func(styler)
    styled_chunk_data = PatchedStylerContext(styler).get_chunk_data_generator().generate()
    expected_styled_cells = _map_cells_by_unique_display_value(styled_chunk_data, True)

    # create: expected filtered
    filtered_styler = df.filter(items=filter_keep_items, axis=filter_axis).style
    filtered_chunk_data = PatchedStylerContext(filtered_styler).get_chunk_data_generator().generate()
    expected_filtered_cells = _map_cells_by_unique_display_value(filtered_chunk_data)

    # create: actual styled and filtered
    chunk_styler = df.style
    init_styler_func(chunk_styler)
    styled_and_filtered_chunk_data = PatchedStylerContext(
            chunk_styler,
            FilterCriteria.from_frame(df.filter(items=filter_keep_items, axis=filter_axis)),
        ).get_chunk_data_generator().generate_by_combining_chunks(
        rows_per_chunk=rows_per_chunk,
        cols_per_chunk=cols_per_chunk,
    )
    actual_cells = _map_cells_by_unique_display_value(styled_and_filtered_chunk_data)

    _compare_cells(
        actual_cells=actual_cells,
        expected_styled_cells=expected_styled_cells,
        expected_filtered_cells=expected_filtered_cells,
    )


@dataclass
class CellInfo:
    cell: Cell
    row: int
    col: int


def _compare_cells(
        actual_cells: dict[str, CellInfo],
        expected_styled_cells: dict[str, CellInfo],
        expected_filtered_cells: dict[str, CellInfo],
):
    assert len(expected_styled_cells) > 0
    assert len(actual_cells) == len(expected_filtered_cells)

    for key, value in actual_cells.items():
        # check correct filtering
        expected_filtered_info = expected_filtered_cells[key]
        assert value.row == expected_filtered_info.row
        assert value.col == expected_filtered_info.col

        # check styling
        expected_styled_info = expected_styled_cells[key]
        assert value.cell.meta == expected_styled_info.cell.meta


def _map_cells_by_unique_display_value(chunk_data: ChunkDataResponse, assert_if_empty: bool = False) -> dict[str, CellInfo]:
    result = {}
    for ri, row in enumerate(chunk_data.cells):
        for ci, cell in enumerate(row):
            if cell.value in result:
                raise KeyError("All cell elements must have a unique value.")
            result[cell.value] = CellInfo(cell, ri, ci)
    if assert_if_empty:
        assert len(result) > 0
    return result
