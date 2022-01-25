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
from plugin_code.chunk_parent_provider import ChunkParentProvider
from plugin_code.styler_todo import StylerTodo
from plugin_code.todo_patcher import TodoPatcher

# == copy after here ==
import numpy as np
from typing import Optional, Union
from pandas import DataFrame, Series


# highlight_max: https://github.com/pandas-dev/pandas/blob/v1.4.0/pandas/io/formats/style.py#L3071-L3112
# highlight_min: https://github.com/pandas-dev/pandas/blob/v1.4.0/pandas/io/formats/style.py#L3115-L3156
class HighlightExtremaPatcher(TodoPatcher):

    def __init__(self, df: DataFrame, todo: StylerTodo, op: str):
        super().__init__(df, todo)
        self._op: str = op
        self._attribute: str = todo.style_func_kwargs.get('props', 'background-color: yellow')

    def create_patched_todo(self, chunk: DataFrame) -> Optional[StylerTodo]:
        return self._todo.copy_with(
            apply_args_subset=self._calculate_chunk_subset(chunk),
            style_func_kwargs={},
            style_func=ChunkParentProvider(
                self._styling_func,
                self._todo.apply_args.axis,
                self._subset_data,
            )
        )

    def _styling_func(self,
                      chunk_or_series_from_chunk: Union[DataFrame, Series],
                      chunk_parent: Union[DataFrame, Series],
                      ):
        if chunk_or_series_from_chunk.empty:
            return chunk_or_series_from_chunk

        # https://github.com/pandas-dev/pandas/blob/v1.4.0/pandas/io/formats/style.py#L3651-L3658
        value = getattr(chunk_parent, self._op)(skipna=True)

        if isinstance(chunk_or_series_from_chunk, DataFrame):  # min/max must be done twice to return scalar
            value = getattr(value, self._op)(skipna=True)
        return np.where(chunk_or_series_from_chunk == value, self._attribute, "")
