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
from typing import Optional

from pandas import DataFrame

from cms_rendner_sdfv.base.table_source import AbstractTableSourceContext
from cms_rendner_sdfv.base.types import SortCriteria, TableStructure
from cms_rendner_sdfv.pandas.shared.types import FilterCriteria
from cms_rendner_sdfv.pandas.shared.visible_frame import VisibleFrame


class PandasTableSourceContext(AbstractTableSourceContext, ABC):
    def __init__(self, source_frame: DataFrame, filter_criteria: Optional[FilterCriteria] = None):
        self._source_frame = source_frame
        self._sort_criteria: SortCriteria = SortCriteria()
        self._filter_criteria: FilterCriteria = filter_criteria if filter_criteria is not None else FilterCriteria()
        self._visible_frame: VisibleFrame = self._recompute_visible_frame()

    @property
    def visible_frame(self) -> VisibleFrame:
        return self._visible_frame

    def get_table_structure(self, fingerprint: str) -> TableStructure:
        rows_count = self._visible_frame.region.rows
        columns_count = self._visible_frame.region.cols
        if rows_count == 0 or columns_count == 0:
            rows_count = columns_count = 0
        return TableStructure(
            org_rows_count=len(self._source_frame.index),
            org_columns_count=len(self._source_frame.columns),
            rows_count=rows_count,
            columns_count=columns_count,
            fingerprint=fingerprint,
        )

    def set_sort_criteria(self, sort_by_column_index: Optional[list[int]], sort_ascending: Optional[list[bool]]):
        new_sort_criteria = SortCriteria(sort_by_column_index, sort_ascending)
        if new_sort_criteria != self._sort_criteria:
            self._sort_criteria = new_sort_criteria
            self._visible_frame = self._recompute_visible_frame()

    def _get_initial_visible_frame_indexes(self):
        return self._source_frame.index, self._source_frame.columns

    def _recompute_visible_frame(self) -> VisibleFrame:
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

        return VisibleFrame(
            self._source_frame,
            self._source_frame.index.get_indexer_for(index),
            self._source_frame.columns.get_indexer_for(columns),
        )
