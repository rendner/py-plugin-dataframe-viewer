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
from typing import Any, List

from cms_rendner_sdfv.base.table_source import ChunkDataGenerator as BaseChunkDataGenerator
from cms_rendner_sdfv.base.types import Region, Cell, ChunkDataResponse
from cms_rendner_sdfv.pandas.frame.frame_value_formatter import FrameValueFormatter
from cms_rendner_sdfv.pandas.shared.meta_computer import MetaComputer
from cms_rendner_sdfv.pandas.shared.visible_frame import VisibleFrame


class ChunkDataGenerator(BaseChunkDataGenerator):
    def __init__(self,
                 visible_frame: VisibleFrame,
                 formatter: FrameValueFormatter,
                 meta_computer: MetaComputer,
                 ):
        super().__init__(visible_frame.region)
        self.__visible_frame = visible_frame
        self.__formatter = formatter
        self.__meta_computer = meta_computer

    def _compute_cells(self, region: Region, response: ChunkDataResponse):
        response.cells = []
        col_range = range(region.cols)
        for r in range(region.rows):
            row_cells = []
            response.cells.append(row_cells)
            for c in col_range:
                row_cells.append(self.__chunk_cell_value_at(region, r, c))

    def _compute_row_headers(self, region: Region, response: ChunkDataResponse):
        response.row_headers = []
        for r in range(region.rows):
            response.row_headers.append(self.__chunk_row_labels_at(region, r))

    def __chunk_cell_value_at(self, region: Region, row: int, col: int) -> Cell:
        value = self.__visible_frame.cell_value_at(
            region.first_row + row,
            region.first_col + col,
        )
        _, org_col = self.__visible_frame.to_source_frame_cell_coordinates(region.first_row + row, region.first_col + col)
        meta = self.__meta_computer.compute_cell_meta(org_col, value)
        return Cell(value=self.__formatter.format_cell(value), meta=meta)

    def __chunk_row_labels_at(self, region: Region, row: int) -> List[Any]:
        labels = self.__visible_frame.row_labels_at(region.first_row + row)
        return [self.__formatter.format_index(lbl) for lbl in labels]
