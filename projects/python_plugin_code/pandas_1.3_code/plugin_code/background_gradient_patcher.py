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
from typing import Optional, Union

import numpy as np
from pandas import DataFrame, Series
from pandas.io.formats.style import _validate_apply_axis_arg


# background_gradient: https://github.com/pandas-dev/pandas/blob/v1.3.0/pandas/io/formats/style.py#L1826-L1978
class BackgroundGradientPatcher(TodoPatcher):

    def __init__(self, df: DataFrame, todo: StylerTodo):
        super().__init__(df, todo)

    def create_patched_todo(self, chunk: DataFrame) -> Optional[StylerTodo]:
        return self._todo.builder() \
            .with_subset(self._calculate_chunk_subset(chunk)) \
            .with_style_func(ChunkParentProvider(self._styling_func, self._todo.apply_args.axis, self._subset_data)) \
            .build()

    def _styling_func(self,
                      chunk_or_series_from_chunk: Union[DataFrame, Series],
                      chunk_parent: Union[DataFrame, Series],
                      **kwargs,
                      ):
        if chunk_or_series_from_chunk.empty:
            return chunk_or_series_from_chunk

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

        if vmin is None or vmax is None:
            if vmin is None:
                vmin = np.nanmin(gmap)
            if vmax is None:
                vmax = np.nanmax(gmap)

        # adjust shape of gmap to match shape of chunk
        gmap = self._adjust_gmap_shape_to_chunk_shape(gmap, chunk_or_series_from_chunk, chunk_parent)

        return self._todo.apply_args.style_func(
            chunk_or_series_from_chunk,
            **dict(kwargs, vmin=vmin, vmax=vmax, gmap=gmap),
        )

    def _adjust_gmap_shape_to_chunk_shape(self,
                                          gmap: np.ndarray,
                                          chunk_or_series_from_chunk: Union[DataFrame, Series],
                                          chunk_parent: Union[DataFrame, Series],
                                          ) -> np.ndarray:
        # Note:
        # "get_indexer_for" has to be used.
        # Using "first_row, first_col, rows, and cols" which were used to create the chunk can't be used
        # to get the matching "gmap" for the chunk, because a user could have specified a subset.
        # The coordinates of the subset have to be taken into account to adjust the gmap correctly.
        # This is all done automatically by using "get_indexer_for".
        if isinstance(chunk_or_series_from_chunk, Series):
            return gmap[chunk_parent.index.get_indexer_for(chunk_or_series_from_chunk.index)]
        elif isinstance(chunk_or_series_from_chunk, DataFrame) and self._todo.apply_args.axis is None:
            ri = chunk_parent.index.get_indexer_for(chunk_or_series_from_chunk.index)
            ci = chunk_parent.columns.get_indexer_for(chunk_or_series_from_chunk.columns)
            if isinstance(gmap, DataFrame):
                return gmap.iloc[(ri, ci)]
            elif isinstance(gmap, np.ndarray):
                return DataFrame(data=gmap, index=chunk_parent.index, columns=chunk_parent.columns).iloc[(ri, ci)]
        return gmap
