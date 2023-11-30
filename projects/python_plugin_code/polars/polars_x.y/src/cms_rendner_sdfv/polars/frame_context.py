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
from typing import List, Optional

from polars import DataFrame

from cms_rendner_sdfv.base.table_source import AbstractTableFrameGenerator, AbstractTableSourceContext
from cms_rendner_sdfv.base.types import SortCriteria, TableStructure
from cms_rendner_sdfv.polars.visible_frame import VisibleFrame


class FrameContext(AbstractTableSourceContext):
    def __init__(self, source_frame: DataFrame):
        self._source_frame = source_frame
        self._sort_criteria: SortCriteria = SortCriteria()
        self._visible_frame: VisibleFrame = self._recompute_visible_frame()

    @property
    def visible_frame(self) -> VisibleFrame:
        return self._visible_frame

    def set_sort_criteria(self, sort_by_column_index: Optional[List[int]], sort_ascending: Optional[List[bool]]):
        new_sort_criteria = SortCriteria(sort_by_column_index, sort_ascending)
        if new_sort_criteria != self._sort_criteria:
            self._sort_criteria = new_sort_criteria
            self._visible_frame = self._recompute_visible_frame()

    def get_table_structure(self, fingerprint: str) -> TableStructure:
        rows_count, columns_count = self._visible_frame.region.frame_shape
        org_rows_count, org_cols_count = self._source_frame.shape
        if rows_count == 0 or columns_count == 0:
            rows_count = columns_count = 0
        return TableStructure(
            org_rows_count=org_rows_count,
            org_columns_count=org_cols_count,
            rows_count=rows_count,
            columns_count=columns_count,
            fingerprint=fingerprint,
        )

    def get_table_frame_generator(self) -> AbstractTableFrameGenerator:
        # local import to resolve cyclic import
        from cms_rendner_sdfv.polars.table_frame_generator import TableFrameGenerator
        return TableFrameGenerator(self._visible_frame)

    def _recompute_visible_frame(self) -> VisibleFrame:
        row_idx = None
        if not self._sort_criteria.is_empty():
            # get col names before we insert "with_row_count" col
            # otherwise we would have to translate all col idx by one
            col_names = self._source_frame.columns

            # ensures that we always have our own col which starts with a zero index
            # in case the user has configured something else
            row_idx_col_name: str = "cms_render_sdfv__row_nr"
            frame_with_index = self._source_frame.with_row_count(row_idx_col_name)

            by_names = [col_names[i] for i in self._sort_criteria.by_column]
            row_idx = frame_with_index \
                .sort(by_names, descending=[not asc for asc in self._sort_criteria.ascending]) \
                .get_column(row_idx_col_name)

        return VisibleFrame(self._source_frame, row_idx)