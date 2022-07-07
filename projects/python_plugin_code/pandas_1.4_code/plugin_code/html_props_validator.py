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
from plugin_code.custom_json_encoder import CustomJSONEncoder
from plugin_code.html_props_generator import HTMLPropsGenerator
from plugin_code.html_props_table_builder import HTMLPropsTable, HTMLPropsTableBuilder
from plugin_code.patched_styler_context import PatchedStylerContext, Region

# == copy after here ==
from dataclasses import dataclass
import json


@dataclass(frozen=True)
class HTMLPropsValidationResult:
    actual: str
    expected: str
    is_equal: bool


class HTMLPropsValidator:
    def __init__(self, styler_context: PatchedStylerContext):
        self.__styler_context: PatchedStylerContext = styler_context
        self.__html_props_generator: HTMLPropsGenerator = HTMLPropsGenerator(styler_context)

    def validate(self, rows_per_chunk: int, cols_per_chunk: int) -> HTMLPropsValidationResult:
        return self.validate_region(self.__styler_context.get_visible_region(), rows_per_chunk, cols_per_chunk)

    def validate_region(self,
                        region: Region,
                        rows_per_chunk: int,
                        cols_per_chunk: int,
                        ) -> HTMLPropsValidationResult:
        clamped_region = self.__styler_context.compute_visible_intersection(region)
        if clamped_region.is_empty():
            return HTMLPropsValidationResult('', '', True)

        combined_table = self.__compute_table_from_chunks(clamped_region, rows_per_chunk, cols_per_chunk)
        expected_table = self.__compute_expected_table(clamped_region)
        combined_json = self.__jsonify_table(combined_table)
        expected_json = self.__jsonify_table(expected_table)

        return HTMLPropsValidationResult(combined_json, expected_json, combined_json == expected_json)

    def __compute_expected_table(self, region: Region) -> HTMLPropsTable:
        if region == self.__styler_context.get_visible_region():
            html_props = self.__html_props_generator.compute_unpatched_props()
        else:
            html_props = self.__html_props_generator.compute_chunk_props(
                region=region,
                translate_indices=False,
            )
        props_table_generator = HTMLPropsTableBuilder()
        props_table_generator.append_props(
            html_props=html_props,
            target_row_offset=0,
            is_part_of_first_rows_in_chunk=True,
            is_part_of_first_cols_in_chunk=True,
        )
        return props_table_generator.build_table()

    def __compute_table_from_chunks(self,
                                    region: Region,
                                    rows_per_chunk: int,
                                    cols_per_chunk: int,
                                    ) -> HTMLPropsTable:
        props_table_generator = HTMLPropsTableBuilder()
        rows_processed = 0
        while rows_processed < region.rows:
            rows = min(rows_per_chunk, region.rows - rows_processed)
            cols_in_row_processed = 0
            while cols_in_row_processed < region.cols:
                cols = min(cols_per_chunk, region.cols - cols_in_row_processed)
                chunk_html_props = self.__html_props_generator.compute_chunk_props(
                    region=Region(
                        region.first_row + rows_processed,
                        region.first_col + cols_in_row_processed,
                        rows,
                        cols,
                    ),
                    exclude_row_header=cols_in_row_processed > 0,
                    exclude_col_header=rows_processed > 0,
                    translate_indices=False,
                )

                props_table_generator.append_props(
                    html_props=chunk_html_props,
                    target_row_offset=rows_processed,
                    is_part_of_first_rows_in_chunk=rows_processed == 0,
                    is_part_of_first_cols_in_chunk=cols_in_row_processed == 0,
                )

                cols_in_row_processed += cols
            rows_processed += rows

        return props_table_generator.build_table()

    @staticmethod
    def __jsonify_table(table: HTMLPropsTable) -> str:
        return json.dumps(table, indent=2, cls=CustomJSONEncoder)
