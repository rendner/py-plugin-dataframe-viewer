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
from pandas.io.formats.style import Styler
from pandas import (
    DataFrame,
    IndexSlice,
)
from pandas.core.indexing import (
    non_reducing_slice,
)


class BaseApplyMapPatcher:

    def __init__(self, data: DataFrame, apply_args: ApplyMapArgs, func_kwargs: dict):
        self._apply_args = apply_args
        self._func_kwargs = func_kwargs
        self._subset_data = self._evaluate_subset_data(data, apply_args.subset())

    def apply_to_styler(self, chunk_styler: Styler):
        chunk_subset = self._evaluate_chunk_subset(chunk_styler.data)
        chunk_styler.applymap(self._exec_patched_func, subset=chunk_subset)

    @staticmethod
    def _evaluate_subset_data(data, subset):
        if subset is None:
            subset = IndexSlice[:]
        subset = non_reducing_slice(subset)
        return data.loc[subset]

    def _evaluate_chunk_subset(self, chunk: DataFrame):
        chunk_subset = None
        if self._apply_args.subset() is not None:
            chunk_subset = (
                chunk.index.intersection(self._subset_data.index),
                chunk.columns.intersection(self._subset_data.columns))
        return chunk_subset

    def _exec_patched_func(self, scalar):
        pass
