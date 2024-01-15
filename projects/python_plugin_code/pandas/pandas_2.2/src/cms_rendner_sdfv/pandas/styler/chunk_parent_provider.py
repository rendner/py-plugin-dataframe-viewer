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
from typing import Callable, Optional, Union

from pandas import DataFrame, Series
from pandas._typing import Axis


class ChunkParentProvider:
    def __init__(self, style_func: Callable, axis: Optional[Axis], subset_frame: DataFrame):
        self.__style_func = style_func
        self.__axis = axis
        self.__subset_frame = subset_frame

    def __call__(self, chunk_or_series_from_chunk: Union[DataFrame, Series], *args, **kwargs):
        if chunk_or_series_from_chunk.empty:
            return chunk_or_series_from_chunk

        kwargs['chunk_parent'] = self._get_parent(chunk_or_series_from_chunk)
        return self.__style_func(chunk_or_series_from_chunk, *args, **kwargs)

    def _get_parent(self, chunk_or_series_from_chunk: Union[DataFrame, Series]):
        if self.__axis == 0 or self.__axis == "index":
            return self.__subset_frame[chunk_or_series_from_chunk.name]
        elif self.__axis == 1 or self.__axis == "columns":
            return self.__subset_frame.loc[chunk_or_series_from_chunk.name]
        else:
            return self.__subset_frame
