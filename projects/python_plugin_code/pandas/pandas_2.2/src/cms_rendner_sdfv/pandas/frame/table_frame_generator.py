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
from typing import Any, Callable, Optional

from pandas import get_option
from pandas.core.dtypes.common import (
    is_complex,
    is_float,
    is_integer,
)

from cms_rendner_sdfv.base.table_source import AbstractTableFrameGenerator
from cms_rendner_sdfv.base.types import Region, TableFrame, TableFrameCell, TableFrameColumn, TableFrameLegend
from cms_rendner_sdfv.pandas.shared.value_formatter import ValueFormatter
from cms_rendner_sdfv.pandas.shared.visible_frame import Chunk, VisibleFrame


class _ValueFormatter(ValueFormatter):
    def __init__(self):
        self._precision = get_option("display.precision")
        self._float_format: Optional[Callable] = get_option("display.float_format")

    def _default_format(self, x: Any, fallback_formatter) -> Any:
        if is_float(x) or is_complex(x):
            if callable(self._float_format):
                return self._float_format(x)
            return f"{x:.{self._precision}f}"
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
    def __init__(self, visible_frame: VisibleFrame):
        super().__init__(visible_frame)

    def generate(self,
                 region: Region = None,
                 exclude_row_header: bool = False,
                 exclude_col_header: bool = False,
                 ) -> TableFrame:

        chunk = self._visible_frame.get_chunk(region)
        formatter = _ValueFormatter()

        columns = [] if exclude_col_header else self._extract_columns(chunk, formatter)
        index_labels = [] if exclude_row_header else self._extract_index_header_labels(chunk, formatter)
        cells = self._extract_cells(chunk, formatter)
        legend_label = None if exclude_col_header and exclude_row_header else self._extract_legend_label(chunk, formatter)

        return TableFrame(
            index_labels=index_labels,
            columns=columns,
            legend=legend_label,
            cells=cells,
        )

    def _extract_columns(self, chunk: Chunk, formatter: ValueFormatter) -> list[TableFrameColumn]:
        result: list[TableFrameColumn] = []

        for col_offset in range(chunk.region.cols):
            name = chunk.column_at(col_offset)
            if isinstance(name, tuple):
                labels = [formatter.format_column(h) for h in name]
            else:
                labels = [formatter.format_column(name)]
            result.append(
                TableFrameColumn(
                    dtype=str(chunk.dtype_at(col_offset)),
                    labels=labels,
                    describe=None if self._exclude_column_describe else chunk.describe_at(col_offset),
                )
            )

        return result

    @staticmethod
    def _extract_index_header_labels(chunk: Chunk, formatter: ValueFormatter) -> list[list[str]]:
        result: list[list[str]] = []

        for row_offset in range(chunk.region.rows):
            name = chunk.index_at(row_offset)
            if isinstance(name, tuple):
                result.append([formatter.format_index(h) for h in name])
            else:
                result.append([formatter.format_index(name)])

        return result

    @staticmethod
    def _extract_cells(chunk: Chunk, formatter: ValueFormatter) -> list[list[TableFrameCell]]:
        result: list[list[TableFrameCell]] = []

        col_range = range(chunk.region.cols)
        for row_offset in range(chunk.region.rows):
            result.append(
                [TableFrameCell(
                    value=formatter.format_cell(chunk.cell_value_at(row_offset, col_offset)),
                ) for col_offset in col_range]
            )

        return result

    @staticmethod
    def _extract_legend_label(chunk: Chunk, formatter: ValueFormatter) -> TableFrameLegend:
        index_legend = [formatter.format_index(n) for n in chunk.index_names() if n is not None]
        column_legend = [formatter.format_index(n) for n in chunk.column_names() if n is not None]
        return TableFrameLegend(index=index_legend, column=column_legend) if index_legend or column_legend else None
