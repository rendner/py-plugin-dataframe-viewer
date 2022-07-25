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
from abc import ABC, abstractmethod
from typing import List, Optional, Tuple, Callable, Any
from dataclasses import dataclass
import numpy as np
from pandas import DataFrame
from pandas.io.formats.style import Styler


class IndexTranslator(ABC):
    @abstractmethod
    def translate(self, index):
        pass


class _SequenceIndexTranslator(IndexTranslator):
    def __init__(self, seq):
        super().__init__()
        self.__seq = seq

    def translate(self, index):
        return self.__seq[index]


class _OffsetIndexTranslator(IndexTranslator):
    def __init__(self, offset: int):
        super().__init__()
        self.__offset = offset

    def translate(self, index):
        return index + self.__offset


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

    def iterate_chunkwise(self, rows_per_chunk: int, cols_per_chunk: int):
        rows_processed = 0
        while rows_processed < self.rows:
            rows = min(rows_per_chunk, self.rows - rows_processed)
            cols_in_row_processed = 0
            while cols_in_row_processed < self.cols:
                cols = min(cols_per_chunk, self.cols - cols_in_row_processed)

                yield Region(rows_processed, cols_in_row_processed, rows, cols)

                cols_in_row_processed += cols
            rows_processed += rows


class PatchedStylerContext:
    def __init__(self, styler: Styler, org_context: Any = None):
        self.__styler: Styler = styler
        self.__styler_todos: Optional[List[StylerTodo]] = None
        self.__sort_by_column_index: Optional[List[int]] = None
        self.__sort_ascending: Optional[List[bool]] = None

        if isinstance(org_context, PatchedStylerContext):
            # don't copy "self.__styler_todos" (it's on purpose)
            self.__visible_frame: DataFrame = org_context.__visible_frame
            self.__visible_region = org_context.__visible_region
            self.__sort_by_column_index = org_context.__sort_by_column_index
            self.__sort_ascending = org_context.__sort_ascending
        else:
            self.__visible_frame: DataFrame = self.__sort_frame(self.__calculate_visible_frame(styler))
            self.__visible_region = Region(0, 0, len(self.__visible_frame.index), len(self.__visible_frame.columns))

    def new_context_with_copied_styler(self):
        styler_copy = self.__styler.data.style._copy(deepcopy=True)
        return PatchedStylerContext(styler_copy, self)

    def set_sort_criteria(self, sort_by_column_index: Optional[List[int]], sort_ascending: Optional[List[bool]]):
        if self.__sort_by_column_index != sort_by_column_index or self.__sort_ascending != sort_ascending:
            self.__sort_by_column_index = sort_by_column_index
            self.__sort_ascending = sort_ascending
            self.__visible_frame = self.__sort_frame(self.__calculate_visible_frame(self.__styler))

    def get_region_of_visible_frame(self) -> Region:
        return self.__visible_region

    def compute_visible_intersection(self, region: Region) -> Region:
        if region.is_empty():
            return region
        if self.__visible_region.is_empty():
            return self.__visible_region
        assert region.is_valid()
        first_row = min(region.first_row, self.__visible_region.rows - 1)
        first_col = min(region.first_col, self.__visible_region.cols - 1)
        rows_left = self.__visible_region.rows - (0 if first_row == 0 else first_row + 1)
        cols_left = self.__visible_region.cols - (0 if first_col == 0 else first_col + 1)
        rows = min(region.rows, rows_left)
        cols = min(region.cols, cols_left)
        return Region(first_row, first_col, rows, cols)

    def get_visible_frame(self) -> DataFrame:
        return self.__visible_frame

    def get_styler(self) -> Styler:
        return self.__styler

    def get_styler_todos(self) -> List[StylerTodo]:
        if self.__styler_todos is None:
            self.__styler_todos = [StylerTodo.from_tuple(t) for t in self.__styler._todo]
        return self.__styler_todos

    def create_patched_todos(self, chunk: DataFrame) -> List[Tuple[Callable, tuple, dict]]:
        return TodosPatcher().patch_todos_for_chunk(self.get_styler_todos(), self.__styler.data, chunk)

    def get_row_index_translator_for_chunk(self, chunk: DataFrame, chunk_region: Region) -> IndexTranslator:
        if self.__sort_by_column_index is None:
            return _OffsetIndexTranslator(chunk_region.first_row)
        return _SequenceIndexTranslator(self.__styler.index.get_indexer_for(chunk.index))

    def get_column_index_translator_for_chunk(self, chunk: DataFrame, chunk_region: Region) -> IndexTranslator:
        if self.__sort_by_column_index is None and len(self.__styler.hidden_columns) == 0:
            return _OffsetIndexTranslator(chunk_region.first_col)
        return _SequenceIndexTranslator(self.__styler.columns.get_indexer_for(chunk.columns))

    def __sort_frame(self, frame: DataFrame) -> DataFrame:
        if self.__sort_by_column_index is None:
            return frame
        ascending = True if self.__sort_ascending is None else self.__sort_ascending
        return frame.sort_values(
            by=[frame.columns[i] for i in self.__sort_by_column_index],
            ascending=ascending,
        )

    @staticmethod
    def __calculate_visible_frame(styler: Styler) -> DataFrame:
        if len(styler.hidden_columns) == 0:
            return styler.data
        else:
            visible_columns = np.delete(styler.columns.get_indexer_for(styler.columns), styler.hidden_columns)
            return styler.data.iloc[:, visible_columns]
