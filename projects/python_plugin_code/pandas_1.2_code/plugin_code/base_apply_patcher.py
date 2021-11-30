from plugin_code.apply_args import ApplyArgs

# == copy after here ==
from pandas import DataFrame
from pandas.io.formats.style import Styler
from typing import Any
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
        chunk_styler.apply(self._exec_patched_func, axis=self._apply_args.axis(), subset=chunk_subset)

    def _exec_patched_func(self, chunk: DataFrame) -> Any:
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
