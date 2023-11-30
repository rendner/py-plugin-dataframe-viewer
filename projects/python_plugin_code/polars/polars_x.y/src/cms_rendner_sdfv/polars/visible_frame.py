#  Copyright 2021-2023 cms.rendner (Daniel Schmidt)
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
from typing import List, Union

import polars as pl

from cms_rendner_sdfv.base.table_source import AbstractVisibleFrame
from cms_rendner_sdfv.base.types import Region


class VisibleFrame(AbstractVisibleFrame):
    def __init__(self, source_frame: pl.DataFrame, row_idx: Union[None, pl.Series]):
        self.source_frame = source_frame
        self._row_idx = row_idx
        self._region = Region.with_frame_shape(source_frame.shape)

    @property
    def region(self) -> Region:
        return self._region

    def get_row_idx_in_source(self, region: Region) -> List[int]:
        r = range(region.first_row, region.first_row + region.rows)
        if self._row_idx is None:
            return list(r)
        return [self._row_idx[i] for i in r]

    def get_col_idx_in_source(self, region: Region) -> List[int]:
        return list(range(region.first_col, region.first_col + region.cols))
