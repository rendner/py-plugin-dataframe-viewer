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
from dataclasses import dataclass
from enum import Enum
from typing import Any, Dict, List, Optional, Tuple, Union


@dataclass(frozen=True)
class TableStructure:
    org_rows_count: int
    org_columns_count: int
    rows_count: int
    columns_count: int
    fingerprint: str


@dataclass
class TableFrameCell:
    value: str
    css: Dict[str, str] = None


@dataclass
class TableFrameLegend:
    index: List[str]
    column: List[str]


@dataclass
class TableFrame:
    index_labels: Union[None, List[List[str]]]
    column_labels: List[List[str]]
    cells: List[List[TableFrameCell]]
    legend: Union[None, TableFrameLegend] = None


@dataclass(frozen=True)
class TableFrameValidationResult:
    actual: str
    expected: str
    is_equal: bool


@dataclass(frozen=True)
class Region:
    first_row: int = 0
    first_col: int = 0
    rows: int = 0
    cols: int = 0

    @classmethod
    def from_shape(cls, shape: Tuple[int, int]):
        return cls(rows=shape[0], cols=shape[1])

    def is_empty(self) -> bool:
        return self.rows == 0 or self.cols == 0

    def is_valid(self) -> bool:
        return self.first_row >= 0 and self.first_col >= 0 and self.rows >= 0 and self.cols >= 0

    def iterate_chunkwise(self, rows_per_chunk: int, cols_per_chunk: int):
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

    def get_bounded_region(self, region_to_bound: 'Region') -> 'Region':
        if not self.is_valid():
            raise ValueError("No valid bounds.")
        if not region_to_bound.is_valid():
            raise ValueError("Can't compute a bounded region against an invalid Region.")
        first_row = max(region_to_bound.first_row, self.first_row)
        first_col = max(region_to_bound.first_col, self.first_col)
        last_row = min(region_to_bound.first_row + region_to_bound.rows, self.first_row + self.rows)
        last_col = min(region_to_bound.first_col + region_to_bound.cols, self.first_col + self.cols)
        result = Region(first_row, first_col, last_row - first_row, last_col - first_col)
        return result if result.is_valid() else Region(
            first_row=region_to_bound.first_row,
            first_col=region_to_bound.first_col
        )


@dataclass(frozen=True)
class SortCriteria:
    by_column: Optional[List[int]] = None
    ascending: Optional[List[bool]] = None

    def is_empty(self) -> bool:
        return not self.by_column

    def __eq__(self, other):
        if isinstance(other, SortCriteria):
            def _equals(s: Optional[List[Any]], o: Optional[List[Any]]) -> bool:
                # None and [] are interpreted as "no sorting"
                return (not s and not o) or s == o

            return _equals(self.by_column, other.by_column) and _equals(self.ascending, other.ascending)
        return False


@dataclass(frozen=True)
class CreateTableSourceConfig:
    data_source_transform_hint: Optional[str] = None
    previous_fingerprint: Optional[str] = None
    filter_eval_expr: Optional[str] = None
    filter_eval_expr_provide_frame: Optional[bool] = None


@dataclass(frozen=True)
class CreateTableSourceFailure:
    error_kind: str
    info: str


class TableSourceKind(Enum):
    TABLE_SOURCE = 1
    PATCHED_STYLER = 2

