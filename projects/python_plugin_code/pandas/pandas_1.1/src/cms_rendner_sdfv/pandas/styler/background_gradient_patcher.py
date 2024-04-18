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

from cms_rendner_sdfv.pandas.styler.chunk_parent_provider import ChunkParentProvider
from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo
from cms_rendner_sdfv.pandas.styler.todo_patcher import TodoPatcher


# "background_gradient": https://github.com/pandas-dev/pandas/blob/v1.1.5/pandas/io/formats/style.py#L1024-L1103
# "_background_gradient": https://github.com/pandas-dev/pandas/blob/v1.1.5/pandas/io/formats/style.py#L1106-L1169
class BackgroundGradientPatcher(TodoPatcher):

    def __init__(self, todo: StylerTodo):
        super().__init__(todo)

    def create_patched_todo(self, org_frame: DataFrame, chunk: DataFrame) -> Optional[StylerTodo]:
        subset_frame = self._create_subset_frame(org_frame, self.todo.apply_args.subset)
        return self._todo_builder() \
            .with_subset(self._calculate_chunk_subset(subset_frame, chunk)) \
            .with_style_func(ChunkParentProvider(self._styling_func, self.todo.apply_args.axis, subset_frame)) \
            .build()

    def _styling_func(self,
                      chunk_or_series_from_chunk: Union[DataFrame, Series],
                      chunk_parent: Union[DataFrame, Series],
                      **kwargs,
                      ):
        if chunk_or_series_from_chunk.empty:
            return chunk_or_series_from_chunk

        vmin = kwargs.get("vmin", None)
        vmax = kwargs.get("vmax", None)

        if vmin is None or vmax is None:
            n = chunk_parent.to_numpy()
            if vmin is None:
                vmin = np.nanmin(n)
            if vmax is None:
                vmax = np.nanmax(n)

        return self.todo.apply_args.style_func(
            chunk_or_series_from_chunk,
            **dict(kwargs, vmin=vmin, vmax=vmax),
        )
