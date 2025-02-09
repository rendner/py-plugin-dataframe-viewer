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
from typing import Optional, Union

import numpy as np
from pandas import DataFrame, Series

from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo
from cms_rendner_sdfv.pandas.styler.todo_patcher import TodoPatcher


# "_highlight_handler": https://github.com/pandas-dev/pandas/blob/v1.1.5/pandas/io/formats/style.py#L1386-L1397
# "_highlight_extrema": https://github.com/pandas-dev/pandas/blob/v1.1.5/pandas/io/formats/style.py#L1400-L1418
class HighlightExtremaPatcher(TodoPatcher):

    def __init__(self, org_frame: DataFrame, todo: StylerTodo):
        super().__init__(org_frame, todo)
        self.__max: bool = todo.style_func_kwargs.get('max_', False)
        self.__attribute: str = f"background-color: {todo.style_func_kwargs.get('color', 'yellow')}"
        self.__computed_values_cache = {}

    def unlink(self):
        super().unlink()
        self.__computed_values_cache = None

    def create_patched_todo(self, chunk: DataFrame) -> Optional[StylerTodo]:
        return self._todo_builder(chunk) \
            .with_style_func_kwargs({}) \
            .with_style_func(self._wrap_with_chunk_parent_provider(self._styling_func)) \
            .build()

    def _styling_func(self,
                      chunk_or_series_from_chunk: Union[DataFrame, Series],
                      chunk_parent: Union[DataFrame, Series],
                      ):
        if chunk_or_series_from_chunk.empty:
            return chunk_or_series_from_chunk

        value = self.__get_or_compute_extrema(chunk_parent)

        if self.__max:
            extrema = chunk_or_series_from_chunk == value
        else:
            extrema = chunk_or_series_from_chunk == value

        if chunk_or_series_from_chunk.ndim == 1:
            return [self.__attribute if v else "" for v in extrema]
        else:
            return DataFrame(
                np.where(extrema, self.__attribute, ""),
                index=chunk_or_series_from_chunk.index,
                columns=chunk_or_series_from_chunk.columns
            )

    def __get_or_compute_extrema(self, chunk_parent: Union[DataFrame, Series]):
        cache_key = "frame"
        if isinstance(chunk_parent, Series):
            cache_key = chunk_parent.name

        value = self.__computed_values_cache.get(cache_key, None)

        if value is None:
            if self.__max:
                value = np.nanmax(chunk_parent.to_numpy())
            else:
                value = np.nanmin(chunk_parent.to_numpy())

            self.__computed_values_cache[cache_key] = value

        return value
