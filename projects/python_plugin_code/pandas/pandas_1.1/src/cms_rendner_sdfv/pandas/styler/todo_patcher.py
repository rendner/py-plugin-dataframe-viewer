#  Copyright 2021-2024 cms.rendner (Daniel Schmidt)
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
from abc import ABC, abstractmethod
from typing import Optional, Callable, Any

from pandas import DataFrame
from pandas.core.indexing import _non_reducing_slice

from cms_rendner_sdfv.pandas.styler.style_func_with_chunk_parent import StyleFuncWithChunkParent
from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo, StylerTodoBuilder


class TodoPatcher(ABC):

    def __init__(self, org_frame: DataFrame, todo: StylerTodo):
        self.todo: StylerTodo = todo
        # The DataFrame slice to style by the style func - unsorted and unfiltered.
        # Style functions are always applied on the DataFrame of the Styler.
        # If a "subset" has been specified for a style function, then it
        # is applied to the DataFrame created by applying the "subset".
        self.__org_subset_frame: DataFrame = self.__compute_org_subset_frame(org_frame, todo.apply_args.subset)

    @abstractmethod
    def create_patched_todo(self, chunk: DataFrame) -> Optional[StylerTodo]:
        pass

    def _todo_builder(self, chunk: DataFrame) -> StylerTodoBuilder:
        return StylerTodoBuilder(self.todo).with_subset(self.__calculate_chunk_subset(chunk))

    def _wrap_with_chunk_parent_provider(self, style_func: Callable):
        return StyleFuncWithChunkParent(style_func, self.todo.apply_args.axis, self.__org_subset_frame)

    def __calculate_chunk_subset(self, chunk: DataFrame) -> Any:
        index_intersection = chunk.index.intersection(self.__org_subset_frame.index)
        column_intersection = chunk.columns.intersection(self.__org_subset_frame.columns)
        return index_intersection, column_intersection

    @staticmethod
    def __compute_org_subset_frame(org_frame: DataFrame, subset: Optional[Any]) -> DataFrame:
        subset_frame = org_frame

        if subset is not None:

            # same steps as in pandas
            # https://github.com/pandas-dev/pandas/blob/v1.1.5/pandas/io/formats/style.py#L635-L637
            subset = slice(None) if subset is None else subset
            subset = _non_reducing_slice(subset)
            subset_frame = org_frame.loc[subset]
            # end

            if org_frame.shape == subset_frame.shape:
                subset_frame = org_frame

        return subset_frame
