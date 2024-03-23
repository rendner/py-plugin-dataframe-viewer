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
from typing import Any, Callable, List, Optional

from pandas import get_option
from pandas.core.dtypes.common import (
    is_complex,
    is_float,
    is_integer,
)

from cms_rendner_sdfv.base.table_source import AbstractTableFrameGenerator
from cms_rendner_sdfv.base.types import Region, TableFrame, TableFrameCell, TableFrameColumn, TableFrameLegend
from cms_rendner_sdfv.pandas.frame.frame_context import Chunk, FrameContext
from cms_rendner_sdfv.pandas.shared.value_formatter import ValueFormatter


class _ValueFormatter(ValueFormatter):
    def __init__(self):
        self.__precision = get_option("display.precision")
        self.__float_format: Optional[Callable] = get_option("display.float_format")

    def _default_format(self, x: Any, fallback_formatter) -> Any:
        if is_float(x) or is_complex(x):
            if callable(self.__float_format):
                return self.__float_format(x)
            return f"{x:.{self.__precision}f}"
        elif is_integer(x):
            return str(x)

        return fallback_formatter(x)

    def format_column(self, value: Any) -> str:
        return self._default_format(value, super().format_column)

    def format_index(self, value: Any) -> str:
        return self._default_format(value, super().format_index)

    def format_cell(self, value: Any) -> str:
        return self._default_format(value, super().format_cell)


class TableFrameGenerator(AbstractTableFrameGenerator):
    def __init__(self, context: FrameContext):
        super().__init__(context.visible_frame)
        self.__context: FrameContext = context

    def generate(self,
                 region: Region = None,
                 exclude_row_header: bool = False,
                 exclude_col_header: bool = False,
                 ) -> TableFrame:
        chunk = self.__context.get_chunk(region)
        formatter = _ValueFormatter()

        return TableFrame(
            index_labels=[] if exclude_row_header else self._extract_index_header_labels(chunk, formatter),
            columns=[] if exclude_col_header else self._extract_columns(chunk, formatter),
            legend=None if exclude_col_header and exclude_row_header else self._extract_legend_label(formatter),
            cells=self._extract_cells(chunk, formatter),
        )

    def _extract_columns(self, chunk: Chunk, formatter: ValueFormatter) -> List[TableFrameColumn]:
        result: List[TableFrameColumn] = []

        for c in range(chunk.region.cols):
            labels = [formatter.format_column(lbl) for lbl in chunk.col_labels_at(c)]
            info = self._visible_frame.get_column_info(chunk.region.first_col + c)
            result.append(TableFrameColumn(dtype=str(info.dtype), labels=labels, describe=info.describe()))

        return result

    @staticmethod
    def _extract_index_header_labels(chunk: Chunk, formatter: ValueFormatter) -> List[List[str]]:
        result: List[List[str]] = []

        for r in range(chunk.region.rows):
            result.append([formatter.format_index(lbl) for lbl in chunk.row_labels_at(r)])

        return result

    @staticmethod
    def _extract_cells(chunk: Chunk, formatter: ValueFormatter) -> List[List[TableFrameCell]]:
        result: List[List[TableFrameCell]] = []

        col_range = range(chunk.region.cols)
        for r in range(chunk.region.rows):
            result.append([TableFrameCell(value=formatter.format_cell(chunk.cell_value_at(r, c))) for c in col_range])

        return result

    def _extract_legend_label(self, formatter: ValueFormatter) -> TableFrameLegend:
        index_legend = [formatter.format_index(n) for n in self._visible_frame.index_names if n is not None]
        column_legend = [formatter.format_index(n) for n in self._visible_frame.column_names if n is not None]
        return TableFrameLegend(index=index_legend, column=column_legend) if index_legend or column_legend else None
