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
import json
from typing import Callable

from pandas import DataFrame
from pandas.io.formats.style import Styler

from plugin_code.html_props_generator import HTMLPropsGenerator
from plugin_code.patched_styler import PatchedStyler
from plugin_code.patched_styler_context import Region
from plugin_code.styled_data_frame_viewer_bridge import StyledDataFrameViewerBridge
from tests.helpers.asserts.table_extractor import StyledTable, TableExtractor


def create_and_assert_patched_styler_html_string(
        df: DataFrame,
        init_styler_func: Callable[[Styler], None],
        rows_per_chunk: int,
        cols_per_chunk: int,
):
    # Create two independent styler objects - to guarantee that changes on one don't affect the other one
    #
    # There is no way to copy an already initialized styler. "styler.use(other_styler.export())" doesn't duplicate
    # the full internal state of a styler (the export behavior was improved in 1.3, but it is better to create
    # two separated instances).
    styler = df.style
    init_styler_func(styler)

    patched_styler_styler = df.style
    init_styler_func(patched_styler_styler)
    patched_styler = StyledDataFrameViewerBridge.create_patched_styler(patched_styler_styler)

    _assert_render(styler, patched_styler, rows_per_chunk, cols_per_chunk)


def create_combined_html_string(
        df: DataFrame,
        init_styler_func: Callable[[Styler], None],
        rows_per_chunk: int,
        cols_per_chunk: int,
) -> StyledTable:
    patched_styler_styler = df.style
    init_styler_func(patched_styler_styler)
    patched_styler = StyledDataFrameViewerBridge.create_patched_styler(patched_styler_styler)
    return _create_render_result_for_chunks(patched_styler, rows_per_chunk, cols_per_chunk)


def _assert_render(styler: Styler, patched_styler: PatchedStyler, rows_per_chunk: int, cols_per_chunk: int):
    actual_table = _create_render_result_for_chunks(patched_styler, rows_per_chunk, cols_per_chunk)
    expected_table = _convert_to_styled_table(
        _render_unpatched(StyledDataFrameViewerBridge.create_patched_styler(styler))
    )

    if actual_table is None:
        table_structure = patched_styler.get_table_structure()
        # special case table has no columns/rows and therefore no cell values
        assert table_structure.columns_count == 0 or table_structure.rows_count == 0
        assert _count_cell_values(expected_table) == 0
        return

    # Currently style.render() doesn't exclude id based css-style rules for hidden_rows/hidden_cols which are not
    # present in the generated HTML. It is fixed in pandas 1.4 (see: https://github.com/pandas-dev/pandas/pull/43673)
    # Since they are not used anyway, it is safe to remove them from the expected result.
    _delete_unused_css_rules_with_id_selector(expected_table)

    # use a json string to compare the tables to get a nicer output if they are not equal
    assert _jsonify_table(actual_table) == _jsonify_table(expected_table)


def _jsonify_table(table: StyledTable) -> str:
    return json.dumps(table, default=lambda x: getattr(x, '__dict__', str(x)), indent=2, sort_keys=True)


def _create_render_result_for_chunks(patched_styler: PatchedStyler, rows_per_chunk: int,
                                     cols_per_chunk: int) -> StyledTable:
    result = None
    table_extractor = TableExtractor()
    table_structure = patched_styler.get_table_structure()

    rows_processed = 0
    while rows_processed < table_structure.rows_count:
        rows = min(rows_per_chunk, table_structure.rows_count - rows_processed)
        cols_in_row_processed = 0
        while cols_in_row_processed < table_structure.columns_count:
            cols = min(cols_per_chunk, table_structure.columns_count - cols_in_row_processed)
            # fetch column header only for whole first row (all other rows have the same)
            exclude_col_header = rows_processed > 0
            # fetch row header only for first col-block (all others have the same row header)
            exclude_row_header = cols_in_row_processed > 0
            chunk_html = _render_chunk(
                patched_styler,
                Region(rows_processed, cols_in_row_processed, rows, cols),
                exclude_row_header,
                exclude_col_header
            )

            extracted_table = table_extractor.extract(chunk_html)

            if result is None:
                result = extracted_table
            else:
                _merge_tables(result, extracted_table, rows_processed, rows_processed == 0, cols_in_row_processed == 0)

            cols_in_row_processed += cols
        rows_processed += rows

    return result


def _render_chunk(
        patched_styler: PatchedStyler,
        chunk_region: Region,
        exclude_row_header: bool = False,
        exclude_col_header: bool = False,  # unused in pandas 1.1 plugin code
) -> str:
    psc = patched_styler.get_context()
    html_props = HTMLPropsGenerator(psc).compute_chunk_props(
        region=chunk_region,
        exclude_row_header=exclude_row_header,
    )
    # use template of original styler
    styler = psc.get_styler()
    return styler.template.render(
        **html_props,
        encoding="utf-8",
        sparse_columns=False,
        sparse_index=False,
    )


def _render_unpatched(patched_styler: PatchedStyler) -> str:
    # This method deliberately does not use the "html_props_generator" but the original
    # "Styler::render" method to create the html string.
    styler = patched_styler.get_context().get_styler()
    styler.uuid = ''
    styler.uuid_len = 0
    styler.cell_ids = False
    # bug in pandas 1.1: we have to specify the "uuid" as arg
    return styler.render(
        encoding="utf-8",
        uuid="",
        sparse_columns=False,
        sparse_index=False,
    )


def _merge_tables(target_styled_table: StyledTable, source_styled_table: StyledTable, row_offset: int,
                  is_first_row_chunk: bool,
                  is_first_col_chunk: bool):
    source_table = source_styled_table.table
    target_table = target_styled_table.table

    if is_first_row_chunk:
        target_thead = target_table.find_first('thead')
        for i, row in enumerate(source_table.find_first('thead').children):
            for th in row.find('th'):
                if th.has_class('col_heading'):
                    target_thead.children[i].children.append(th)

    if is_first_col_chunk:
        target_table.find_first('tbody').children.extend(source_table.find_first('tbody').children)
    else:  # fetched another part of the table
        target_tbody = target_table.find_first('tbody')
        for i, row in enumerate(source_table.find_first('tbody').children):
            target_tbody.children[row_offset + i].children.extend(row.find('td'))

    target_styled_table.styles.update(source_styled_table.styles)


def _convert_to_styled_table(html: str) -> StyledTable:
    table_extractor = TableExtractor()
    result = table_extractor.extract(html)

    thead = result.table.find_first('thead')
    thead.children = [child for child in thead.children if len(child.children) > 0]

    tbody = result.table.find_first('tbody')
    tbody.children = [child for child in tbody.children if len(child.children) > 0]

    return result


def _count_cell_values(styled_table: StyledTable) -> int:
    result = 0
    for i, row in enumerate(styled_table.table.find_first('tbody').children):
        result += len(row.find('td'))
    return result


def _delete_unused_css_rules_with_id_selector(styled_table: StyledTable):
    elements_to_check = [styled_table.table]
    current_element = elements_to_check.pop()
    visited_id_selectors = set()
    while current_element is not None:
        elements_to_check.extend(current_element.children)
        for key, value in current_element.attrs.items():
            if value is None:
                continue
            if key == "id":
                visited_id_selectors.add(f'#{value}')

        if len(elements_to_check) > 0:
            current_element = elements_to_check.pop()
        else:
            current_element = None

    for k in list(styled_table.styles.keys()):
        if k not in visited_id_selectors:
            del styled_table.styles[k]
