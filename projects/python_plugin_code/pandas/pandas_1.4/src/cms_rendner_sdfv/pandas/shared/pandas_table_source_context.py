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
from abc import ABC
from typing import List, Optional, Any

from pandas import DataFrame

from cms_rendner_sdfv.base.table_source import AbstractTableSourceContext, AbstractColumnNameCompleter
from cms_rendner_sdfv.base.types import SortCriteria, TableStructure
from cms_rendner_sdfv.pandas.shared.types import FilterCriteria
from cms_rendner_sdfv.pandas.shared.visible_frame import VisibleFrame, MappedVisibleFrame


class PandasColumnNameCompleter(AbstractColumnNameCompleter, ABC):
    def __init__(self, source_frame: DataFrame):
        self.__source_frame = source_frame

    def _resolve_column_names(self, source: Any, is_synthetic_df: bool) -> List[str]:
        if source is None and is_synthetic_df:
            # To use columns that have already been filtered out, the original unfiltered data source is used.
            source = self.__source_frame

        return list(source) if isinstance(source, DataFrame) else []


class PandasTableSourceContext(AbstractTableSourceContext, ABC):
    def __init__(self, source_frame: DataFrame, filter_criteria: Optional[FilterCriteria] = None):
        self.__source_frame = source_frame
        self.__sort_criteria: SortCriteria = SortCriteria()
        self.__filter_criteria: FilterCriteria = filter_criteria if filter_criteria is not None else FilterCriteria()
        self.__visible_frame: VisibleFrame = self.__recompute_visible_frame()

    @property
    def visible_frame(self) -> VisibleFrame:
        return self.__visible_frame

    def get_table_structure(self, fingerprint: str) -> TableStructure:
        rows_count = self.__visible_frame.region.rows
        columns_count = self.__visible_frame.region.cols
        if rows_count == 0 or columns_count == 0:
            rows_count = columns_count = 0
        return TableStructure(
            org_rows_count=len(self.__source_frame.index),
            org_columns_count=len(self.__source_frame.columns),
            rows_count=rows_count,
            columns_count=columns_count,
            fingerprint=fingerprint,
        )

    def get_column_name_completer(self) -> Optional[AbstractColumnNameCompleter]:
        return PandasColumnNameCompleter(self.__source_frame)

    def set_sort_criteria(self, sort_by_column_index: Optional[List[int]], sort_ascending: Optional[List[bool]]):
        new_sort_criteria = SortCriteria(sort_by_column_index, sort_ascending)
        if new_sort_criteria != self.__sort_criteria:
            self.__sort_criteria = new_sort_criteria
            self.__visible_frame = self.__recompute_visible_frame()

    def _get_initial_visible_frame_indexes(self):
        return self.__source_frame.index, self.__source_frame.columns

    def __recompute_visible_frame(self) -> VisibleFrame:
        index, columns = self._get_initial_visible_frame_indexes()

        if self.__filter_criteria.index is not None:
            index = index.intersection(self.__filter_criteria.index)

        if self.__filter_criteria.columns is not None:
            columns = columns.intersection(self.__filter_criteria.columns)

        if not self.__sort_criteria.is_empty():
            sc = self.__sort_criteria
            frame = self.__source_frame.loc[index, columns]
            frame = frame.sort_values(
                by=[frame.columns[i] for i in sc.by_column],
                ascending=True if sc.ascending is None or len(sc.ascending) == 0 else sc.ascending,
            )
            index = frame.index

        if index is self.__source_frame.index and columns is self.__source_frame.columns:
            return VisibleFrame(self.__source_frame)

        return MappedVisibleFrame(
            self.__source_frame,
            self.__source_frame.index.get_indexer_for(index),
            self.__source_frame.columns.get_indexer_for(columns),
        )
