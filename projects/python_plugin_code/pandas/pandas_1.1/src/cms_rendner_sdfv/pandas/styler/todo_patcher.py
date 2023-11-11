#  Copyright 2021-2023 cms.rendner (Daniel Schmidt)
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
# == copy after here ==
from abc import ABC, abstractmethod
from typing import Any, Optional

from pandas import DataFrame
from pandas.core.indexing import _non_reducing_slice

from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo


class TodoPatcher(ABC):

    def __init__(self, todo: StylerTodo):
        self._todo: StylerTodo = todo

    @abstractmethod
    def create_patched_todo(self, org_frame: DataFrame, chunk: DataFrame) -> Optional[StylerTodo]:
        pass

    @staticmethod
    def _calculate_chunk_subset(org_subset_frame: DataFrame, chunk: DataFrame) -> Any:
        index_intersection = chunk.index.intersection(org_subset_frame.index)
        column_intersection = chunk.columns.intersection(org_subset_frame.columns)
        return index_intersection, column_intersection

    @staticmethod
    def _create_subset_frame(org_frame: DataFrame, subset: Optional[Any]) -> DataFrame:
        # same steps as in pandas
        # https://github.com/pandas-dev/pandas/blob/v1.1.5/pandas/io/formats/style.py#L635-L637
        subset = slice(None) if subset is None else subset
        subset = _non_reducing_slice(subset)
        return org_frame.loc[subset]
