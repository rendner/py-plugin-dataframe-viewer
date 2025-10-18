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

import polars as pl

from cms_rendner_sdfv.base.table_source import ChunkDataGenerator as BaseChunkDataGenerator
from cms_rendner_sdfv.base.types import Region, ChunkDataResponse, Cell
from cms_rendner_sdfv.polars.meta_computer import MetaComputer
from cms_rendner_sdfv.polars.visible_frame import VisibleFrame


@dataclass(frozen=True)
class FormatOptions:
    str_len: int
    cell_list_len: int


class ChunkDataGenerator(BaseChunkDataGenerator):
    def __init__(self,
                 visible_frame: VisibleFrame,
                 format_options: FormatOptions,
                 meta_computer: MetaComputer,
                 ):
        super().__init__(visible_frame.region)
        self.__visible_frame = visible_frame
        self.__format_options = format_options
        self.__meta_computer = meta_computer

    def _compute_cells(self, region: Region, response: ChunkDataResponse):
        with pl.Config() as cfg:
            cfg.set_fmt_str_lengths(self.__format_options.str_len)
            cfg.set_fmt_table_cell_list_len(self.__format_options.cell_list_len)

            response.cells = []
            is_first_cols_iteration = True
            for c in range(region.cols):
                series = self.__visible_frame.series_at(region.first_col + c)
                for r, row_in_series in enumerate(self.__visible_frame.row_idx_iter(region)):
                    raw_v = series.item(row_in_series)
                    v = series._s.get_fmt(row_in_series, self.__format_options.str_len)
                    if v[0] == '"':
                        v = v[1:]
                    if v[-1] == '"':
                        v = v[0:-1]

                    org_col_idx = self.__visible_frame.get_col_index_in_source_frame(region.first_col + c)
                    cell = Cell(
                        value=v,
                        meta=self.__meta_computer.compute_cell_meta(org_col_idx, raw_v),
                    )

                    if is_first_cols_iteration:
                        response.cells.append([cell])
                    else:
                        response.cells[r].append(cell)

                is_first_cols_iteration = False
