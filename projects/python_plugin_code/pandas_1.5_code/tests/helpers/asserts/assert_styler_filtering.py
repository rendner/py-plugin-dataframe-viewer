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
from typing import List, Dict, Callable, Optional, Union, Any

from pandas import DataFrame
from pandas.io.formats.style import Styler

from plugin_code.html_props_table_builder import HTMLPropsTable, HTMLPropsTableRowElement
from plugin_code.patched_styler import PatchedStyler
from plugin_code.patched_styler_context import Region, FilterCriteria
from plugin_code.styled_data_frame_viewer_bridge import StyledDataFrameViewerBridge

"""
Q: How can we test that a styled chunk is correctly filtered?
    note:
        -> "filter first and style afterwards" doesn't work because filtering could break expected styled output
            => filtering out min/max values results in highlighting other min/max values

    idea:
        - all cell values have to be unique
        - use a styled expected result (html-props) and a filtered expected result (html-props)
        - generate the styled and filtered actual result (html-props)
        - convert each html-props into a dict with the unique cell value as key
        - pick an actual key and compare the element attributes of it
            - displayValue
            - rowIndex
            - colIndex
            - cssStyling
        - also compare that all entries of the actual result and the filtered expected result are the same elements
"""


def create_and_assert_patched_styler_filtering(
        df: DataFrame,
        init_styler_func: Callable[[Styler], None],
        rows_per_chunk: int,
        cols_per_chunk: int,
        filter_keep_items: Optional[List[Any]],
        filter_axis: Optional[Union[str, int]],
):
    # create: expected styled
    styler = df.style
    init_styler_func(styler)
    patched_styler = StyledDataFrameViewerBridge.create_patched_styler(styler)
    styled_table = patched_styler.internal_compute_unpatched_html_props_table()
    expected_styled_dict = _map_cell_elements_by_unique_display_value(styled_table, True)

    # create: expected filtered
    filtered_styler = df.filter(items=filter_keep_items, axis=filter_axis).style
    filtered_patched_styler = StyledDataFrameViewerBridge.create_patched_styler(filtered_styler)
    expected_filtered_dict = _map_cell_elements_by_unique_display_value(
        filtered_patched_styler.internal_compute_unpatched_html_props_table(),
    )

    # create: actual styled and filtered
    chunk_styler = df.style
    init_styler_func(chunk_styler)
    filter_criteria = FilterCriteria.from_frame(df.filter(items=filter_keep_items, axis=filter_axis))
    patched_chunk_styler = StyledDataFrameViewerBridge.create_patched_styler(chunk_styler, filter_criteria)
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
        expected_filtered_dict=expected_filtered_dict,
    )


@dataclass
class ElementInfo:
    element: HTMLPropsTableRowElement
    row: int
    col: int


def _compare_element_dicts(
        actual_dict: Dict[str, ElementInfo],
        expected_styled_dict: Dict[str, ElementInfo],
        expected_filtered_dict: Dict[str, ElementInfo],
):
    assert len(expected_styled_dict) > 0
    assert len(actual_dict) == len(expected_filtered_dict)

    for key, value in actual_dict.items():
        # check correct filtering
        expected_filtered_info = expected_filtered_dict[key]
        assert value.row == expected_filtered_info.row
        assert value.col == expected_filtered_info.col

        # check styling
        expected_styled_info = expected_styled_dict[key]
        assert value.element.css_props == expected_styled_info.element.css_props


def _map_cell_elements_by_unique_display_value(table: HTMLPropsTable, assert_if_empty: bool = False) -> Dict[str, ElementInfo]:
    result = {}
    for ri, row in enumerate(table.body):
        for ci, element in enumerate(row):
            if "td" == element.type:
                if element.display_value in result:
                    raise KeyError("All cell elements must have a unique value.")
                result[element.display_value] = ElementInfo(element, ri, ci)
    if assert_if_empty:
        assert len(result) > 0
    return result


def _build_combined_chunk_table(
        patched_chunk_styler: PatchedStyler,
        rows_per_chunk: int,
        cols_per_chunk: int,
):
    combined_table: Optional[HTMLPropsTable] = None
    region = patched_chunk_styler.internal_get_context().get_region_of_visible_frame()

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
