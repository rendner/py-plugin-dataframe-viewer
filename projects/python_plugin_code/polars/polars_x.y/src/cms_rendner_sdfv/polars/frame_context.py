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
from typing import List, Optional, Union, Any

from polars import DataFrame

from cms_rendner_sdfv.base.table_source import AbstractTableFrameGenerator, AbstractTableSourceContext, \
    AbstractColumnNameCompleter
from cms_rendner_sdfv.base.types import SortCriteria, TableStructure, TableStructureColumnInfo, TableStructureColumn
from cms_rendner_sdfv.polars.visible_frame import VisibleFrame


class PolarsColumnNameCompleter(AbstractColumnNameCompleter, ABC):
    def __init__(self, source_frame: DataFrame):
        self.__source_frame = source_frame

    def _resolve_column_names(self, source: Any, is_synthetic_df: bool) -> List[Any]:
        if source is None and is_synthetic_df:
            # To use columns that have already been filtered out, the original unfiltered data source is used.
            source = self.__source_frame

        return source.columns if isinstance(source, DataFrame) else []


class FrameContext(AbstractTableSourceContext):
    def __init__(self, source_frame: DataFrame, filtered_frame: Union[DataFrame, None] = None):
        self.__source_frame = source_frame
        self.__filtered_frame = filtered_frame
        self.__sort_criteria: SortCriteria = SortCriteria()
        self.__visible_frame: VisibleFrame = self._recompute_visible_frame()

    def unlink(self):
        self.__source_frame = None
        self.__filtered_frame = None
        self.__sort_criteria = None
        self.__visible_frame = None

    @property
    def visible_frame(self) -> VisibleFrame:
        return self.__visible_frame

    def get_column_name_completer(self) -> Union[None, AbstractColumnNameCompleter]:
        return PolarsColumnNameCompleter(self.__source_frame)

    def set_sort_criteria(self, sort_by_column_index: Optional[List[int]], sort_ascending: Optional[List[bool]]):
        new_sort_criteria = SortCriteria(sort_by_column_index, sort_ascending)
        if new_sort_criteria != self.__sort_criteria:
            self.__sort_criteria = new_sort_criteria
            self.__visible_frame = self._recompute_visible_frame()

    def get_table_structure(self, fingerprint: str) -> TableStructure:
        rows_count, columns_count = self.__visible_frame.region.frame_shape
        org_rows_count, org_cols_count = self.__source_frame.shape
        if rows_count == 0 or columns_count == 0:
            rows_count = columns_count = 0
        return TableStructure(
            org_rows_count=org_rows_count,
            org_columns_count=org_cols_count,
            rows_count=rows_count,
            columns_count=columns_count,
            fingerprint=fingerprint,
            column_info=self._get_frame_column_info() if columns_count != 0
            else TableStructureColumnInfo(columns=[], legend=None),
        )

    def _get_frame_column_info(self) -> TableStructureColumnInfo:
        ts_columns: List[TableStructureColumn] = []

        col_names = self.__source_frame.columns
        col_dtypes = self.__source_frame.dtypes
        for col in self.visible_frame.get_column_indices():
            ts_columns.append(
                TableStructureColumn(
                    dtype=str(col_dtypes[col]),
                    labels=[col_names[col]],
                    id=col,
                )
            )

        return TableStructureColumnInfo(columns=ts_columns, legend=None)

    def get_table_frame_generator(self) -> AbstractTableFrameGenerator:
        # local import to resolve cyclic import
        from cms_rendner_sdfv.polars.table_frame_generator import TableFrameGenerator
        return TableFrameGenerator(self.__visible_frame)

    def _recompute_visible_frame(self) -> VisibleFrame:
        col_idx = None

        if self.__filtered_frame is None:
            data_frame = self.__source_frame
        else:
            col_idx = []
            org_col_names = self.__source_frame.columns
            for c_name in self.__filtered_frame.columns:
                try:
                    col_idx.append(org_col_names.index(c_name))
                except ValueError:
                    pass

            if not col_idx:
                # filter frame and org frame have no common columns
                return VisibleFrame(DataFrame(), None, None)

            data_frame = self.__filtered_frame

        row_idx = None
        if not self.__sort_criteria.is_empty():
            # get col names before we insert "with_row_count" col
            # otherwise we would have to translate all col idx by one
            col_names = data_frame.columns

            # ensures that we always have our own col which starts with a zero index
            # in case the user has configured something else
            row_idx_col_name: str = "cms_render_sdfv__row_nr"

            if hasattr(data_frame, 'with_row_index'):
                frame_with_index = data_frame.with_row_index(row_idx_col_name)
            else:
                frame_with_index = data_frame.with_row_count(row_idx_col_name)

            by_names = [col_names[i] for i in self.__sort_criteria.by_column]
            row_idx = frame_with_index \
                .sort(by_names, descending=[not asc for asc in self.__sort_criteria.ascending]) \
                .get_column(row_idx_col_name)

        return VisibleFrame(data_frame, row_idx, col_idx)
