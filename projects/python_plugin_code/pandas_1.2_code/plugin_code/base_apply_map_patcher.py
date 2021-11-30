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
