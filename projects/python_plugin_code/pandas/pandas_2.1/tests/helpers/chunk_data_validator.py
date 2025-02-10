#  Copyright 2021-2025 cms.rendner (Daniel Schmidt)
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
from dataclasses import dataclass

from cms_rendner_sdfv.base.table_source import AbstractChunkDataGenerator, AbstractTableSourceContext
from cms_rendner_sdfv.base.transforms import to_json
from cms_rendner_sdfv.base.types import Region


@dataclass(frozen=True)
class ChunkDataValidationResult:
    actual: str
    expected: str
    is_equal: bool


class ChunkDataValidator:
    def __init__(self, chunk_region: Region, generator: AbstractChunkDataGenerator):
        self.__chunk_region = chunk_region
        self.__generator = generator

    @staticmethod
    def with_context(ctx: AbstractTableSourceContext):
        return ChunkDataValidator(ctx.visible_frame.region, ctx.get_chunk_data_generator())

    def validate(self,
                 rows_per_chunk: int,
                 cols_per_chunk: int,
                 region: Region = None,
                 ) -> ChunkDataValidationResult:
        region = self.__chunk_region.get_bounded_region(region)

        if region.is_empty():
            return ChunkDataValidationResult('', '', True)
        combined_chunk_data = self.__generator.generate_by_combining_chunks(rows_per_chunk, cols_per_chunk, region)
        expected_chunk_data = self.__generator.generate(region)
        combined_json = to_json(combined_chunk_data, indent=2)
        expected_json = to_json(expected_chunk_data, indent=2)
        return ChunkDataValidationResult(combined_json, expected_json, combined_json == expected_json)
