#  Copyright 2022 cms.rendner (Daniel Schmidt)
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

# == copy after here ==
from typing import Callable, Optional, Union

from pandas import DataFrame, Series
from pandas._typing import Axis


class ChunkParentProvider:
    def __init__(self, style_func: Callable, axis: Optional[Axis], subset_data: DataFrame):
        self._style_func = style_func
        self._axis = axis
        self._subset_data = subset_data

    def __call__(self, chunk_or_series_from_chunk: Union[DataFrame, Series], *args, **kwargs):
        if chunk_or_series_from_chunk.empty:
            return chunk_or_series_from_chunk

        kwargs['chunk_parent'] = self._get_parent(chunk_or_series_from_chunk)
        return self._style_func(chunk_or_series_from_chunk, *args, **kwargs)

    def _get_parent(self, chunk_or_series_from_chunk: Union[DataFrame, Series]):
        if self._axis == 0 or self._axis == "index":
            return self._subset_data[chunk_or_series_from_chunk.name]
        elif self._axis == 1 or self._axis == "columns":
            return self._subset_data.loc[chunk_or_series_from_chunk.name]
        else:
            return self._subset_data
