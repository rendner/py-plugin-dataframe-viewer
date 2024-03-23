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
from typing import List, Tuple, Dict

import numpy as np
from pandas import DataFrame, Series

from cms_rendner_sdfv.base.constants import DESCRIBE_COL_MAX_STR_LEN
from cms_rendner_sdfv.base.helpers import truncate_str
from cms_rendner_sdfv.base.table_source import AbstractVisibleFrame
from cms_rendner_sdfv.base.types import Region


class VisibleColumnInfo:
    def __init__(self, column: Series):
        self.__column = column

    @property
    def dtype(self):
        return self.__column.dtype

    def describe(self) -> Dict[str, str]:
        try:
            return {
                k: truncate_str(str(v), DESCRIBE_COL_MAX_STR_LEN)
                for k, v in self.__column.describe().to_dict().items()
            }
        except TypeError as e:
            return {'error': str(e)}


class VisibleFrame(AbstractVisibleFrame):
    def __init__(self, source_frame: DataFrame):
        super().__init__(Region(0, 0, len(source_frame.index), len(source_frame.columns)))
        self._source_frame = source_frame

    @property
    def index_names(self) -> list:
        return self._source_frame.index.names

    @property
    def column_names(self) -> list:
        return self._source_frame.columns.names

    def cell_value_at(self, row: int, col: int):
        return self._source_frame.iat[row, col]

    def column_at(self, col: int):
        return self._source_frame.columns[col]

    def index_at(self, row: int):
        return self._source_frame.index[row]

    def get_column_info(self, col: int) -> VisibleColumnInfo:
        return VisibleColumnInfo(self._source_frame.iloc[:, col])

    def to_frame(self, region: Region) -> DataFrame:
        r = self.region.get_bounded_region(region)
        return self._source_frame.iloc[
               r.first_row:r.first_row + r.rows,
               r.first_col:r.first_col + r.cols,
               ]

    def to_source_frame_cell_coordinates(self, row: int, col: int):
        return row, col

    def get_column_indices(self, part_start: int, max_columns: int):
        r = self.region.get_bounded_region(Region(part_start, 0, max_columns, 0))
        return list(range(r.first_row, r.first_row + r.rows))


class MappedVisibleFrame(VisibleFrame):
    def __init__(self, source_frame: DataFrame, visible_rows: np.ndarray, visible_cols: np.ndarray):
        super().__init__(source_frame)
        self.region = Region(0, 0, len(visible_rows), len(visible_cols))
        self.__i_rows = visible_rows
        self.__i_cols = visible_cols

    def cell_value_at(self, row: int, col: int):
        return self._source_frame.iat[self.__i_rows[row], self.__i_cols[col]]

    def column_at(self, col: int):
        return self._source_frame.columns[self.__i_cols[col]]

    def index_at(self, row: int):
        return self._source_frame.index[self.__i_rows[row]]

    def get_column_info(self, col: int) -> VisibleColumnInfo:
        return VisibleColumnInfo(self._source_frame.iloc[self.__i_rows, self.__i_cols[col]])

    def to_frame(self, region: Region) -> DataFrame:
        r = self.region.get_bounded_region(region)
        i_rows = self.__i_rows[r.first_row:r.first_row + r.rows]
        i_cols = self.__i_cols[r.first_col:r.first_col + r.cols]
        return self._source_frame.iloc[i_rows, i_cols]

    def to_source_frame_cell_coordinates(self, row: int, col: int) -> Tuple[int, int]:
        return self.__i_rows[row], self.__i_cols[col]

    def get_column_indices(self, part_start: int, max_columns: int) -> List[int]:
        return list(self.__i_cols[part_start:part_start + max_columns])
