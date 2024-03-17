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
from typing import Any, Callable

import numpy as np
from pandas import DataFrame, Series

from cms_rendner_sdfv.base.constants import DESCRIBE_COL_MAX_STR_LEN
from cms_rendner_sdfv.base.helpers import truncate_str
from cms_rendner_sdfv.base.table_source import AbstractVisibleFrame
from cms_rendner_sdfv.base.types import Region


class Chunk:
    def __init__(self,
                 visible_frame: DataFrame,
                 region: Region,
                 translate_into_source_frame_cell_coordinates: Callable[[tuple[int, int]], tuple[int, int]]):
        self._visible_frame = visible_frame
        self._region = region
        self._translate_into_source_frame_cell_coordinates = translate_into_source_frame_cell_coordinates

    @property
    def region(self) -> Region:
        return self._region

    def cell_value_at(self, row_offset: int, col_offset: int) -> Any:
        return self._visible_frame.iloc[
            self.region.first_row + row_offset,
            self.region.first_col + col_offset,
        ]

    def column_at(self, offset: int) -> Any:
        return self._visible_frame.columns[self.region.first_col + offset]

    def index_at(self, offset: int) -> Any:
        return self._visible_frame.index[self.region.first_row + offset]

    def dtype_at(self, col: int) -> Any:
        return self._visible_frame.dtypes.iloc[self.region.first_col + col]

    def describe_at(self, col: int) -> dict[str, str]:
        s: Series = self._visible_frame.iloc[:, self.region.first_col + col]
        try:
            return {k: truncate_str(str(v), DESCRIBE_COL_MAX_STR_LEN) for k, v in s.describe().to_dict().items()}
        except TypeError as e:
            return {'error': str(e)}

    def index_names(self) -> list:
        return self._visible_frame.index.names

    def column_names(self) -> list:
        return self._visible_frame.columns.names

    def to_frame(self) -> DataFrame:
        r = self.region
        return self._visible_frame.iloc[
               r.first_row:r.first_row + r.rows,
               r.first_col:r.first_col + r.cols,
               ]

    def get_translate_into_source_frame_cell_coordinates(self) -> Callable[[tuple[int, int]], tuple[int, int]]:
        return self._translate_into_source_frame_cell_coordinates


class VisibleFrame(AbstractVisibleFrame):
    def __init__(self, source_frame: DataFrame, visible_rows: np.ndarray, visible_cols: np.ndarray):
        self._source_frame = source_frame
        self._i_rows = visible_rows
        self._i_cols = visible_cols
        self._region = Region(0, 0, len(visible_rows), len(visible_cols))

    @property
    def region(self) -> Region:
        return self._region

    def get_chunk(self, region: Region = None) -> Chunk:
        chunk_region = self._region if region is None else self.region.get_bounded_region(region)
        visible_frame = self._source_frame.iloc[self._i_rows, self._i_cols]

        def translate_into_source_frame_cell_coordinates(k: tuple[int, int]) -> tuple[int, int]:
            return self._i_rows[chunk_region.first_row + k[0]], self._i_cols[chunk_region.first_col + k[1]]

        return Chunk(visible_frame, chunk_region, translate_into_source_frame_cell_coordinates)

    def get_column_indices(self, part_start: int, max_columns: int) -> list[int]:
        return list(self._i_cols[part_start:part_start + max_columns])
