#  Copyright 2021-2023 cms.rendner (Daniel Schmidt)
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
from typing import Optional

from cms_rendner_sdfv.base.table_source import AbstractTableFrameGenerator, AbstractTableSourceContext
from cms_rendner_sdfv.base.transforms import to_json
from cms_rendner_sdfv.base.types import Region, TableFrameValidationResult


class TableFrameValidator:
    def __init__(self, table_source_context: AbstractTableSourceContext, generator: Optional[AbstractTableFrameGenerator] = None):
        self.__table_source_context = table_source_context
        self.__generator = generator if generator is not None else table_source_context.get_table_frame_generator()

    def validate(self,
                 rows_per_chunk: int,
                 cols_per_chunk: int,
                 region: Region = None,
                 ) -> TableFrameValidationResult:
        if region is None:
            region = self.__table_source_context.get_region_of_frame()
        else:
            region = self.__table_source_context.get_region_of_frame().get_bounded_region(region)

        if region.is_empty():
            return TableFrameValidationResult('', '', True)
        combined_table = self.__generator.generate_by_combining_chunks(rows_per_chunk, cols_per_chunk, region)
        expected_table = self.__generator.generate(region)
        combined_json = to_json(combined_table, indent=2)
        expected_json = to_json(expected_table, indent=2)
        return TableFrameValidationResult(combined_json, expected_json, combined_json == expected_json)
