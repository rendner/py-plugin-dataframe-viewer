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

# source "background_gradient": https://github.com/pandas-dev/pandas/blob/v1.2.5/pandas/io/formats/style.py#L1162-L1241
# source "_background_gradient": https://github.com/pandas-dev/pandas/blob/v1.2.5/pandas/io/formats/style.py#L1244-L1307
from plugin_code.apply_args import ApplyArgs
from plugin_code.base_apply_patcher import BaseApplyPatcher

# == copy after here ==
import numpy as np
from pandas import DataFrame
from pandas.core.indexing import (
    maybe_numeric_slice,
    non_reducing_slice,
)


class BackgroundGradientPatch(BaseApplyPatcher):

    def __init__(self, data: DataFrame, apply_args: ApplyArgs, func_kwargs: dict):
        BaseApplyPatcher.__init__(self, data, apply_args, func_kwargs)

    def _exec_patched_func(self, chunk):
        vmin = self._func_kwargs.get("vmin", None)
        vmax = self._func_kwargs.get("vmax", None)

        if vmin is None or vmax is None:
            chunk_parent = self._get_parent(chunk)
            n = chunk_parent.to_numpy()
            if vmin is None:
                vmin = np.nanmin(n)
            if vmax is None:
                vmax = np.nanmax(n)

        return self._apply_args.func()(chunk, **dict(self._func_kwargs, vmin=vmin, vmax=vmax))

    @staticmethod
    def _evaluate_subset_data(data, subset):
        if subset is None:
            return data
        else:
            subset = maybe_numeric_slice(data, subset)
            subset = non_reducing_slice(subset)
            return data.loc[subset]
