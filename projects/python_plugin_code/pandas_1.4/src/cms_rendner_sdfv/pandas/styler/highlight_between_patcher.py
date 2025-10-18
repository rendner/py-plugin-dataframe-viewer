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
from pandas.io.formats.style import _validate_apply_axis_arg

from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo
from cms_rendner_sdfv.pandas.styler.todo_patcher import TodoPatcher


# highlight_between: https://github.com/pandas-dev/pandas/blob/v1.4.0/pandas/io/formats/style.py#L3159-L3260
class HighlightBetweenPatcher(TodoPatcher):

    def __init__(self, org_frame: DataFrame, todo: StylerTodo):
        super().__init__(org_frame, todo)

    def create_patched_todo(self, chunk: DataFrame) -> Optional[StylerTodo]:
        return self._todo_builder(chunk) \
            .with_style_func(self._wrap_with_chunk_parent_provider(self._styling_func)) \
            .build()

    def _styling_func(self,
                      chunk_or_series_from_chunk: Union[DataFrame, Series],
                      chunk_parent: Union[DataFrame, Series],
                      **kwargs,
                      ):
        if chunk_or_series_from_chunk.empty:
            return chunk_or_series_from_chunk

        left = kwargs.get("left", None)
        right = kwargs.get("right", None)

        # https://github.com/pandas-dev/pandas/blob/v1.4.0/pandas/io/formats/style.py#L3603-L3648
        if np.iterable(left) and not isinstance(left, str):
            left = _validate_apply_axis_arg(left, "left", None, chunk_parent)
            # adjust "left" for chunk
            left = self.__extract_chunk_bounds_from_chunk_parent_bounds(left, chunk_or_series_from_chunk, chunk_parent)

        if np.iterable(right) and not isinstance(right, str):
            right = _validate_apply_axis_arg(right, "right", None, chunk_parent)
            # adjust "right" for chunk
            right = self.__extract_chunk_bounds_from_chunk_parent_bounds(right, chunk_or_series_from_chunk, chunk_parent)

        return self.todo.apply_args.style_func(
            chunk_or_series_from_chunk,
            **dict(kwargs, left=left, right=right),
        )

    @staticmethod
    def __extract_chunk_bounds_from_chunk_parent_bounds(bounds: np.ndarray,
                                                        chunk_or_series_from_chunk: Union[DataFrame, Series],
                                                        chunk_parent: Union[DataFrame, Series],
                                                        ) -> np.ndarray:
        # Note:
        # "bounds" was taken from the "chunk_parent", which is unsorted and unfiltered.
        # The "chunk" is a part of the visible DataFrame, which is filtered and sorted.
        # "get_indexer_for" has to be used to extract the correct part of the "bounds" which belongs to the chunk.
        if isinstance(chunk_parent, Series):
            return bounds[chunk_parent.index.get_indexer_for(chunk_or_series_from_chunk.index)]
        elif isinstance(chunk_parent, DataFrame):
            ri = chunk_parent.index.get_indexer_for(chunk_or_series_from_chunk.index)
            ci = chunk_parent.columns.get_indexer_for(chunk_or_series_from_chunk.columns)
            if isinstance(bounds, DataFrame):
                return bounds.iloc[(ri, ci)]
            elif isinstance(bounds, np.ndarray):
                return DataFrame(data=bounds, index=chunk_parent.index, columns=chunk_parent.columns).iloc[(ri, ci)]
        return bounds
