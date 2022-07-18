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
from plugin_code.todos_patcher import TodosPatcher

# == copy after here ==
from typing import List, Optional, Tuple, Callable, Any
from dataclasses import dataclass
import numpy as np
from pandas import DataFrame
from pandas.io.formats.style import Styler


@dataclass(frozen=True)
class Region:
    first_row: int = 0
    first_col: int = 0
    rows: int = 0
    cols: int = 0

    def is_empty(self) -> bool:
        return self.rows == 0 or self.cols == 0

    def is_valid(self) -> bool:
        return self.first_row >= 0 and self.first_col >= 0 and self.rows >= 0 and self.cols >= 0


class PatchedStylerContext:
    def __init__(self, styler: Styler, org_context: Any = None):
        self.__styler: Styler = styler
        self.__styler_todos: Optional[List[StylerTodo]] = None

        if isinstance(org_context, PatchedStylerContext):
            self.__visible_data: DataFrame = org_context.__visible_data
            self.__visible_region = org_context.__visible_region
        else:
            self.__visible_data: DataFrame = self.__calculate_visible_data(styler)
            self.__visible_region = Region(0, 0, len(self.__visible_data.index), len(self.__visible_data.columns))

    def new_context_with_copied_styler(self):
        styler_copy = self.__styler.data.style._copy(deepcopy=True)
        return PatchedStylerContext(styler_copy, self)

    def get_visible_region(self) -> Region:
        return self.__visible_region

    def compute_visible_intersection(self, region: Region) -> Region:
        if region.is_empty():
            return region
        if self.__visible_region.is_empty():
            return self.__visible_region
        assert region.is_valid()
        first_row = min(region.first_row, self.__visible_region.rows - 1)
        first_col = min(region.first_col, self.__visible_region.cols - 1)
        rows_left = self.__visible_region.rows - (first_row if first_row == 0 else first_row + 1)
        cols_left = self.__visible_region.cols - (first_col if first_col == 0 else first_col + 1)
        rows = min(region.rows, rows_left)
        cols = min(region.cols, cols_left)
        return Region(first_row, first_col, rows, cols)

    def get_visible_data(self) -> DataFrame:
        return self.__visible_data

    def get_styler(self) -> Styler:
        return self.__styler

    def get_styler_todos(self) -> List[StylerTodo]:
        if self.__styler_todos is None:
            self.__styler_todos = [StylerTodo.from_tuple(t) for t in self.__styler._todo]
        return self.__styler_todos

    def create_patched_todos(self, chunk: DataFrame) -> List[Tuple[Callable, tuple, dict]]:
        return TodosPatcher().patch_todos_for_chunk(self.get_styler_todos(), self.__styler.data, chunk)

    @staticmethod
    def __calculate_visible_data(styler: Styler) -> DataFrame:
        if len(styler.hidden_columns) == 0:
            return styler.data
        else:
            visible_columns = np.delete(styler.columns.get_indexer_for(styler.columns), styler.hidden_columns)
            return styler.data.iloc[:, visible_columns]
