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
from typing import Optional

from pandas import DataFrame

from cms_rendner_sdfv.base.types import TableStructureColumnInfo, TableStructureColumn, \
    TableStructureLegend
from cms_rendner_sdfv.pandas.frame.chunk_data_generator import ChunkDataGenerator
from cms_rendner_sdfv.pandas.frame.frame_value_formatter import FrameValueFormatter
from cms_rendner_sdfv.pandas.shared.pandas_table_source_context import PandasTableSourceContext
from cms_rendner_sdfv.pandas.shared.types import FilterCriteria


class FrameContext(PandasTableSourceContext):
    def __init__(self, source_frame: DataFrame, filter_criteria: Optional[FilterCriteria] = None):
        super().__init__(source_frame, filter_criteria, formatter=FrameValueFormatter())
        self.__source_frame = source_frame

    def unlink(self):
        self.__source_frame = None
        super().unlink()

    def get_chunk_data_generator(self):
        return ChunkDataGenerator(self._visible_frame, self._formatter, self._meta_computer)

    def _get_frame_column_info(self) -> TableStructureColumnInfo:
        ts_columns = []
        dtypes = self.__source_frame.dtypes
        nlevels = self.__source_frame.columns.nlevels
        for col in self._visible_frame.get_column_indices():
            col_label = self.__source_frame.columns[col]
            labels = [col_label] if nlevels == 1 else col_label
            labels = [self._formatter.format_column(lbl) for lbl in labels]
            ts_columns.append(TableStructureColumn(dtype=str(dtypes[col_label]), labels=labels, id=col))

        index_legend = [self._formatter.format_index(lbl) for lbl in self._visible_frame.index_names if lbl is not None]
        column_legend = [self._formatter.format_index(lbl) for lbl in self._visible_frame.column_names if lbl is not None]

        return TableStructureColumnInfo(
            columns=ts_columns,
            legend=TableStructureLegend(
                index=index_legend,
                column=column_legend,
            ) if index_legend or column_legend else None
        )
