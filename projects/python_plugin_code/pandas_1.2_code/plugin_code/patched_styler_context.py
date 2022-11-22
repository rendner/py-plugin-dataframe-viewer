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
from pandas import DataFrame, Index
from pandas.io.formats.style import Styler
from pandas.core.indexing import non_reducing_slice


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


@dataclass(frozen=True)
class SortCriteria:
    by_column: Optional[List[int]] = None
    ascending: Optional[List[bool]] = None

    def is_empty(self) -> bool:
        return self.by_column is None or len(self.by_column) == 0

    def __eq__(self, other):
        if isinstance(other, SortCriteria):
            def _equals(s: Optional[List[Any]], o: Optional[List[Any]]) -> bool:
                # None and [] are interpreted as "no sorting"
                s_len = 0 if s is None else len(s)
                o_len = 0 if o is None else len(o)
                return (s_len + o_len == 0) or s == o

            return _equals(self.by_column, other.by_column) and _equals(self.ascending, other.ascending)
        return False


@dataclass(frozen=True)
class FilterCriteria:
    index: Optional[Index] = None
    columns: Optional[Index] = None

    @staticmethod
    def from_frame(frame: Optional[DataFrame]):
        return None if frame is None else FilterCriteria(frame.index, frame.columns)

    def is_empty(self) -> bool:
        return self.index is None and self.columns is None

    def __eq__(self, other):
        if isinstance(other, FilterCriteria):
            def _equals(s: Optional[Index], o: Optional[Index]) -> bool:
                if s is None and o is None:
                    return True
                return s is not None and o is not None and s.equals(o)
            return _equals(self.columns, other.columns) and _equals(self.index, other.index)
        return False


class PatchedStylerContext:
    def __init__(self, styler: Styler, filter_criteria: Optional[FilterCriteria] = None):
        self.__styler: Styler = styler
        self.__styler_todos: List[StylerTodo] = [StylerTodo.from_tuple(t) for t in styler._todo]
        self.__sort_criteria: SortCriteria = SortCriteria()
        self.__filter_criteria: FilterCriteria = filter_criteria if filter_criteria is not None else FilterCriteria()

        self.__recompute_visible_frame()

    def is_filtered(self):
        return not self.__filter_criteria.is_empty()

    def set_sort_criteria(self, sort_by_column_index: Optional[List[int]], sort_ascending: Optional[List[bool]]):
        self.__sort_criteria = SortCriteria(sort_by_column_index, sort_ascending)
        self.__recompute_visible_frame()

    def get_org_indices_of_visible_columns(self, part_start: int, max_columns: int) -> np.ndarray:
        part = self.__visible_frame.columns[part_start:part_start + max_columns]
        return self.__styler.columns.get_indexer_for(part)

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

    def create_patched_todos(self,
                             chunk: DataFrame,
                             todos_filter: Optional[Callable[[StylerTodo], bool]] = None,
                             ) -> List[Tuple[Callable, tuple, dict]]:
        all_todos = self.get_styler_todos()
        filtered_todos = all_todos if todos_filter is None else list(filter(todos_filter, all_todos))
        return TodosPatcher().patch_todos_for_chunk(filtered_todos, self.__styler.data, chunk)

    def get_row_index_translator_for_chunk(self, chunk: DataFrame, chunk_region: Region) -> IndexTranslator:
        if self.__sort_criteria.is_empty() and self.__filter_criteria.index is None:
            return _OffsetIndexTranslator(chunk_region.first_row)
        return _SequenceIndexTranslator(self.__styler.index.get_indexer_for(chunk.index))

    def get_column_index_translator_for_chunk(self, chunk: DataFrame, chunk_region: Region) -> IndexTranslator:
        if self.__filter_criteria.columns is None and len(self.__styler.hidden_columns) == 0:
            return _OffsetIndexTranslator(chunk_region.first_col)
        return _SequenceIndexTranslator(self.__styler.columns.get_indexer_for(chunk.columns))

    def __recompute_visible_frame(self):
        self.__visible_frame = self.__sort_frame(
            self.__filter_frame(self.__frame_without_hidden_rows_and_cols(self.__styler)),
        )
        self.__visible_region = Region(0, 0, len(self.__visible_frame.index), len(self.__visible_frame.columns))

    def __sort_frame(self, frame: DataFrame) -> DataFrame:
        if self.__sort_criteria.is_empty():
            return frame
        sc = self.__sort_criteria
        return frame.sort_values(
            by=[frame.columns[i] for i in sc.by_column],
            ascending=True if sc.ascending is None or len(sc.ascending) == 0 else sc.ascending,
        )

    def __filter_frame(self, frame: DataFrame) -> DataFrame:
        if self.__filter_criteria.is_empty():
            return frame
        else:
            fc = self.__filter_criteria
            index_intersection = frame.index if fc.index is None else frame.index.intersection(fc.index)
            column_intersection = frame.columns if fc.columns is None else frame.columns.intersection(fc.columns)
            subset = index_intersection, column_intersection
            subset = slice(None) if subset is None else subset
            subset = non_reducing_slice(subset)
            return frame.loc[subset]

    @staticmethod
    def __frame_without_hidden_rows_and_cols(styler: Styler) -> DataFrame:
        if len(styler.hidden_columns) == 0:
            return styler.data
        else:
            visible_columns = np.delete(styler.columns.get_indexer_for(styler.columns), styler.hidden_columns)
            return styler.data.iloc[:, visible_columns]
