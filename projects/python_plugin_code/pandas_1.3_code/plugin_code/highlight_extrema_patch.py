# source: https://github.com/pandas-dev/pandas/blob/v1.3.0/pandas/io/formats/style.py#L2237-L2286

from plugin_code.apply_args import ApplyArgs
from plugin_code.base_apply_patcher import BaseApplyPatcher

# == copy after here ==
import numpy as np
from typing import Optional
from pandas import DataFrame
from pandas.io.formats.style_render import (
    non_reducing_slice, Subset,
)


class HighlightExtremaPatch(BaseApplyPatcher):

    def __init__(self, data: DataFrame, apply_args: ApplyArgs, func_kwargs: dict, is_max: bool):
        BaseApplyPatcher.__init__(self, data, apply_args, func_kwargs)
        self.__attribute = func_kwargs.get('props', 'background-color: yellow')
        self.__is_max = is_max

    def _exec_patched_func(self, chunk: DataFrame):
        chunk_parent = self._get_parent(chunk)
        op = 'max' if self.__is_max else 'min'
        value = getattr(chunk_parent, op)(skipna=True)
        if isinstance(chunk, DataFrame):  # min/max must be done twice to return scalar
            value = getattr(value, op)(skipna=True)
        return np.where(chunk == value, self.__attribute, "")

    @staticmethod
    def _evaluate_subset_data(data: DataFrame, subset: Optional[Subset]) -> DataFrame:
        if subset is None:
            return data
        else:
            subset = non_reducing_slice(subset)
        return data.loc[subset]
