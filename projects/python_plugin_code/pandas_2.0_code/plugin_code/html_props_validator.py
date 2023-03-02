#  Copyright 2023 cms.rendner (Daniel Schmidt)
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
from plugin_code.custom_json_encoder import CustomJSONEncoder
from plugin_code.html_props_generator import HTMLPropsGenerator
from plugin_code.html_props_table_builder import HTMLPropsTable
from plugin_code.html_props_table_generator import HTMLPropsTableGenerator
from plugin_code.patched_styler_context import PatchedStylerContext, Region

# == copy after here ==
from typing import Optional
from dataclasses import dataclass
import json


@dataclass(frozen=True)
class HTMLPropsValidationResult:
    actual: str
    expected: str
    is_equal: bool


class HTMLPropsValidator:
    def __init__(self, styler_context: PatchedStylerContext, props_generator: Optional[HTMLPropsGenerator] = None):
        self.__styler_context: PatchedStylerContext = styler_context
        self.__table_generator: HTMLPropsTableGenerator = HTMLPropsTableGenerator(
            props_generator if props_generator is not None else HTMLPropsGenerator(styler_context),
        )

    def test_only_validate(self, rows_per_chunk: int, cols_per_chunk: int) -> HTMLPropsValidationResult:
        region = self.__styler_context.get_region_of_visible_frame()
        if region.is_empty():
            return HTMLPropsValidationResult('', '', True)
        combined_table = self.__table_generator.compute_table_from_chunks(region, rows_per_chunk, cols_per_chunk)
        expected_table = self.__table_generator.internal_compute_unpatched_table()
        return self.__compare_tables(combined_table, expected_table)

    def validate_chunk_region(self,
                              chunk_region: Region,
                              rows_per_chunk: int,
                              cols_per_chunk: int,
                              ) -> HTMLPropsValidationResult:
        clamped_region = self.__styler_context.compute_visible_intersection(chunk_region)
        if clamped_region.is_empty():
            return HTMLPropsValidationResult('', '', True)
        combined_table = self.__table_generator.compute_table_from_chunks(clamped_region, rows_per_chunk, cols_per_chunk)
        expected_table = self.__table_generator.compute_chunk_table(clamped_region)
        return self.__compare_tables(combined_table, expected_table)

    def __compare_tables(self,
                         combined_table: HTMLPropsTable,
                         expected_table: HTMLPropsTable,
                         ) -> HTMLPropsValidationResult:
        combined_json = self.__jsonify_table(combined_table)
        expected_json = self.__jsonify_table(expected_table)
        return HTMLPropsValidationResult(combined_json, expected_json, combined_json == expected_json)

    @staticmethod
    def __jsonify_table(table: HTMLPropsTable) -> str:
        return json.dumps(table, indent=2, cls=CustomJSONEncoder)
