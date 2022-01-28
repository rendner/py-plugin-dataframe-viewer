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
from collections import OrderedDict
from typing import Callable

from pandas import DataFrame
from pandas.io.formats.style import Styler

from plugin_code.patched_styler import PatchedStyler
from tests.helpers.asserts.table_extractor import StyledTable, TableExtractor


def create_and_assert_patched_styler(
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
    patched_styler = PatchedStyler(patched_styler_styler)

    _assert_render(styler, patched_styler, rows_per_chunk, cols_per_chunk)


def _assert_render(styler: Styler, patched_styler: PatchedStyler, rows_per_chunk: int, cols_per_chunk: int):
    actual_table = _create_render_result_for_chunks(patched_styler, rows_per_chunk, cols_per_chunk)
    expected_table = _convert_to_styled_table(PatchedStyler(styler).render_unpatched())

    if actual_table is None:
        table_structure = patched_styler.get_table_structure()
        # special case table has no columns/rows and therefore no cell values
        assert table_structure.columns_count == 0 or table_structure.rows_count == 0
        assert _count_cell_values(expected_table) == 0
        return

    actual_table.styles = OrderedDict(sorted(actual_table.styles.items()))
    expected_table.styles = OrderedDict(sorted(expected_table.styles.items()))

    # use a json string to compare the tables to get a nicer output if they are not equal
    actual_table_json = json.dumps(actual_table, default=lambda x: getattr(x, '__dict__', str(x)), indent=2)
    expected_table_json = json.dumps(expected_table, default=lambda x: getattr(x, '__dict__', str(x)), indent=2)

    assert actual_table_json == expected_table_json


def _create_render_result_for_chunks(patched_styler: PatchedStyler, rows_per_chunk: int,
                                     cols_per_chunk: int) -> StyledTable:
    result = None
    table_extractor = TableExtractor()
    table_structure = patched_styler.get_table_structure()

    for ri in range(0, table_structure.rows_count, rows_per_chunk):
        for ci in range(0, table_structure.columns_count, cols_per_chunk):
            # fetch column header only for whole first row (all other rows have the same)
            exclude_col_header = ri > 0
            # fetch row header only for first col-block (all others have the same row header)
            exclude_row_header = ci > 0
            chunk_html = patched_styler.render_chunk(
                ri,
                ci,
                ri + rows_per_chunk,
                ci + cols_per_chunk,
                exclude_row_header,
                exclude_col_header
            )

            extracted_table = table_extractor.extract(chunk_html)

            if result is None:
                result = extracted_table
            else:
                _merge_tables(result, extracted_table, ri, ri == 0, ci == 0)

    return result


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

