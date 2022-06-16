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
from plugin_code.styler_todo import StylerTodo

# == copy after here ==
from abc import ABC, abstractmethod
from typing import Optional, Any

from pandas import DataFrame
from pandas.core.indexing import _non_reducing_slice


class TodoPatcher(ABC):

    def __init__(self, df: DataFrame, todo: StylerTodo):
        self._todo: StylerTodo = todo
        self._subset_data: DataFrame = self._get_subset_data(df, todo.apply_args.subset)

    @abstractmethod
    def create_patched_todo(self, chunk: DataFrame) -> Optional[StylerTodo]:
        pass

    def _calculate_chunk_subset(self, chunk: DataFrame) -> Any:
        index_intersection = chunk.index.intersection(self._subset_data.index)
        column_intersection = chunk.columns.intersection(self._subset_data.columns)
        return index_intersection, column_intersection

    @staticmethod
    def _get_subset_data(df: DataFrame, subset: Optional[Any]) -> DataFrame:
        # same steps as in pandas
        # https://github.com/pandas-dev/pandas/blob/v1.1.5/pandas/io/formats/style.py#L635-L637
        subset = slice(None) if subset is None else subset
        subset = _non_reducing_slice(subset)
        return df.loc[subset]
