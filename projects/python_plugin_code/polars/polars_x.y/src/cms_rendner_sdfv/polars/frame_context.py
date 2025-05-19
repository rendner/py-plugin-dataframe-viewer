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
import os
from typing import List, Optional, Union, Any, Dict

from polars import DataFrame, DataType, datatypes

from cms_rendner_sdfv.base.constants import CELL_MAX_STR_LEN, CELL_MAX_LIST_LEN
from cms_rendner_sdfv.base.helpers import fq_type
from cms_rendner_sdfv.base.table_source import AbstractTableSourceContext
from cms_rendner_sdfv.base.types import SortCriteria, TableStructure, TableStructureColumnInfo, TableStructureColumn, \
    CompletionVariant, NestedCompletionVariant, TextAlign
from cms_rendner_sdfv.polars.chunk_data_generator import ChunkDataGenerator, FormatOptions
from cms_rendner_sdfv.polars.meta_computer import MetaComputer
from cms_rendner_sdfv.polars.visible_frame import VisibleFrame


def _compute_format_options() -> FormatOptions:
    def get_min_value(key: str, fallback: int) -> int:
        try:
            return min(fallback, int(os.environ.get(key, str(fallback))))
        except:
            return fallback

    return FormatOptions(
        str_len=get_min_value("POLARS_FMT_STR_LEN", CELL_MAX_STR_LEN),
        cell_list_len=get_min_value("POLARS_FMT_TABLE_CELL_LIST_LEN", CELL_MAX_LIST_LEN)
    )


class FrameContext(AbstractTableSourceContext):
    def __init__(self, source_frame: DataFrame, filtered_frame: Union[DataFrame, None] = None):
        self.__source_frame = source_frame
        self.__filtered_frame = filtered_frame
        self.__sort_criteria: SortCriteria = SortCriteria()
        self.__visible_frame: VisibleFrame = self._recompute_visible_frame()
        self.__format_options = _compute_format_options()
        self.__meta_computer = MetaComputer(source_frame)

    def unlink(self):
        self.__source_frame = None
        self.__filtered_frame = None
        self.__sort_criteria = None
        self.__visible_frame = None
        self.__meta_computer.unlink()
        self.__meta_computer = None

    @property
    def visible_frame(self) -> VisibleFrame:
        return self.__visible_frame

    def get_column_statistics(self, col_index: int) -> Dict[str, str]:
        return self.__visible_frame.get_column_statistics(col_index)

    def get_column_name_completion_variants(self, source: Any, is_synthetic_df: bool) -> List[
        Union[CompletionVariant, NestedCompletionVariant]]:
        result = []

        if (source is None and is_synthetic_df) or source is self.__source_frame:
            # To use columns that have already been filtered out, the original unfiltered data source is used.
            source = self.__source_frame

        if not isinstance(source, DataFrame):
            return result

        str_fqt = fq_type("")
        for col in source.columns:
            result.append(CompletionVariant(fq_type=str_fqt, value=col))

        return result

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
            col_dtype = col_dtypes[col]
            ts_columns.append(
                TableStructureColumn(
                    dtype=str(col_dtype),
                    labels=[col_names[col]],
                    id=col,
                    text_align=self._get_column_text_align(col_dtype),
                )
            )

        return TableStructureColumnInfo(columns=ts_columns, legend=None)

    @staticmethod
    def _get_column_text_align(col_dtype: DataType) -> Union[None, TextAlign]:
        if col_dtype.is_numeric() and col_dtype is not datatypes.Boolean:
            return TextAlign.RIGHT
        return None

    def get_chunk_data_generator(self):
        return ChunkDataGenerator(self.__visible_frame, self.__format_options, self.__meta_computer)

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

        sorted_row_idx = None
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
            sorted_row_idx = frame_with_index \
                .sort(by_names, descending=[not asc for asc in self.__sort_criteria.ascending]) \
                .get_column(row_idx_col_name)

        return VisibleFrame(unsorted_source_frame=data_frame, sorted_row_idx=sorted_row_idx, org_col_idx=col_idx)
