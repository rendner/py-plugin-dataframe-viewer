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
import os
from typing import List

import polars as pl

from cms_rendner_sdfv.base.table_source import AbstractTableFrameGenerator
from cms_rendner_sdfv.base.types import Region, TableFrame, TableFrameCell, TableFrameColumn
from cms_rendner_sdfv.polars.visible_frame import VisibleFrame


class TableFrameGenerator(AbstractTableFrameGenerator):
    def __init__(self, visible_frame: VisibleFrame):
        super().__init__(visible_frame)

    def generate(self,
                 region: Region = None,
                 exclude_row_header: bool = False,
                 exclude_col_header: bool = False,
                 ) -> TableFrame:
        frame = self._visible_frame

        if region is None:
            region = frame.region
        else:
            region = frame.region.get_bounded_region(region)

        column_labels = [] if exclude_col_header else self._extract_column_header_labels(frame, region)
        cell_values = self._extract_cell_values(frame, region)

        return TableFrame(
            index_labels=None,
            column_labels=column_labels,
            legend=None,
            cells=cell_values,
        )

    @staticmethod
    def _extract_column_header_labels(frame: VisibleFrame, region: Region) -> List[TableFrameColumn]:
        col_names = frame.source_frame.columns
        dtypes = frame.source_frame.dtypes
        return [TableFrameColumn(dtype=str(dtypes[i]), labels=[col_names[i]]) for i in frame.get_col_idx_in_source(region)]

    @staticmethod
    def _extract_cell_values(frame: VisibleFrame, region: Region) -> List[List[TableFrameCell]]:
        result: List[List[TableFrameCell]] = []

        if frame.region.is_empty():
            return result

        source_frame = frame.source_frame
        dtypes = source_frame.dtypes
        src_col_idx = frame.get_col_idx_in_source(region)
        src_row_idx = frame.get_row_idx_in_source(region)

        str_lengths = int(os.environ.get("POLARS_FMT_STR_LEN", "42"))

        for sci in src_col_idx:
            series = source_frame.get_column(source_frame.columns[sci])
            is_string = isinstance(dtypes[sci], pl.Utf8)
            should_create_row = not result
            for ri, sri in enumerate(src_row_idx):
                if is_string:
                    # 'series._s.get_fmt(...)' wraps strings with a leading '"' and a trailing '"'.
                    # If the wrapped string exceeds the configured string length, it gets truncated
                    # and the last char is replaced with a '…'.
                    #
                    # examples:
                    # '12345' with a configured max length of 3 becomes '"12…'
                    # '12345' with a configured max length of 6 becomes '"12345…'
                    # '12345' with a configured max length of 7 becomes '"12345"'
                    #
                    # A printed DataFrame doesn't wrap strings with additional '"'.
                    # To get identical values for strings, the length is increased by two and altered afterwards.
                    v = series._s.get_fmt(sri, str_lengths + 2)
                    if v[-1] == '"':
                        v = v[1:-1]
                    else:
                        v = v[1:-2] + v[-1]
                else:
                    v = series._s.get_fmt(sri, str_lengths)

                if should_create_row:
                    result.append([TableFrameCell(v)])
                else:
                    result[ri].append(TableFrameCell(v))

        return result
