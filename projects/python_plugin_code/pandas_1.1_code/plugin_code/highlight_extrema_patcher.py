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


# "_highlight_handler": https://github.com/pandas-dev/pandas/blob/v1.1.5/pandas/io/formats/style.py#L1386-L1397
# "_highlight_extrema": https://github.com/pandas-dev/pandas/blob/v1.1.5/pandas/io/formats/style.py#L1400-L1418
class HighlightExtremaPatcher(TodoPatcher):

    def __init__(self, df: DataFrame, todo: StylerTodo):
        super().__init__(df, todo)
        self._max: bool = todo.style_func_kwargs.get('max_', False)
        self._attribute: str = f"background-color: {todo.style_func_kwargs.get('color', 'yellow')}"

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

        if self._max:
            extrema = chunk_or_series_from_chunk == np.nanmax(chunk_parent.to_numpy())
        else:
            extrema = chunk_or_series_from_chunk == np.nanmin(chunk_parent.to_numpy())

        if chunk_or_series_from_chunk.ndim == 1:
            return [self._attribute if v else "" for v in extrema]
        else:
            return DataFrame(
                np.where(extrema, self._attribute, ""),
                index=chunk_or_series_from_chunk.index,
                columns=chunk_or_series_from_chunk.columns
            )
