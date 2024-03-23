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
from typing import Union, Dict, Iterator

import polars as pl

from cms_rendner_sdfv.base.table_source import AbstractVisibleFrame
from cms_rendner_sdfv.base.types import Region


class Chunk:
    def __init__(self, frame: 'VisibleFrame', region: Region):
        self.__frame = frame
        self.region = region

    def series_at(self, offset: int) -> pl.Series:
        return self.__frame.series_at(self.region.first_col + offset)

    @staticmethod
    def describe(s: pl.Series) -> Dict[str, str]:
        def truncate(v) -> str:
            vs = str(v)
            # truncate too long values
            return vs if len(vs) <= 120 else vs[:120] + '…'
        try:
            df = s.describe()
            keys = df.get_column(df.columns[0]).to_list()
            values = [truncate(v) for v in df.get_column(df.columns[1]).to_list()]
            return dict(zip(keys, values))
        except TypeError as e:
            return {'error': str(e)}

    def row_idx_iter(self) -> Iterator[int]:
        return self.__frame.row_idx_iter(self.region)


class VisibleFrame(AbstractVisibleFrame):
    def __init__(self, source_frame: pl.DataFrame, row_idx: Union[None, pl.Series]):
        super().__init__(Region.with_frame_shape(source_frame.shape))
        self.__source_frame = source_frame
        self.__column_names = source_frame.columns
        self.__row_idx = row_idx

    def row_idx_iter(self, region: Region = None) -> Iterator[int]:
        region = self.region.get_bounded_region(region)
        r = 0
        while r < region.rows:
            if self.__row_idx is None:
                yield r + region.first_row
            else:
                yield self.__row_idx[r + region.first_row]
            r += 1

    def series_at(self, col: int) -> pl.Series:
        name = self.__column_names[self.region.first_col + col]
        return self.__source_frame.get_column(name)

    def get_chunk(self, region: Region = None) -> Chunk:
        return Chunk(self, self.region.get_bounded_region(region))
