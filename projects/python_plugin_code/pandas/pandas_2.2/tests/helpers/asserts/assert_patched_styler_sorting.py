#  Copyright 2021-2024 cms.rendner (Daniel Schmidt)
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
from typing import Callable, Optional

from pandas import DataFrame
from pandas.io.formats.style import Styler

from cms_rendner_sdfv.base.types import TableFrameCell, TableFrame
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext
from cms_rendner_sdfv.pandas.styler.table_frame_generator import TableFrameGenerator

"""
Q: How can we test that a styled chunk is correctly sorted?
    note:
        -> the feature "style and sort afterwards" doesn't exist in pandas
        -> "sort first and style afterwards" doesn't work see test case "test_sort_values_before_styling_breaks_styling"

    idea:
        - all cell values have to be unique
        - create a FrameTable for:
            - the styled expected result 
            - the sorted expected result
            - the styled and sorted actual result (combined from chunks)
        - convert each table into a dict with the unique cell value as key
        - iterate over dicts and ensure:
            - actual cell has the same styling as in the expected styled cells
            - actual cell has the same position as in the expected sorted cells
"""


def assert_patched_styler_sorting(
        df: DataFrame,
        init_styler_func: Callable[[Styler], None],
        rows_per_chunk: int,
        cols_per_chunk: int,
        sort_by_column_index: list[int],
        sort_ascending: list[bool],
        init_expected_sorted_styler_func: Optional[Callable[[Styler], None]] = None,
):
    # create: expected styled
    styler = df.style
    init_styler_func(styler)
    styled_table = TableFrameGenerator(PatchedStylerContext(styler)).generate()
    expected_styled_cells = _map_cells_by_unique_display_value(styled_table)

    # create: expected sorted
    sorted_styler = df.sort_values(by=[df.columns[i] for i in sort_by_column_index], ascending=sort_ascending).style
    if init_expected_sorted_styler_func is not None:
        init_expected_sorted_styler_func(sorted_styler)
    sorted_table = TableFrameGenerator(PatchedStylerContext(sorted_styler)).generate()
    expected_sorted_cells = _map_cells_by_unique_display_value(sorted_table)

    # create: actual styled and sorted
    chunk_styler = df.style
    init_styler_func(chunk_styler)
    ps_ctx = PatchedStylerContext(chunk_styler)
    ps_ctx.set_sort_criteria(sort_by_column_index, sort_ascending)
    actual_cells = _map_cells_by_unique_display_value(
        TableFrameGenerator(ps_ctx).generate_by_combining_chunks(rows_per_chunk=rows_per_chunk, cols_per_chunk=cols_per_chunk)
    )

    _compare_cells(
        actual_cells=actual_cells,
        expected_styled_cells=expected_styled_cells,
        expected_sorted_cells=expected_sorted_cells,
    )


@dataclass
class CellInfo:
    cell: TableFrameCell
    row: int
    col: int


def _compare_cells(
        actual_cells: dict[str, CellInfo],
        expected_styled_cells: dict[str, CellInfo],
        expected_sorted_cells: dict[str, CellInfo],
):
    assert len(actual_cells) > 0
    assert len(actual_cells) == len(expected_styled_cells)
    assert len(actual_cells) == len(expected_sorted_cells)

    for key, value in actual_cells.items():
        # check correct sorting
        expected_sorted_info = expected_sorted_cells[key]
        assert value.row == expected_sorted_info.row
        assert value.col == expected_sorted_info.col

        # check styling
        expected_styled_info = expected_styled_cells[key]
        assert value.cell.css == expected_styled_info.cell.css


def _map_cells_by_unique_display_value(table: TableFrame) -> dict[str, CellInfo]:
    result = {}
    for ri, row in enumerate(table.cells):
        for ci, cell in enumerate(row):
            if cell.value in result:
                raise KeyError("All cell elements must have a unique value.")
            result[cell.value] = CellInfo(cell, ri, ci)
    assert len(result) > 0
    return result
