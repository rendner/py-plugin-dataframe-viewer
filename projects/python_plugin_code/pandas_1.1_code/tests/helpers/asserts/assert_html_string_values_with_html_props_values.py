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
from typing import Callable, List

from pandas import DataFrame
from pandas.io.formats.style import Styler

from plugin_code.html_props_table_builder import HTMLPropsTableRowElement, HTMLPropsTable
from plugin_code.patched_styler import PatchedStyler
from tests.helpers.asserts.assert_styler_html_string import create_combined_html_string
from tests.helpers.asserts.table_extractor import Element


def assert_html_string_values_with_html_props_values(
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
    html_props_table = _create_combined_html_props(
        df,
        init_styler_func,
        rows_per_chunk,
        cols_per_chunk,
    )

    html_table = create_combined_html_string(
        df,
        init_styler_func,
        rows_per_chunk,
        cols_per_chunk,
    )

    if html_table is None:
        assert len(html_props_table.head) == 0
        assert len(html_props_table.body) == 0
        return

    _assert_table_row_values(html_table.table.find_first('thead'), html_props_table.head)
    _assert_table_row_values(html_table.table.find_first('tbody'), html_props_table.body)


def _create_combined_html_props(
        df: DataFrame,
        init_styler_func: Callable[[Styler], None],
        rows_per_chunk: int,
        cols_per_chunk: int,
) -> HTMLPropsTable:
    styler = df.style
    init_styler_func(styler)
    patched_styler = PatchedStyler(styler)
    region = patched_styler.get_context().get_visible_region()
    return patched_styler.create_html_props_validator()._HTMLPropsValidator__compute_table_from_chunks(
        region,
        rows_per_chunk,
        cols_per_chunk,
    )


def _assert_table_row_values(html_rows: Element, html_props_rows: List[List[HTMLPropsTableRowElement]]):
    assert len(html_rows.children) == len(html_props_rows)
    for ri, row in enumerate(html_rows.children):
        assert len(row.children) == len(html_props_rows[ri])
        for ei, element in enumerate(row.children):
            html_props_element = html_props_rows[ri][ei]
            if html_props_element.kind != "blank":
                assert element.text == html_props_element.display_value
