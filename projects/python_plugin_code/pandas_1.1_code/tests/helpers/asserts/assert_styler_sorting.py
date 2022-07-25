#  Copyright 2022 cms.rendner (Daniel Schmidt)
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
from typing import List, Dict, Callable, Optional

from pandas import DataFrame
from pandas.io.formats.style import Styler

from plugin_code.html_props_table_builder import HTMLPropsTable, HTMLPropsTableRowElement
from plugin_code.patched_styler import PatchedStyler
from plugin_code.patched_styler_context import Region

"""
Q: How can we test that a styled chunk is correctly sorted?
    note:
        -> the feature "style and sort afterwards" doesn't exist in pandas
        -> "sort first and style afterwards" doesn't work because sorting could break subsets specified as IndexSlice
            => a continues IndexSlice couldn't exist after sorting

    idea:
        - all cell values have to be unique
        - use a styled expected result (html-props) and a sorted expected result (html-props)
        - generate the styled and sorted actual result (html-props)
        - convert each html-props into a dict with the unique cell value as key
        - pick an actual key and compare the element attributes of it
            - displayValue
            - rowIndex
            - colIndex
            - cssStyling
"""


def create_and_assert_patched_styler_sorting(
        df: DataFrame,
        init_styler_func: Callable[[Styler], None],
        rows_per_chunk: int,
        cols_per_chunk: int,
        sort_by_column_index: List[int],
        sort_ascending: List[bool],
        init_expected_sorted_styler_func: Optional[Callable[[Styler], None]] = None,
):
    # create: expected styled
    styler = df.style
    init_styler_func(styler)
    patched_styler = PatchedStyler(styler)
    styled_table = patched_styler.compute_unpatched_html_props_table()
    expected_styled_dict = _map_cell_elements_by_unique_display_value(styled_table)

    # create: expected sorted
    sorted_styler = df.sort_values(by=[df.columns[i] for i in sort_by_column_index], ascending=sort_ascending).style
    if init_expected_sorted_styler_func is not None:
        init_expected_sorted_styler_func(sorted_styler)
    sorted_patched_styler = PatchedStyler(sorted_styler)
    expected_sorted_dict = _map_cell_elements_by_unique_display_value(
        sorted_patched_styler.compute_unpatched_html_props_table(),
    )

    # create: actual styled and sorted'
    chunk_styler = df.style
    init_styler_func(chunk_styler)
    patched_chunk_styler = PatchedStyler(chunk_styler)
    patched_chunk_styler.set_sort_criteria(sort_by_column_index, sort_ascending)
    actual_dict = _map_cell_elements_by_unique_display_value(
        _build_combined_chunk_table(
            patched_chunk_styler=patched_chunk_styler,
            rows_per_chunk=rows_per_chunk,
            cols_per_chunk=cols_per_chunk,
        ),
    )

    _compare_element_dicts(
        actual_dict=actual_dict,
        expected_styled_dict=expected_styled_dict,
        expected_sorted_dict=expected_sorted_dict,
    )


@dataclass
class ElementInfo:
    element: HTMLPropsTableRowElement
    row: int
    col: int


def _compare_element_dicts(
        actual_dict: Dict[str, ElementInfo],
        expected_styled_dict: Dict[str, ElementInfo],
        expected_sorted_dict: Dict[str, ElementInfo],
):
    assert len(actual_dict) > 0
    assert len(actual_dict) == len(expected_styled_dict)
    assert len(actual_dict) == len(expected_sorted_dict)

    for key, value in actual_dict.items():
        # check correct sorting
        expected_sorted_info = expected_sorted_dict[key]
        assert value.row == expected_sorted_info.row
        assert value.col == expected_sorted_info.col

        # check styling
        expected_styled_info = expected_styled_dict[key]
        assert value.element.css_props == expected_styled_info.element.css_props


def _map_cell_elements_by_unique_display_value(table: HTMLPropsTable) -> Dict[str, ElementInfo]:
    result = {}
    for ri, row in enumerate(table.body):
        for ci, element in enumerate(row):
            if "td" == element.type:
                if element.display_value in result:
                    raise KeyError("All cell elements must have a unique value.")
                result[element.display_value] = ElementInfo(element, ri, ci)
    assert len(result) > 0
    return result


def _build_combined_chunk_table(
        patched_chunk_styler: PatchedStyler,
        rows_per_chunk: int,
        cols_per_chunk: int,
):
    combined_table: Optional[HTMLPropsTable] = None
    region = patched_chunk_styler.get_context().get_region_of_visible_frame()

    for chunk_region in region.iterate_chunkwise(rows_per_chunk, cols_per_chunk):
        chunk_props_table = patched_chunk_styler.compute_chunk_html_props_table(
            first_row=region.first_row + chunk_region.first_row,
            first_col=region.first_col + chunk_region.first_col,
            rows=chunk_region.rows,
            cols=chunk_region.cols,
            exclude_row_header=chunk_region.first_col > 0,
            exclude_col_header=chunk_region.first_row > 0,
        )

        if combined_table is None:
            combined_table = chunk_props_table
        else:
            _add_table_part(
                table=combined_table,
                part_to_add=chunk_props_table,
                part_region=chunk_region,
            )

    return combined_table if combined_table is not None else HTMLPropsTable([], [])


def _add_table_part(
        table: HTMLPropsTable,
        part_to_add: HTMLPropsTable,
        part_region: Region,
):
    # append header elements
    if part_region.first_row == 0:
        for ri, row in enumerate(part_to_add.head):
            for element in row:
                if element.kind == "col_heading":
                    table.head[ri].append(element)

    # append body elements
    if part_region.first_col == 0:
        # first part of the row - add header and data
        table.body.extend(part_to_add.body)
    else:
        # continue rows - only add data
        for ri, row in enumerate(part_to_add.body):
            target_row = table.body[part_region.first_row + ri]
            for ei, entry in enumerate(row):
                if entry.type == 'td':
                    target_row.extend(row[ei:])
                    break
