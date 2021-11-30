# source "highlight_between": https://github.com/pandas-dev/pandas/blob/v1.3.0/pandas/io/formats/style.py#L2339-L2446
# source "_highlight_between: https://github.com/pandas-dev/pandas/blob/v1.3.0/pandas/io/formats/style.py#L2789-L2834

from plugin_code.apply_args import ApplyArgs
from plugin_code.base_apply_patcher import BaseApplyPatcher

# == copy after here ==
import numpy as np
import pandas as pd
from pandas.io.formats.style import _validate_apply_axis_arg


class HighlightBetweenPatch(BaseApplyPatcher):

    def __init__(self, data: pd.DataFrame, apply_args: ApplyArgs, func_kwargs: dict):
        BaseApplyPatcher.__init__(self, data, apply_args, func_kwargs)

    def _exec_patched_func(self, chunk: pd.DataFrame):

        left = self._func_kwargs.get("left", None)
        right = self._func_kwargs.get("right", None)

        chunk_parent = self._get_parent(chunk)

        if np.iterable(left) and not isinstance(left, str):
            left = _validate_apply_axis_arg(
                left, "left", None, chunk_parent
            )
            # adjust shape of "left" to match shape of chunk
            left = self.__adjust_range_part(left, chunk, chunk_parent)

        if np.iterable(right) and not isinstance(right, str):
            right = _validate_apply_axis_arg(
                right, "right", None, chunk_parent
            )
            # adjust shape of "right" to match shape of chunk
            right = self.__adjust_range_part(right, chunk, chunk_parent)

        return self._apply_args.func()(chunk, **dict(self._func_kwargs, left=left, right=right))

    def __adjust_range_part(
            self,
            part,
            chunk,
            chunk_parent,
    ):
        if isinstance(chunk, pd.Series):
            return part[chunk_parent.index.get_indexer_for(chunk.index)]
        elif isinstance(chunk, pd.DataFrame) and self._apply_args.axis() is None:
            ri = chunk_parent.index.get_indexer_for(chunk.index)
            ci = chunk_parent.columns.get_indexer_for(chunk.columns)
            ri_slice = slice(ri[0], ri[-1] + 1)
            ci_slice = slice(ci[0], ci[-1] + 1)
            return part[ri_slice, ci_slice]
