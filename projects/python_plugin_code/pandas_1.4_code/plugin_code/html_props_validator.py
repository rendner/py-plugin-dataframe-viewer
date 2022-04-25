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
from plugin_code.html_props_generator import HTMLPropsGenerator

# == copy after here ==
import json

from pandas import DataFrame
from pandas.io.formats.style import Styler


class _MyJSONEncoder(json.JSONEncoder):
    def encode(self, obj):
        return super().encode(self._sanitize_dict_keys(obj))

    def default(self, obj: any) -> str:
        return str(obj)

    def _sanitize_dict_keys(self, obj):
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


def _jsonify_html_props(d: dict) -> str:
    copy = dict(d)
    if "cellstyle" in copy:
        copy["cellstyle"] = _map_by_css_selector(copy["cellstyle"])
    if "body" in copy:
        copy["body"] = _remove_hidden_row_entries(copy["body"])
    if "head" in copy:
        copy["head"] = _remove_hidden_row_entries(copy["head"])
    return json.dumps(copy, indent=2, sort_keys=True, cls=_MyJSONEncoder)


def _map_by_css_selector(cellstyle: list) -> dict:
    result = {}
    for entry in cellstyle:
        for s in entry.get('selectors', []):
            result[s] = entry['props']
    return result


def _remove_hidden_row_entries(rows: list) -> list:
    result = []
    all_empty = True
    for row in rows:
        visible_entries = list(filter(lambda e: e.get("is_visible", True), row))
        if all_empty:
            all_empty = len(visible_entries) == 0
        result.append(visible_entries)
    return [] if all_empty else result


class HTMLPropsValidationResult:
    def __init__(self, actual: str, expected: str, is_equal: bool):
        self.actual = actual
        self.expected = expected
        self.is_equal = is_equal


class AbstractHTMLPropsValidator:
    def __init__(self, html_props_generator: HTMLPropsGenerator):
        self._html_props_generator: HTMLPropsGenerator = html_props_generator

    def _validate_html_props(self, actual_html_props: dict, expected_html_props: dict,
                             write_html_on_error: bool = False) -> HTMLPropsValidationResult:
        actual_json = _jsonify_html_props(actual_html_props)
        expected_json = _jsonify_html_props(expected_html_props)

        if actual_json != expected_json and write_html_on_error:
            self.__write_html(self._html_props_generator.create_html(actual_html_props), "actual.html")
            self.__write_html(self._html_props_generator.create_html(expected_html_props), "expected.html")

        return HTMLPropsValidationResult(actual_json, expected_json, actual_json == expected_json)

    @staticmethod
    def __write_html(html: str, file_name: str):
        with open(file_name, "w") as file:
            file.write(html)

    def _append_chunk_html_props(self, chunk_props: dict, target: dict, target_row_offset: int):
        unmerged_container_props = []
        # don't expect that all known keys are always present (for downwards compatibility)
        for key in chunk_props.keys():
            if key == "uuid":
                target[key] = chunk_props[key]
            elif key == "caption":
                target[key] = chunk_props[key]
            elif key == "table_attributes":
                target[key] = chunk_props[key]
            elif key == "head":
                self._add_head_elements(chunk_props[key], target.setdefault(key, []))
            elif key == "body":
                self._add_body_elements(chunk_props[key], target.setdefault(key, []), target_row_offset)
            elif key == "cellstyle":
                self._add_new_list_entries(chunk_props[key], target.setdefault(key, []))
            elif key == "table_styles":
                self._add_new_list_entries(chunk_props[key], target.setdefault(key, []))
            elif key == "index_lengths":
                self._update_index_lengths(chunk_props[key], target.setdefault(key, {}))
            elif key == "cellstyle_columns":
                self._add_new_list_entries(chunk_props[key], target.setdefault(key, []))
            elif key == "cellstyle_index":
                self._add_new_list_entries(chunk_props[key], target.setdefault(key, []))
            else:
                if isinstance(chunk_props[key], (list, dict)):
                    unmerged_container_props.append(key)

        if len(unmerged_container_props) > 0:
            raise KeyError(f"Merge failed, unsupported keys {unmerged_container_props} found.")

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
                        if 'td' == entry.get("type", ""):
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

    @staticmethod
    def _update_index_lengths(source: dict, target: dict):
        assert isinstance(source, dict)
        assert isinstance(target, dict)
        target.update(source)


class HTMLPropsValidator(AbstractHTMLPropsValidator):
    def __init__(self, visible_data: DataFrame, styler: Styler):
        super().__init__(HTMLPropsGenerator(visible_data, styler))
        self._visible_data: DataFrame = visible_data
        self._styler: Styler = styler

    def validate(self, rows_per_chunk: int, cols_per_chunk: int, write_html_on_error: bool = False) -> HTMLPropsValidationResult:
        rows_in_frame: int = len(self._visible_data.index)
        columns_in_frame: int = len(self._visible_data.columns)

        if rows_in_frame == 0 or columns_in_frame == 0:
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
        columns_in_frame: int = len(self._visible_data.columns)

        for ri in range(0, rows_in_frame, rows_per_chunk):
            for ci in range(0, columns_in_frame, cols_per_chunk):
                chunk_html_props = self._html_props_generator.generate_props_for_chunk(
                    first_row=ri,
                    first_column=ci,
                    last_row=ri + rows_per_chunk,
                    last_column=ci + cols_per_chunk,
                    exclude_row_header=False,
                    exclude_column_header=False,
                )

                self._append_chunk_html_props(
                    chunk_props=chunk_html_props,
                    target=combined_props,
                    target_row_offset=ri,
                )

        return combined_props
