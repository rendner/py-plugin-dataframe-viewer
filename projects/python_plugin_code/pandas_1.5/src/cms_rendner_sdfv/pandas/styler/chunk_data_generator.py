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

from cms_rendner_sdfv.base.table_source import ChunkDataGenerator as BaseChunkDataGenerator
from cms_rendner_sdfv.base.types import Region, ChunkDataResponse
from cms_rendner_sdfv.pandas.styler.chunk_computer import ChunkComputer, Chunk


class ChunkDataGenerator(BaseChunkDataGenerator):
    def __init__(self, bounds: Region, chunk_computer: ChunkComputer):
        super().__init__(bounds)
        self.__chunk_computer = chunk_computer
        self.__current_chunk: Chunk = None

    def _before_generate(self, region: Region):
        self.__current_chunk = self.__chunk_computer.compute(region)

    def _after_generate(self, region: Region):
        self.__current_chunk = None

    def _compute_cells(self, region: Region, response: ChunkDataResponse):
        response.cells = []
        col_range = range(region.cols)
        for r in range(region.rows):
            row_cells = []
            response.cells.append(row_cells)
            for c in col_range:
                row_cells.append(self.__current_chunk.cell_value_at(r, c))

    def _compute_row_headers(self, region: Region, response: ChunkDataResponse):
        if not self.__current_chunk.has_row_headers:
            return
        response.row_headers = []
        for r in range(region.rows):
            response.row_headers.append(self.__current_chunk.row_labels_at(r))
