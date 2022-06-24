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
from plugin_code.html_props_generator import HTMLPropsGenerator, Region

# == copy after here ==
from dataclasses import dataclass, asdict, is_dataclass
from typing import List, Dict, Any
import json

from pandas import DataFrame
from pandas.io.formats.style import Styler


@dataclass
class _TableElement:
    type: str
    display_value: str
    is_heading: bool
    css: dict
    attributes: List[str]


@dataclass
class _Table:
    body: List[List[_TableElement]]
    head: List[List[_TableElement]]


@dataclass
class _SpannedElement:
    row_span: int
    col_span: int
    element: _TableElement


class _MyJSONEncoder(json.JSONEncoder):
    def default(self, obj: Any) -> str:
        if is_dataclass(obj):
            return str(asdict(obj))
        return str(obj)


@dataclass(frozen=True)
class HTMLPropsValidationResult:
    actual: str
    expected: str
    is_equal: bool


class HTMLPropsValidator:
    def __init__(self, visible_data: DataFrame, styler: Styler):
        self.__html_props_generator: HTMLPropsGenerator = HTMLPropsGenerator(visible_data, styler)
        self.__visible_region = Region(0, 0, len(visible_data.index), len(visible_data.columns))

    def validate(self, rows_per_chunk: int, cols_per_chunk: int) -> HTMLPropsValidationResult:
        return self.validate_region(self.__visible_region, rows_per_chunk, cols_per_chunk)

    def validate_region(self,
                        region: Region,
                        rows_per_chunk: int,
                        cols_per_chunk: int,
                        ) -> HTMLPropsValidationResult:
        clamped_region = self.__compute_clamped_region(region)
        if clamped_region.is_empty():
            return HTMLPropsValidationResult('', '', True)

        combined_table = self.__create_combined_table_from_chunks(clamped_region, rows_per_chunk, cols_per_chunk)
        expected_table = self.__create_expected_table(clamped_region)
        combined_json = self.__jsonify_html_props(combined_table)
        expected_json = self.__jsonify_html_props(expected_table)

        return HTMLPropsValidationResult(combined_json, expected_json, combined_json == expected_json)

    def __compute_clamped_region(self, region: Region) -> Region:
        if region.is_empty():
            return region
        if self.__visible_region.is_empty():
            return self.__visible_region
        assert region.is_valid()
        first_row = min(region.first_row, self.__visible_region.rows - 1)
        first_col = min(region.first_col, self.__visible_region.cols - 1)
        rows_left = self.__visible_region.rows - (first_row if first_row == 0 else first_row + 1)
        cols_left = self.__visible_region.cols - (first_col if first_col == 0 else first_col + 1)
        rows = min(region.rows, rows_left)
        cols = min(region.cols, cols_left)
        return Region(first_row, first_col, rows, cols)

    def __create_expected_table(self, region: Region) -> _Table:
        if region == self.__visible_region:
            html_props = self.__html_props_generator.generate_props_unpatched()
        else:
            html_props = self.__html_props_generator.generate_props_for_chunk(
                region=region,
                translate_indices=False,
            )
        table: _Table = _Table([], [])
        self.__append_to_table(
            table=table,
            html_props=html_props,
            target_row_offset=0,
            is_part_of_first_rows_in_chunk=True,
            is_part_of_first_cols_in_chunk=True,
        )
        return table

    def __create_combined_table_from_chunks(self, region: Region, rows_per_chunk: int, cols_per_chunk: int):
        table: _Table = _Table([], [])

        rows_processed = 0
        while rows_processed < region.rows:
            rows = min(rows_per_chunk, region.rows - rows_processed)
            cols_in_row_processed = 0
            while cols_in_row_processed < region.cols:
                cols = min(cols_per_chunk, region.cols - cols_in_row_processed)
                chunk_html_props = self.__html_props_generator.generate_props_for_chunk(
                    region=Region(
                        region.first_row + rows_processed,
                        region.first_col + cols_in_row_processed,
                        rows,
                        cols,
                    ),
                    exclude_row_header=cols_in_row_processed > 0,
                    translate_indices=False,
                )

                self.__append_to_table(
                    table=table,
                    html_props=chunk_html_props,
                    target_row_offset=rows_processed,
                    is_part_of_first_rows_in_chunk=rows_processed == 0,
                    is_part_of_first_cols_in_chunk=cols_in_row_processed == 0,
                )

                cols_in_row_processed += cols
            rows_processed += rows

        return table

    @staticmethod
    def __jsonify_html_props(html_props: _Table) -> str:
        return json.dumps(html_props, indent=2, cls=_MyJSONEncoder)

    def __transform_rows(self, rows: List[List[dict]], css_dict: Dict[str, str]) -> List[List[_TableElement]]:
        # - ids are removed from the entries, otherwise they have to be re-indexed to be unique when combining chunks
        # - non-visible elements are removed
        # - "rowspan" and "colspan" are resolved
        # - trailing blank headers are removed
        # - css classes of the entries are replaced with the corresponding css properties, otherwise some classes
        # have to be re-indexed to be unique when combining chunks
        transformed_rows: List[List[_TableElement]] = []
        open_spans: Dict[int, List[_SpannedElement]] = {}
        css_dict_is_empty = len(css_dict) == 0

        for row in rows:

            transformed_row = []
            ignore_trailing_blank_row_headers = False

            for ci, element in enumerate(row):

                element_to_add = None
                element_type = element.get("type", "")
                element_classes = set(element.get("class", []).split(" "))
                is_header = element_type == "th"

                if ignore_trailing_blank_row_headers:
                    if is_header and "blank" in element_classes:
                        continue
                else:
                    ignore_trailing_blank_row_headers = is_header and "blank" not in element_classes

                if element.get("is_visible", True):
                    transformed_css = {"id": css_dict.get(element.get("id", None), None), "class": []}
                    if not css_dict_is_empty:
                        for c in element_classes:
                            css = css_dict.get(c, None)
                            if css is not None:
                                transformed_css["class"].append(css)

                    transformed_element = _TableElement(
                        type=element_type,
                        display_value=element.get("display_value", ""),
                        is_heading=is_header and ("col_heading" in element_classes or "row_heading" in element_classes),
                        css=transformed_css,
                        attributes=element.get("attributes", []),
                    )
                    element_to_add = transformed_element

                    attributes = transformed_element.attributes
                    if attributes is not None:
                        row_span = self.__get_and_remove_span_value(attributes, "rowspan")
                        col_span = self.__get_and_remove_span_value(attributes, "colspan")
                        if row_span > 1 or col_span > 1:
                            element_to_add = None
                            open_spans.setdefault(ci, []).append(
                                _SpannedElement(row_span, col_span, transformed_element),
                            )

                if ci in open_spans:
                    pending_col_spans = open_spans[ci]
                    remove_consumed_spans = False
                    for pending_span in pending_col_spans:
                        transformed_row.extend(pending_span.col_span * [pending_span.element])
                        pending_span.row_span -= 1
                        if pending_span.row_span < 1:
                            remove_consumed_spans = True

                    if remove_consumed_spans:
                        cleaned = [s for s in pending_col_spans if s.row_span > 0]
                        if len(cleaned) == 0:
                            del open_spans[ci]
                        else:
                            open_spans[ci] = cleaned

                if element_to_add is not None:
                    transformed_row.append(element_to_add)

            if len(transformed_row) > 0:
                transformed_rows.append(transformed_row)

        assert len(open_spans) == 0, f"Found {len(open_spans)} non processed open spans"
        return transformed_rows

    @staticmethod
    def __get_and_remove_span_value(attributes: List[str], span_name: str) -> int:
        span = next((x for x in attributes if x.startswith(f"{span_name}=")), None)
        if span is None:
            return 1
        attributes.remove(span)
        return int(span.split("=")[1].strip('"'))

    def __append_to_table(self,
                          table: _Table,
                          html_props: dict,
                          target_row_offset: int,
                          is_part_of_first_rows_in_chunk: bool,
                          is_part_of_first_cols_in_chunk: bool,
                          ):
        cellstyle = html_props.get("cellstyle", None)
        css_dict = {}
        if cellstyle is not None:
            for entry in cellstyle:
                if len(entry["props"]) == 0:
                    continue
                for s in entry.get('selectors', []):
                    css_dict[s] = entry['props']

        if is_part_of_first_rows_in_chunk:
            self.__append_head_elements(
                table,
                html_props.get("head", []),
                css_dict,
            )

        self.__append_body_elements(
            table,
            html_props.get("body", []),
            target_row_offset,
            is_part_of_first_cols_in_chunk,
            css_dict,
        )

    def __append_head_elements(self, table: _Table, head_elements: list, css_dict: dict):
        transformed_head_elements = self.__transform_rows(head_elements, css_dict)
        if len(table.head) == 0:
            table.head.extend(transformed_head_elements)
        else:
            # continue rows
            for i, row in enumerate(transformed_head_elements):
                for element in row:
                    if element.is_heading:
                        table.head[i].append(element)

    def __append_body_elements(self,
                               table: _Table,
                               body_elements: list,
                               target_row_offset: int,
                               is_part_of_first_cols_in_chunk: bool,
                               css_dict: dict,
                               ):
        transformed_body_elements = self.__transform_rows(body_elements, css_dict)
        if is_part_of_first_cols_in_chunk:
            table.body.extend(transformed_body_elements)
        else:
            # continue rows
            for i, row in enumerate(transformed_body_elements):
                target_row = table.body[target_row_offset + i]
                for j, entry in enumerate(row):
                    if 'td' == entry.type:
                        target_row.extend(row[j:])
                        break
