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
from typing import Dict, Iterator, List, Union

import polars as pl

from cms_rendner_sdfv.base.constants import COL_STATISTIC_ENTRY_MAX_STR_LEN
from cms_rendner_sdfv.base.types import Region


class VisibleFrame:
    def __init__(self,
                 unsorted_source_frame: pl.DataFrame,
                 sorted_row_idx: Union[None, pl.Series] = None,
                 org_col_idx: Union[None, List[int]] = None,
                 ):
        self.region = Region.with_frame_shape(unsorted_source_frame.shape)
        self.__unsorted_source_frame: pl.DataFrame = unsorted_source_frame
        self.__column_names: List[str] = unsorted_source_frame.columns
        self.__sorted_row_idx: Union[None, pl.Series] = sorted_row_idx
        self.__org_col_idx: Union[None, List[int]] = org_col_idx

    def unlink(self):
        self.__unsorted_source_frame = None
        self.__column_names = None
        self.__sorted_row_idx = None
        self.__org_col_idx = None

    def row_idx_iter(self, region: Region = None) -> Iterator[int]:
        region = self.region.get_bounded_region(region)
        r = 0
        while r < region.rows:
            if self.__sorted_row_idx is None:
                yield r + region.first_row
            else:
                yield self.__sorted_row_idx[r + region.first_row]
            r += 1

    def series_at(self, col: int) -> pl.Series:
        name = self.__column_names[self.region.first_col + col]
        return self.__unsorted_source_frame.get_column(name)

    def get_column_indices(self) -> List[int]:
        if self.__org_col_idx is None:
            return list(range(self.region.cols))
        return self.__org_col_idx

    def get_col_index_in_source_frame(self, col: int) -> int:
        return col if self.__org_col_idx is None else self.__org_col_idx[col]

    def get_column_statistics(self, col_index: int) -> Dict[str, str]:
        def truncate(v) -> str:
            vs = str(v)
            # truncate too long values
            return vs if len(vs) <= COL_STATISTIC_ENTRY_MAX_STR_LEN else vs[:COL_STATISTIC_ENTRY_MAX_STR_LEN - 1] + '…'

        try:
            s = self.series_at(col_index)
            df = s.describe()
            keys = df.get_column(df.columns[0]).to_list()
            values = [truncate(v) for v in df.get_column(df.columns[1]).to_list()]
            return dict(zip(keys, values))
        except TypeError as e:
            return {'error': str(e)}
