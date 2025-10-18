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
import pandas as pd
from pandas import DataFrame, Series

from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo
from cms_rendner_sdfv.pandas.styler.todo_patcher import TodoPatcher


# highlight_max: https://github.com/pandas-dev/pandas/blob/v1.5.0/pandas/io/formats/style.py#L3395-L3436
# highlight_min: https://github.com/pandas-dev/pandas/blob/v1.5.0/pandas/io/formats/style.py#L3439-L3480
class HighlightExtremaPatcher(TodoPatcher):

    def __init__(self, org_frame: DataFrame, todo: StylerTodo):
        super().__init__(org_frame, todo)
        self.__attribute: str = todo.style_func_kwargs.get('props', 'background-color: yellow')
        self.__computed_values_cache = {}
        self._op: str = "unset"

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

        cond = chunk_or_series_from_chunk == value
        cond = cond.where(pd.notna(cond), False)
        return np.where(cond, self.__attribute, "")

    def __get_or_compute_extrema(self, chunk_parent: Union[DataFrame, Series]):
        cache_key = "frame"
        if isinstance(chunk_parent, Series):
            cache_key = chunk_parent.name

        value = self.__computed_values_cache.get(cache_key, None)

        if value is None:
            # https://github.com/pandas-dev/pandas/blob/v1.5.0/pandas/io/formats/style.py#L4031-L4040
            value = getattr(chunk_parent, self._op)(skipna=True)
            if isinstance(chunk_parent, DataFrame):  # min/max must be done twice to return scalar
                value = getattr(value, self._op)(skipna=True)

            self.__computed_values_cache[cache_key] = value

        return value


class HighlightMaxPatcher(HighlightExtremaPatcher):
    def __init__(self, org_frame: DataFrame, todo: StylerTodo):
        super().__init__(org_frame, todo)
        self._op: str = "max"


class HighlightMinPatcher(HighlightExtremaPatcher):
    def __init__(self, org_frame: DataFrame, todo: StylerTodo):
        super().__init__(org_frame, todo)
        self._op: str = "min"
