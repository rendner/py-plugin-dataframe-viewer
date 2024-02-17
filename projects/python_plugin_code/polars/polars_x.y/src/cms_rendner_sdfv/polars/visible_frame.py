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
        self._frame = frame
        self._region = region

    @property
    def region(self) -> Region:
        return self._region

    def series_at(self, offset: int) -> pl.Series:
        return self._frame.series_at(self._region.first_col + offset)

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
        return self._frame.row_idx_iter(self._region)


class VisibleFrame(AbstractVisibleFrame):
    def __init__(self, source_frame: pl.DataFrame, row_idx: Union[None, pl.Series]):
        self._source_frame = source_frame
        self._column_names = source_frame.columns
        self._row_idx = row_idx
        self._region = Region.with_frame_shape(source_frame.shape)

    @property
    def region(self) -> Region:
        return self._region

    def row_idx_iter(self, region: Region = None) -> Iterator[int]:
        region = self._sanitized_region(region)
        i = 0
        while i < region.rows:
            if self._row_idx is None:
                yield i + region.first_row
            else:
                yield self._row_idx[i + region.first_row]
            i += 1

    def series_at(self, offset: int) -> pl.Series:
        name = self._column_names[self.region.first_col + offset]
        return self._source_frame.get_column(name)

    def get_chunk(self, region: Region = None) -> Chunk:
        return Chunk(self, self._sanitized_region(region))

    def _sanitized_region(self, region: Region = None) -> Region:
        return self._region if region is None else self.region.get_bounded_region(region)
