#  Copyright 2021-2024 cms.rendner (Daniel Schmidt)
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
import os
from typing import List

import polars as pl

from cms_rendner_sdfv.base.constants import CELL_MAX_STR_LEN
from cms_rendner_sdfv.base.table_source import AbstractTableFrameGenerator
from cms_rendner_sdfv.base.types import Region, TableFrame, TableFrameCell, TableFrameColumn
from cms_rendner_sdfv.polars.constants import CELL_MAX_LIST_LEN
from cms_rendner_sdfv.polars.visible_frame import VisibleFrame, Chunk


class TableFrameGenerator(AbstractTableFrameGenerator):
    def __init__(self, visible_frame: VisibleFrame):
        super().__init__(visible_frame)

    def generate(self,
                 region: Region = None,
                 exclude_row_header: bool = False,
                 exclude_col_header: bool = False,
                 ) -> TableFrame:
        chunk = self._visible_frame.get_chunk(region)

        columns = [] if exclude_col_header else self._extract_columns(chunk)
        cells = self._extract_cells(chunk)

        return TableFrame(
            index_labels=None,
            columns=columns,
            legend=None,
            cells=cells,
        )

    @staticmethod
    def _extract_columns(chunk: Chunk) -> List[TableFrameColumn]:
        result: List[TableFrameColumn] = []

        for c in range(chunk.region.cols):
            series = chunk.series_at(c)
            result.append(
                TableFrameColumn(
                    dtype=str(series.dtype),
                    labels=[series.name],
                    describe=chunk.describe(series),
                )
            )

        return result

    @staticmethod
    def _extract_cells(chunk: Chunk) -> List[List[TableFrameCell]]:
        result: List[List[TableFrameCell]] = []

        if chunk.region.is_empty():
            return result

        with pl.Config() as cfg:
            str_len = int(os.environ.get("POLARS_FMT_STR_LEN", str(CELL_MAX_STR_LEN)))
            cfg.set_fmt_str_lengths(min(str_len, CELL_MAX_STR_LEN))

            cell_list_len = int(os.environ.get("POLARS_FMT_TABLE_CELL_LIST_LEN", str(CELL_MAX_LIST_LEN)))
            cfg.set_fmt_table_cell_list_len(min(cell_list_len, CELL_MAX_LIST_LEN))

            for c in range(chunk.region.cols):
                series = chunk.series_at(c)
                should_create_row = not result
                for r, row_in_series in enumerate(chunk.row_idx_iter()):
                    v = series._s.get_fmt(row_in_series, str_len)
                    if v[0] == '"':
                        v = v[1:]
                    if v[-1] == '"':
                        v = v[0:-1]

                    if should_create_row:
                        result.append([TableFrameCell(v)])
                    else:
                        result[r].append(TableFrameCell(v))

        return result
