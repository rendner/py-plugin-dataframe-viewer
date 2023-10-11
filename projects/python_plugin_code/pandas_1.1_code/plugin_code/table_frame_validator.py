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
from plugin_code.table_frame_generator import TableFrameGenerator, TableFrame
from plugin_code.patched_styler_context import PatchedStylerContext, Region

# == copy after here ==
from typing import Optional
from dataclasses import dataclass
import json


@dataclass(frozen=True)
class TableFrameValidationResult:
    actual: str
    expected: str
    is_equal: bool


class TableFrameValidator:
    def __init__(self, styler_context: PatchedStylerContext, generator: Optional[TableFrameGenerator] = None):
        self.__styler_context: PatchedStylerContext = styler_context
        self.__generator: TableFrameGenerator = TableFrameGenerator(styler_context) if generator is None else generator

    def validate(self,
                 rows_per_chunk: int,
                 cols_per_chunk: int,
                 region: Region = None,
                 ) -> TableFrameValidationResult:
        if region is None:
            region = self.__styler_context.get_region_of_visible_frame()
        else:
            region = self.__styler_context.compute_visible_intersection(region)

        if region.is_empty():
            return TableFrameValidationResult('', '', True)
        combined_table = self.__generator.generate_by_combining_chunks(rows_per_chunk, cols_per_chunk, region)
        expected_table = self.__generator.generate(region)
        combined_json = self.__jsonify_table(combined_table)
        expected_json = self.__jsonify_table(expected_table)
        return TableFrameValidationResult(combined_json, expected_json, combined_json == expected_json)

    @staticmethod
    def __jsonify_table(table: TableFrame) -> str:
        return json.dumps(table, indent=2, cls=CustomJSONEncoder)
