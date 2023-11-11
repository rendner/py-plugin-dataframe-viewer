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
from typing import Optional, Union

import numpy as np
from pandas import DataFrame, Series

from cms_rendner_sdfv.pandas.styler.chunk_parent_provider import ChunkParentProvider
from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo
from cms_rendner_sdfv.pandas.styler.todo_patcher import TodoPatcher


# highlight_max: https://github.com/pandas-dev/pandas/blob/v1.3.0/pandas/io/formats/style.py#L2237-L2286
# highlight_min: https://github.com/pandas-dev/pandas/blob/v1.3.0/pandas/io/formats/style.py#L2288-L2337
class HighlightExtremaPatcher(TodoPatcher):

    def __init__(self, todo: StylerTodo, op: str):
        super().__init__(todo)
        self._op: str = op
        self._attribute: str = todo.style_func_kwargs.get('props', 'background-color: yellow')

    def create_patched_todo(self, org_frame: DataFrame, chunk: DataFrame) -> Optional[StylerTodo]:
        subset_frame = self._create_subset_frame(org_frame, self._todo.apply_args.subset)
        return self._todo.builder() \
            .with_subset(self._calculate_chunk_subset(subset_frame, chunk)) \
            .with_style_func_kwargs({}) \
            .with_style_func(ChunkParentProvider(self._styling_func, self._todo.apply_args.axis, subset_frame)) \
            .build()

    def _styling_func(self,
                      chunk_or_series_from_chunk: Union[DataFrame, Series],
                      chunk_parent: Union[DataFrame, Series],
                      ):
        if chunk_or_series_from_chunk.empty:
            return chunk_or_series_from_chunk

        # original 1.3.0 impl: https://github.com/pandas-dev/pandas/blob/v1.3.0/pandas/io/formats/style.py#L2237-L2286
        # bugfix: https://github.com/pandas-dev/pandas/commit/e7e93e371b68847564b2d1d0eb7780d640e96aa8
        # latest state: https://github.com/pandas-dev/pandas/blob/1.3.x/pandas/io/formats/style.py#L2308-L2357
        value = getattr(chunk_parent, self._op)(skipna=True)

        if isinstance(chunk_or_series_from_chunk, DataFrame):  # min/max must be done twice to return scalar
            value = getattr(value, self._op)(skipna=True)
        return np.where(chunk_or_series_from_chunk == value, self._attribute, "")
