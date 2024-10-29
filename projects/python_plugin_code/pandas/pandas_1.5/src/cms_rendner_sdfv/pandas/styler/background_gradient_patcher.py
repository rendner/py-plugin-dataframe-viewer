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
from collections.abc import Sequence
from typing import Optional, Union, Dict, Tuple

import numpy as np
from pandas import DataFrame, Series
from pandas.io.formats.style import _validate_apply_axis_arg

from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo
from cms_rendner_sdfv.pandas.styler.todo_patcher import TodoPatcher


# background_gradient: https://github.com/pandas-dev/pandas/blob/v1.5.0/pandas/io/formats/style.py#L2995-L3141
class BackgroundGradientPatcher(TodoPatcher):

    def __init__(self, org_frame: DataFrame, todo: StylerTodo):
        super().__init__(org_frame, todo)
        self.__computed_params_cache: Dict[str, Tuple[float, float, Sequence]] = {}

    def unlink(self):
        super().unlink()
        self.__computed_params_cache = None

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

        vmin, vmax, gmap = self.__get_or_compute_parameters(chunk_parent, kwargs)

        # Adjust "gmap" for chunk.
        # "gmap" was calculated from "chunk_parent" and an already cached value can be reused to
        # compute the gmap for a chunk, even if the DataFrame is sorted or filtered later.
        chunk_gmap = self.__extract_chunk_gmap_from_chunk_parent_gmap(gmap, chunk_or_series_from_chunk, chunk_parent)

        return self.todo.apply_args.style_func(
            chunk_or_series_from_chunk,
            **dict(kwargs, vmin=vmin, vmax=vmax, gmap=chunk_gmap),
        )

    def __get_or_compute_parameters(self,
                                    chunk_parent: Union[DataFrame, Series],
                                    kwargs: Dict,
                                    ) -> Tuple[float, float, Sequence]:
        cache_key = "frame"
        if isinstance(chunk_parent, Series):
            cache_key = chunk_parent.name

        params = self.__computed_params_cache.get(cache_key, None)

        if params is None:
            params = self.__compute_params(chunk_parent, kwargs)
            self.__computed_params_cache[cache_key] = params

        return params

    @staticmethod
    def __compute_params(chunk_parent: Union[DataFrame, Series], kwargs: Dict) -> Tuple[float, float, Sequence]:
        # "gmap":
        #
        # Gradient map for determining the background colors.
        # If not supplied will use the underlying data from rows, columns or frame.
        # If given as an ndarray or list-like must be an identical shape to the underlying data considering
        # axis and subset. If given as DataFrame or Series must have same index and column labels considering
        # axis and subset. If supplied, vmin and vmax should be given relative to this gradient map.

        vmin = kwargs.get("vmin", None)
        vmax = kwargs.get("vmax", None)
        gmap = kwargs.get("gmap", None)

        if gmap is None:
            gmap = chunk_parent.to_numpy(dtype=float)
        else:
            gmap = _validate_apply_axis_arg(gmap, "gmap", float, chunk_parent)

        if vmin is None:
            vmin = np.nanmin(gmap)
        if vmax is None:
            vmax = np.nanmax(gmap)

        return vmin, vmax, gmap

    @staticmethod
    def __extract_chunk_gmap_from_chunk_parent_gmap(gmap: Sequence,
                                                    chunk_or_series_from_chunk: Union[DataFrame, Series],
                                                    chunk_parent: Union[DataFrame, Series],
                                                    ) -> Sequence:
        # Note:
        # "gmap" was taken from the "chunk_parent", which is unsorted and unfiltered.
        # The "chunk" is a part of the visible DataFrame, which is filtered and sorted.
        # "get_indexer_for" has to be used to extract the correct part of the "gmap" which belongs to the chunk.
        if isinstance(chunk_parent, Series):
            return gmap[chunk_parent.index.get_indexer_for(chunk_or_series_from_chunk.index)]
        elif isinstance(chunk_parent, DataFrame):
            ri = chunk_parent.index.get_indexer_for(chunk_or_series_from_chunk.index)
            ci = chunk_parent.columns.get_indexer_for(chunk_or_series_from_chunk.columns)
            if isinstance(gmap, DataFrame):
                return gmap.iloc[(ri, ci)]
            elif isinstance(gmap, np.ndarray):
                return DataFrame(data=gmap, index=chunk_parent.index, columns=chunk_parent.columns).iloc[(ri, ci)]
        return gmap
