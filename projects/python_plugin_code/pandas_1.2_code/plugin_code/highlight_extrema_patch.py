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

# source "_highlight_handler": https://github.com/pandas-dev/pandas/blob/v1.2.5/pandas/io/formats/style.py#L1524-L1535
# source "_highlight_extrema": https://github.com/pandas-dev/pandas/blob/v1.2.5/pandas/io/formats/style.py#L1538-L1556

from plugin_code.apply_args import ApplyArgs
from plugin_code.base_apply_patcher import BaseApplyPatcher

# == copy after here ==
import numpy as np
from pandas import DataFrame
from pandas.core.indexing import (
    maybe_numeric_slice,
    non_reducing_slice,
)


class HighlightExtremaPatch(BaseApplyPatcher):

    def __init__(self, data: DataFrame, apply_args: ApplyArgs, func_kwargs: dict):
        BaseApplyPatcher.__init__(self, data, apply_args, func_kwargs)
        self.__attribute = f"background-color: {func_kwargs.get('color', 'yellow')}"

    def _exec_patched_func(self, chunk: DataFrame):
        extrema = chunk == self.__get_extrema(chunk)

        if chunk.ndim == 1:  # Series from .apply(axis=0) or axis=1
            return [self.__attribute if v else "" for v in extrema]
        else:  # from .apply(axis=None)
            return DataFrame(
                np.where(extrema, self.__attribute, ""), index=chunk.index, columns=chunk.columns
            )

    def __get_extrema(self, chunk):
        chunk_parent = self._get_parent(chunk)
        max_ = self._func_kwargs.get("max_", False)
        if max_:
            return np.nanmax(chunk_parent.to_numpy())
        else:
            return np.nanmin(chunk_parent.to_numpy())

    @staticmethod
    def _evaluate_subset_data(data: DataFrame, subset) -> DataFrame:
        if subset is None:
            return data
        else:
            subset = maybe_numeric_slice(data, subset)
            subset = non_reducing_slice(subset)
            return data.loc[subset]
