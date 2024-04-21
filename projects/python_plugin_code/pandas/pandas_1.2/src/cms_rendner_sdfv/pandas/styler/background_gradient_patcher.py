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
from typing import Optional, Union, Dict, Tuple

import numpy as np
from pandas import DataFrame, Series

from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo
from cms_rendner_sdfv.pandas.styler.todo_patcher import TodoPatcher


# "background_gradient": https://github.com/pandas-dev/pandas/blob/v1.2.5/pandas/io/formats/style.py#L1162-L1241
# "_background_gradient": https://github.com/pandas-dev/pandas/blob/v1.2.5/pandas/io/formats/style.py#L1244-L1307
class BackgroundGradientPatcher(TodoPatcher):

    def __init__(self, org_frame: DataFrame, todo: StylerTodo):
        super().__init__(org_frame, todo)
        self.__computed_params_cache: Dict[str, Tuple[float, float]] = {}

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

        vmin, vmax = self.__get_or_compute_parameters(chunk_parent, kwargs)

        return self.todo.apply_args.style_func(
            chunk_or_series_from_chunk,
            **dict(kwargs, vmin=vmin, vmax=vmax),
        )

    def __get_or_compute_parameters(self,
                                    chunk_parent: Union[DataFrame, Series],
                                    kwargs: Dict,
                                    ) -> Tuple[float, float]:
        cache_key = "frame"
        if isinstance(chunk_parent, Series):
            cache_key = chunk_parent.name

        params = self.__computed_params_cache.get(cache_key, None)

        if params is None:
            params = self.__compute_params(chunk_parent, kwargs)
            self.__computed_params_cache[cache_key] = params

        return params

    @staticmethod
    def __compute_params(chunk_parent: Union[DataFrame, Series], kwargs: Dict) -> Tuple[float, float]:
        vmin = kwargs.get("vmin", None)
        vmax = kwargs.get("vmax", None)

        if vmin is None or vmax is None:
            n = chunk_parent.to_numpy()
            if vmin is None:
                vmin = np.nanmin(n)
            if vmax is None:
                vmax = np.nanmax(n)

        return vmin, vmax
