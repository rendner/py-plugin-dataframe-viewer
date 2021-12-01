#  Copyright 2021 cms.rendner (Daniel Schmidt)
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
from plugin_code.apply_map_args import ApplyMapArgs

# == copy after here ==
import pandas as pd
from typing import Optional
from pandas.io.formats.style import Styler
from pandas import DataFrame
from pandas.io.formats.style_render import (
    non_reducing_slice, Subset,
)


class BaseApplyMapPatcher:

    def __init__(self, data: DataFrame, _apply_args: ApplyMapArgs, func_kwargs: dict):
        self._apply_args = _apply_args
        self._func_kwargs = func_kwargs
        self._subset_data = self._evaluate_subset_data(data, _apply_args.subset())

    def apply_to_styler(self, chunk_styler: Styler):
        chunk_subset = self._evaluate_chunk_subset(chunk_styler.data)
        chunk_styler.applymap(self._exec_patched_func, subset=chunk_subset)

    @staticmethod
    def _evaluate_subset_data(data: DataFrame, subset: Optional[Subset]) -> DataFrame:
        if subset is None:
            subset = pd.IndexSlice[:]
        subset = non_reducing_slice(subset)
        return data.loc[subset]

    def _evaluate_chunk_subset(self, chunk: DataFrame) -> Optional[Subset]:
        chunk_subset = None
        if self._apply_args.subset() is not None:
            chunk_subset = (
                chunk.index.intersection(self._subset_data.index),
                chunk.columns.intersection(self._subset_data.columns))
        return chunk_subset

    def _exec_patched_func(self, scalar):
        pass
