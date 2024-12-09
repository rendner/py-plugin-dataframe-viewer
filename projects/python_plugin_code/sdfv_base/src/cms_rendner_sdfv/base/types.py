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
import dataclasses
from dataclasses import dataclass
from enum import Enum
from typing import Any, Dict, List, Tuple, Union


@dataclass(frozen=True)
class TableStructureColumn:
    dtype: str
    labels: List[str]
    # -1: if no unique column id can be provided
    id: int


@dataclass(frozen=True)
class TableStructureLegend:
    index: List[str]
    column: List[str]


@dataclass(frozen=True)
class TableStructureColumnInfo:
    columns: List[TableStructureColumn]
    legend: Union[None, TableStructureLegend]


@dataclass(frozen=True)
class TableStructure:
    org_rows_count: int
    org_columns_count: int
    rows_count: int
    columns_count: int
    fingerprint: str
    column_info: TableStructureColumnInfo


@dataclass(frozen=True)
class TableInfo:
    kind: str
    structure: TableStructure


@dataclass(frozen=True)
class TableFrameCell:
    value: str
    css: Union[None, Dict[str, str]] = None


@dataclass(frozen=True)
class TableFrame:
    index_labels: Union[None, List[List[str]]]
    cells: List[List[TableFrameCell]]


@dataclass(frozen=True)
class Region:
    first_row: int = 0
    first_col: int = 0
    rows: int = 0
    cols: int = 0

    @classmethod
    def with_frame_shape(cls, shape: Tuple[int, int]):
        return cls(rows=shape[0], cols=shape[1])

    def translate(self, row_offset: int, col_offset: int):
        return dataclasses.replace(self, first_row=self.first_row + row_offset, first_col=self.first_col + col_offset)

    def is_empty(self) -> bool:
        return self.rows == 0 or self.cols == 0

    def is_valid(self) -> bool:
        return self.first_row >= 0 and self.first_col >= 0 and self.rows >= 0 and self.cols >= 0

    @property
    def frame_shape(self) -> Tuple[int, int]:
        return self.rows, self.cols

    # The returned Regions have local coordinates relative to the iterated Region.
    # Example:
    #   list(Region(first_row=5, first_col=5, rows=5, cols=5).iterate_chunkwise(5, 5))
    # returns:
    #   [Region(first_row=0, first_col=0, rows=5, cols=5)]
    def iterate_local_chunkwise(self, rows_per_chunk: int, cols_per_chunk: int):
        if not self.is_valid():
            raise ValueError("Invalid Regions can't be iterated chunkwise.")
        if rows_per_chunk <= 0 or cols_per_chunk <= 0:
            raise ValueError(f"rows_per_chunk ({rows_per_chunk}) and cols_per_chunk ({cols_per_chunk}) must be > 0")

        rows_processed = 0
        while rows_processed < self.rows:
            rows = min(rows_per_chunk, self.rows - rows_processed)
            cols_in_row_processed = 0
            while cols_in_row_processed < self.cols:
                cols = min(cols_per_chunk, self.cols - cols_in_row_processed)

                yield Region(rows_processed, cols_in_row_processed, rows, cols)

                cols_in_row_processed += cols
            rows_processed += rows

    def get_bounded_region(self, unbound_region: Union[None, 'Region']) -> 'Region':
        if unbound_region is None:
            return self
        if not self.is_valid():
            raise ValueError("No valid bounds.")
        if not unbound_region.is_valid():
            raise ValueError("Can't compute a bounded region against an invalid Region.")
        first_row = max(unbound_region.first_row, self.first_row)
        first_col = max(unbound_region.first_col, self.first_col)
        last_row = min(unbound_region.first_row + unbound_region.rows, self.first_row + self.rows)
        last_col = min(unbound_region.first_col + unbound_region.cols, self.first_col + self.cols)
        result = Region(first_row, first_col, last_row - first_row, last_col - first_col)
        return result if result.is_valid() else Region(
            first_row=unbound_region.first_row,
            first_col=unbound_region.first_col
        )


@dataclass(frozen=True)
class SortCriteria:
    by_column: Union[None, List[int]] = None
    ascending: Union[None, List[bool]] = None

    def is_empty(self) -> bool:
        return not self.by_column

    def __eq__(self, other):
        if isinstance(other, SortCriteria):
            def _equals(s: Union[None, List[Any]], o: Union[None, List[Any]]) -> bool:
                # None and [] are interpreted as "no sorting"
                return (not s and not o) or s == o

            return _equals(self.by_column, other.by_column) and _equals(self.ascending, other.ascending)
        return False


@dataclass(frozen=True)
class CreateTableSourceConfig:
    temp_var_slot_id: Union[None, str] = None
    data_source_transform_hint: Union[None, str] = None
    previous_fingerprint: Union[None, str] = None
    filter_eval_expr: Union[None, str] = None
    filter_eval_expr_provide_frame: Union[None, bool] = None


class CreateTableSourceErrorKind(Enum):
    EVAL_EXCEPTION = 0,
    RE_EVAL_DATA_SOURCE_OF_WRONG_TYPE = 1,
    UNSUPPORTED_DATA_SOURCE_TYPE = 2,
    INVALID_FINGERPRINT = 3,
    FILTER_FRAME_EVAL_FAILED = 4,
    FILTER_FRAME_OF_WRONG_TYPE = 5


@dataclass(frozen=True)
class CreateTableSourceFailure:
    error_kind: CreateTableSourceErrorKind
    info: str


class TableSourceKind(Enum):
    TABLE_SOURCE = 1
    PATCHED_STYLER = 2


@dataclass(frozen=True)
class CompletionVariant:
    fq_type: str
    value: str


@dataclass(frozen=True)
class NestedCompletionVariant:
    fq_type: str
    children: List[CompletionVariant]
