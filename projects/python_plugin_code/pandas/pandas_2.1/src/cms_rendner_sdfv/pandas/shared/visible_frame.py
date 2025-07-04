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
from typing import Any

from pandas import DataFrame, Series

from cms_rendner_sdfv.base.types import Region
from cms_rendner_sdfv.pandas.shared.value_formatter import ValueFormatter


class VisibleFrame:
    def __init__(self, source_frame: DataFrame):
        self.region = Region.with_frame_shape(source_frame.shape)
        self._source_frame = source_frame

    def unlink(self):
        self._source_frame = None

    def get_column_indices(self) -> list[int]:
        return list(range(self.region.cols))

    @property
    def index_names(self) -> list:
        return self._source_frame.index.names

    @property
    def column_names(self) -> list:
        return self._source_frame.columns.names

    def cell_value_at(self, row: int, col: int):
        return self._source_frame.iat[row, col]

    def row_labels_at(self, row: int) -> list[Any]:
        labels = self._source_frame.index[row]
        if self._source_frame.index.nlevels == 1:
            return [labels]
        return list(labels)

    def to_frame(self, region: Region) -> DataFrame:
        r = self.region.get_bounded_region(region)
        return self._source_frame.iloc[
               r.first_row:r.first_row + r.rows,
               r.first_col:r.first_col + r.cols,
               ]

    def to_source_frame_cell_coordinates(self, row: int, col: int):
        return row, col

    def _get_col_series(self, col_index) -> Series:
        return self._source_frame.iloc[:, col_index]

    def get_column_statistics(self, col_index: int, formatter: ValueFormatter) -> dict[str, str]:
        try:
            col_series = self._get_col_series(col_index)
            return {
                k: formatter.format_column_statistic_entry(v)
                for k, v in col_series.describe().to_dict().items()
            }
        except TypeError as e:
            return {'error': str(e)}


class MappedVisibleFrame(VisibleFrame):
    def __init__(self, source_frame: DataFrame, visible_rows: list[int], visible_cols: list[int]):
        super().__init__(source_frame)
        self.region = Region(first_row=0, first_col=0, rows=len(visible_rows), cols=len(visible_cols))
        self.__i_rows = visible_rows
        self.__i_cols = visible_cols

    def unlink(self):
        super().unlink()
        self.__i_rows = None
        self.__i_cols = None

    def cell_value_at(self, row: int, col: int):
        return self._source_frame.iat[self.__i_rows[row], self.__i_cols[col]]

    def row_labels_at(self, row: int):
        labels = self._source_frame.index[self.__i_rows[row]]
        if self._source_frame.index.nlevels == 1:
            return [labels]
        return list(labels)

    def to_frame(self, region: Region):
        r = self.region.get_bounded_region(region)
        i_rows = self.__i_rows[r.first_row:r.first_row + r.rows]
        i_cols = self.__i_cols[r.first_col:r.first_col + r.cols]
        return self._source_frame.iloc[i_rows, i_cols]

    def to_source_frame_cell_coordinates(self, row: int, col: int):
        return self.__i_rows[row], self.__i_cols[col]

    def get_column_indices(self):
        return self.__i_cols

    def _get_col_series(self, col_index) -> Series:
        return self._source_frame.iloc[self.__i_rows, self.__i_cols[col_index]]
