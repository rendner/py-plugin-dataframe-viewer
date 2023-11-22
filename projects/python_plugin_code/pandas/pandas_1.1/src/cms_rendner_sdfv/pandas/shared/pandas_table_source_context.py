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
from abc import ABC
from typing import List, Optional

import numpy as np
from pandas import DataFrame, Index

from cms_rendner_sdfv.base.table_source import AbstractTableSourceContext
from cms_rendner_sdfv.base.types import Region, SortCriteria, TableStructure
from cms_rendner_sdfv.pandas.shared.types import FilterCriteria


class PandasTableSourceContext(AbstractTableSourceContext, ABC):
    def __init__(self, source_frame: DataFrame, filter_criteria: Optional[FilterCriteria] = None):
        self._source_frame = source_frame
        self._sort_criteria: SortCriteria = SortCriteria()
        self._filter_criteria: FilterCriteria = filter_criteria if filter_criteria is not None else FilterCriteria()

        self._recompute_visible_frame()

    def get_table_structure(self, fingerprint: str) -> TableStructure:
        rows_count = len(self._visible_index)
        columns_count = len(self._visible_columns)
        if rows_count == 0 or columns_count == 0:
            rows_count = columns_count = 0
        return TableStructure(
            org_rows_count=len(self._source_frame.index),
            org_columns_count=len(self._source_frame.columns),
            rows_count=rows_count,
            columns_count=columns_count,
            fingerprint=fingerprint,
        )

    def set_sort_criteria(self, sort_by_column_index: Optional[List[int]], sort_ascending: Optional[List[bool]]):
        self._sort_criteria = SortCriteria(sort_by_column_index, sort_ascending)
        self._recompute_visible_frame()

    def get_org_indices_of_visible_columns(self, part_start: int, max_columns: int) -> List[int]:
        part = self._visible_columns[part_start:part_start + max_columns]
        scalar_or_list = self._source_frame.columns.get_indexer_for(part).tolist()
        return scalar_or_list if isinstance(scalar_or_list, list) else [scalar_or_list]

    def get_region_of_frame(self) -> Region:
        return self._visible_region

    def get_chunk(self, region: Region) -> DataFrame:
        frame = self._source_frame
        ind = frame.index.get_indexer_for(self._visible_index[region.first_row:region.first_row + region.rows])
        cols = frame.columns.get_indexer_for(self._visible_columns[region.first_col:region.first_col + region.cols])
        return frame.iloc[ind, cols]

    def get_frame_index(self) -> Index:
        return self._visible_index

    def get_frame_columns(self) -> Index:
        return self._visible_columns

    def translate_chunk_index_to_initial_frame_index_positions(self, chunk: DataFrame) -> np.ndarray:
        return self._source_frame.index.get_indexer_for(chunk.index)

    def translate_chunk_columns_to_initial_frame_column_positions(self, chunk: DataFrame) -> np.ndarray:
        return self._source_frame.columns.get_indexer_for(chunk.columns)

    def _get_initial_visible_frame_indexes(self):
        return self._source_frame.index, self._source_frame.columns

    def _recompute_visible_frame(self):
        index, columns = self._get_initial_visible_frame_indexes()

        if self._filter_criteria.index is not None:
            index = index.intersection(self._filter_criteria.index)

        if self._filter_criteria.columns is not None:
            columns = columns.intersection(self._filter_criteria.columns)

        if not self._sort_criteria.is_empty():
            sc = self._sort_criteria
            frame = self._source_frame.loc[index, columns]
            frame = frame.sort_values(
                by=[frame.columns[i] for i in sc.by_column],
                ascending=True if sc.ascending is None or len(sc.ascending) == 0 else sc.ascending,
            )
            index = frame.index

        self._visible_index = index
        self._visible_columns = columns
        self._visible_region = Region(0, 0, len(index), len(columns))
