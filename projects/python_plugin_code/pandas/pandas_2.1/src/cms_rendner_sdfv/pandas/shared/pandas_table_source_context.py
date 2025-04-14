#  Copyright 2021-2025 cms.rendner (Daniel Schmidt)
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
from typing import Optional, Any, List, Union

from pandas import DataFrame

from cms_rendner_sdfv.base.helpers import fq_type
from cms_rendner_sdfv.base.table_source import AbstractTableSourceContext
from cms_rendner_sdfv.base.types import SortCriteria, TableStructure, TableStructureColumnInfo, CompletionVariant, \
    NestedCompletionVariant
from cms_rendner_sdfv.pandas.shared.meta_computer import MetaComputer
from cms_rendner_sdfv.pandas.shared.types import FilterCriteria
from cms_rendner_sdfv.pandas.shared.value_formatter import ValueFormatter
from cms_rendner_sdfv.pandas.shared.visible_frame import VisibleFrame, MappedVisibleFrame


class PandasTableSourceContext(AbstractTableSourceContext, ABC):
    def __init__(self,
                 source_frame: DataFrame,
                 filter_criteria: Optional[FilterCriteria] = None,
                 formatter: Optional[ValueFormatter] = None,
                 ):
        self.__source_frame = source_frame
        self.__sort_criteria: SortCriteria = SortCriteria()
        self.__filter_criteria: FilterCriteria = filter_criteria if filter_criteria is not None else FilterCriteria()
        self._visible_frame: VisibleFrame = self.__recompute_visible_frame()
        self._formatter = formatter if formatter is not None else ValueFormatter()
        self._meta_computer = MetaComputer(source_frame)

    def unlink(self):
        self.__source_frame = None
        self.__sort_criteria = None
        self.__filter_criteria = None
        self._visible_frame.unlink()
        self._visible_frame = None
        self._meta_computer.unlink()
        self._meta_computer = None

    @property
    def visible_frame(self) -> VisibleFrame:
        return self._visible_frame

    def get_table_structure(self, fingerprint: str) -> TableStructure:
        rows_count = self._visible_frame.region.rows
        columns_count = self._visible_frame.region.cols
        if rows_count == 0 or columns_count == 0:
            rows_count = columns_count = 0
        return TableStructure(
            org_rows_count=len(self.__source_frame.index),
            org_columns_count=len(self.__source_frame.columns),
            rows_count=rows_count,
            columns_count=columns_count,
            fingerprint=fingerprint,
            column_info=self._get_frame_column_info() if columns_count != 0
            else TableStructureColumnInfo(columns=[], legend=None),
        )

    def get_column_statistics(self, col_index: int):
        return self._visible_frame.get_column_statistics(col_index, self._formatter)

    @abstractmethod
    def _get_frame_column_info(self) -> TableStructureColumnInfo:
        pass

    def get_column_name_completion_variants(self, source: Any, is_synthetic_df: bool) -> List[Union[CompletionVariant, NestedCompletionVariant]]:
        result = []

        if (source is None and is_synthetic_df) or source is self.__source_frame:
            # To use columns that have already been filtered out, the original unfiltered data source is used.
            source = self.__source_frame

        if not isinstance(source, DataFrame):
            return result

        for col in source.columns:
            if isinstance(col, tuple):
                result.append(
                    NestedCompletionVariant(
                        fq_type=fq_type(col),
                        children=[CompletionVariant(fq_type=fq_type(lvl), value=str(lvl)) for lvl in col],
                    )
                )
            else:
                result.append(CompletionVariant(fq_type=fq_type(col), value=str(col)))

        return result

    def set_sort_criteria(self, sort_by_column_index: Optional[list[int]], sort_ascending: Optional[list[bool]]):
        new_sort_criteria = SortCriteria(sort_by_column_index, sort_ascending)
        if new_sort_criteria != self.__sort_criteria:
            self.__sort_criteria = new_sort_criteria
            self._visible_frame = self.__recompute_visible_frame()

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
            self.__source_frame.index.get_indexer_for(index).tolist(),
            self.__source_frame.columns.get_indexer_for(columns).tolist(),
        )
