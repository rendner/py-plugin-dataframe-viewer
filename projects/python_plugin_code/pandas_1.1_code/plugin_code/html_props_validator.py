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
from copy import deepcopy
from dataclasses import dataclass
from typing import List, Dict, Any
import json

from pandas import DataFrame
from pandas.io.formats.style import Styler


@dataclass
class SpannedElement:
    row_span: int
    col_span: int
    element: dict


class _MyJSONEncoder(json.JSONEncoder):
    def encode(self, obj: Any):
        return super().encode(self._sanitize_dict_keys(obj))

    def default(self, obj: Any) -> str:
        return str(obj)

    def _sanitize_dict_keys(self, obj: Any):
        if isinstance(obj, dict):
            result = {}
            for key, value in obj.items():
                new_key = key
                if key is not isinstance(key, (str, int, float, bool)):
                    new_key = str(key)
                result[new_key] = self._sanitize_dict_keys(value)
            return result
        if isinstance(obj, list):
            return [self._sanitize_dict_keys(v) for v in obj]
        return obj


@dataclass(frozen=True)
class HTMLPropsValidationResult:
    actual: str
    expected: str
    is_equal: bool


class AbstractHTMLPropsValidator:
    def __init__(self, html_props_generator: HTMLPropsGenerator):
        self._html_props_generator: HTMLPropsGenerator = html_props_generator

    def _validate_html_props(self,
                             actual_html_props: dict,
                             expected_html_props: dict,
                             write_html_on_error: bool = False,
                             ) -> HTMLPropsValidationResult:
        actual_json = self.__jsonify_html_props(actual_html_props)
        expected_json = self.__jsonify_html_props(expected_html_props)

        if actual_json != expected_json and write_html_on_error:
            self.__write_html(self._html_props_generator.create_html(actual_html_props), "actual.html")
            self.__write_html(self._html_props_generator.create_html(expected_html_props), "expected.html")

        return HTMLPropsValidationResult(actual_json, expected_json, actual_json == expected_json)

    def __jsonify_html_props(self, html_props: dict) -> str:
        simplified_html_props = self._transform(html_props)
        return json.dumps(simplified_html_props, indent=2, sort_keys=True, cls=_MyJSONEncoder)

    @staticmethod
    def __write_html(html: str, file_name: str):
        with open(file_name, "w") as file:
            file.write(html)

    def _transform(self, props: dict) -> dict:
        cellstyle = props.get("cellstyle", None)
        css_dict = {}
        if cellstyle is not None:
            del props["cellstyle"]
            for entry in cellstyle:
                if len(entry["props"]) == 0:
                    continue
                for s in entry.get('selectors', []):
                    css_dict[s] = entry['props']

        result = deepcopy(props)
        result["head"] = self.__transform_rows(result.get("head", []), css_dict)
        result["body"] = self.__transform_rows(result.get("body", []), css_dict)
        return result

    def __transform_rows(self, rows: List[List[dict]], css_dict: Dict[str, str]) -> List[List[dict]]:
        # - ids are removed from the entries (not required)
        # - non-visible elements are removed (not required)
        # - "rowspan" and "colspan" are resolved (easier to compare)
        # - trailing blank headers are removed (not required)
        # - css class identifier of the entries are replaced with the corresponding css properties (easier to compare)
        transformed_rows: List[List[dict]] = []
        open_spans: Dict[int, List[SpannedElement]] = {}
        css_dict_is_empty = len(css_dict) == 0
        for row in rows:
            transformed_row = []
            transformed_rows.append(transformed_row)
            ignore_trailing_blank_row_headers = False
            for ci, element in enumerate(row):

                element_to_add = None

                if ignore_trailing_blank_row_headers:
                    if element["type"] == "th" and element.get("class", "") == "blank":
                        continue
                else:
                    ignore_trailing_blank_row_headers = element["type"] == "th" and element.get("class", "") != "blank"

                if element.get("is_visible", True):

                    transformed_element = deepcopy(element)
                    element_to_add = transformed_element

                    transformed_css = {
                        "id": css_dict.get(transformed_element.pop("id", None), None),
                        "class": []
                    }
                    css_class = transformed_element.pop("class", None)
                    if not css_dict_is_empty:
                        if css_class is not None:
                            for c in css_class.split(" "):
                                css = css_dict.get(c, None)
                                if css is not None:
                                    transformed_css["class"].append(css)
                    transformed_element["styles"] = transformed_css

                    attributes = transformed_element.get("attributes", None)
                    if attributes is not None:
                        row_span = self.__get_and_remove_span_value(attributes, "rowspan")
                        col_span = self.__get_and_remove_span_value(attributes, "colspan")
                        if len(attributes) == 0:
                            del transformed_element["attributes"]
                        if row_span > 1 or col_span > 1:
                            element_to_add = None
                            open_spans.setdefault(ci, []).append(
                                SpannedElement(row_span, col_span, transformed_element),
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

        assert len(open_spans) == 0, f"Found {len(open_spans)} non processed open spans"
        return transformed_rows

    @staticmethod
    def __get_and_remove_span_value(attributes: List[str], span_name: str) -> int:
        span = next((x for x in attributes if x.startswith(f"{span_name}=")), None)
        if span is None:
            return 1
        attributes.remove(span)
        return int(span.split("=")[1].strip('"'))

    def _append_chunk_html_props(self, chunk_props: dict, target: dict, target_row_offset: int):
        # don't expect that all known keys are always present (for downwards compatibility)
        for key in chunk_props.keys():
            if key == "head":
                self._add_head_elements(chunk_props[key], target.setdefault(key, []))
            elif key == "body":
                self._add_body_elements(chunk_props[key], target.setdefault(key, []), target_row_offset)
            elif key == "cellstyle":
                self._add_new_list_entries(chunk_props[key], target.setdefault(key, []))
            elif key == "table_styles":
                self._add_new_list_entries(chunk_props[key], target.setdefault(key, []))
            else:
                target[key] = chunk_props[key]

    @staticmethod
    def _add_head_elements(source: list, target: list):
        assert isinstance(source, list)
        assert isinstance(target, list)

        if len(target) == 0:
            target.extend(source)
        else:
            for i, row in enumerate(source):
                if len(target) <= i:
                    # first part of row
                    target.append(row)
                else:
                    # continue row
                    target_row = target[i]
                    for j, entry in enumerate(row):
                        if entry not in target_row:
                            target_row.extend(row[j:])
                            break

    @staticmethod
    def _add_body_elements(source: list, target: list, target_row_offset: int):
        assert isinstance(source, list)
        assert isinstance(target, list)

        if len(target) == 0:
            target.extend(source)
        else:
            for i, row in enumerate(source):
                if len(target) <= target_row_offset + i:
                    # first part of row
                    target.append(row)
                else:
                    # continue row
                    target_row = target[target_row_offset + i]
                    for j, entry in enumerate(row):
                        if 'td' == entry["type"]:
                            target_row.extend(row[j:])
                            break

    @staticmethod
    def _add_new_list_entries(source: list, target: list):
        assert isinstance(source, list)
        assert isinstance(target, list)

        if len(target) == 0:
            target.extend(source)
        else:
            for entry in source:
                if entry not in target:
                    target.append(entry)


class HTMLPropsValidator(AbstractHTMLPropsValidator):
    def __init__(self, visible_data: DataFrame, styler: Styler):
        super().__init__(HTMLPropsGenerator(visible_data, styler))
        self._visible_data: DataFrame = visible_data
        self._styler: Styler = styler

    def validate(self, rows_per_chunk: int, cols_per_chunk: int,
                 write_html_on_error: bool = False) -> HTMLPropsValidationResult:
        rows_in_frame: int = len(self._visible_data.index)
        cols_in_frame: int = len(self._visible_data.columns)

        if rows_in_frame == 0 or cols_in_frame == 0:
            # special case frame has no columns/rows and therefore no important html props
            return HTMLPropsValidationResult('', '', True)

        combined_html_props = self._create_combined_html_props(rows_per_chunk, cols_per_chunk)
        expected_html_props = self._html_props_generator.generate_props_unpatched()

        return self._validate_html_props(combined_html_props, expected_html_props, write_html_on_error)

    def _create_combined_html_props(
            self,
            rows_per_chunk: int,
            cols_per_chunk: int,
    ):
        combined_props: dict = {}
        rows_in_frame: int = len(self._visible_data.index)
        cols_in_frame: int = len(self._visible_data.columns)

        rows_processed = 0
        while rows_processed < rows_in_frame:
            rows = min(rows_per_chunk, rows_in_frame - rows_processed)
            cols_in_row_processed = 0
            while cols_in_row_processed < cols_in_frame:
                cols = min(cols_per_chunk, cols_in_frame - cols_in_row_processed)
                chunk_html_props = self._html_props_generator.generate_props_for_chunk(
                    region=Region(rows_processed, cols_in_row_processed, rows, cols),
                    exclude_row_header=False,
                )

                self._append_chunk_html_props(
                    chunk_props=chunk_html_props,
                    target=combined_props,
                    target_row_offset=rows_processed,
                )

                cols_in_row_processed += cols
            rows_processed += rows

        return combined_props
