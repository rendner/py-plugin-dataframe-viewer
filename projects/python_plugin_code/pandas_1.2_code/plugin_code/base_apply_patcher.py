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
from plugin_code.apply_args import ApplyArgs

# == copy after here ==
from pandas import DataFrame, Series
from pandas.io.formats.style import Styler
from typing import Any, Union
from pandas.core.indexing import (
    non_reducing_slice,
)


class BaseApplyPatcher:

    def __init__(self, data: DataFrame, apply_args: ApplyArgs, func_kwargs: dict):
        self._apply_args = apply_args
        self._func_kwargs = func_kwargs
        self._subset_data = self._evaluate_subset_data(data, apply_args.subset())

    def apply_to_styler(self, chunk_styler: Styler):
        chunk_subset = self._evaluate_chunk_subset(chunk_styler.data)
        chunk_styler.apply(self.__exec_patched_func_guard, axis=self._apply_args.axis(), subset=chunk_subset)

    def __exec_patched_func_guard(self, chunk: Union[DataFrame, Series]) -> Any:
        if chunk.empty:
            return chunk
        else:
            return self._exec_patched_func(chunk)

    def _exec_patched_func(self, chunk: Union[DataFrame, Series]) -> Any:
        pass

    @staticmethod
    def _evaluate_subset_data(data: DataFrame, subset) -> DataFrame:
        subset = slice(None) if subset is None else subset
        subset = non_reducing_slice(subset)
        return data.loc[subset]

    def _evaluate_chunk_subset(self, chunk: DataFrame):
        chunk_subset = None
        if self._apply_args.subset() is not None:
            chunk_subset = (
                chunk.index.intersection(self._subset_data.index),
                chunk.columns.intersection(self._subset_data.columns))
        return chunk_subset

    def _get_parent(self, chunk: DataFrame):
        axis = self._apply_args.axis()
        if axis == 0 or axis == "index":
            return self._subset_data[chunk.name]
        elif axis == 1 or axis == "columns":
            return self._subset_data.loc[chunk.name]
        else:
            return self._subset_data

    def _get_key(self, chunk: DataFrame) -> str:
        axis = self._apply_args.axis()
        if axis == 0 or axis == "index":
            return chunk.name
        elif axis == 1 or axis == "columns":
            return chunk.name
        else:
            return "______df"
