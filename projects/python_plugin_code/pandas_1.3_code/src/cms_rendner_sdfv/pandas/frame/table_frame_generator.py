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
from typing import Any, Callable, List, Optional

from cms_rendner_sdfv.base.table_source import AbstractTableFrameGenerator
from cms_rendner_sdfv.base.types import Region, TableFrame, TableFrameCell
from pandas import get_option
from pandas.core.dtypes.common import (
    is_complex,
    is_float,
    is_integer,
)

from cms_rendner_sdfv.pandas.shared.pandas_table_source_context import PandasTableSourceContext
from cms_rendner_sdfv.pandas.shared.value_formatter import ValueFormatter


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
    def __init__(self, source_context: PandasTableSourceContext):
        self.__source_context = source_context

    def _region_or_region_of_frame(self, region: Region = None) -> Region:
        return region if region is not None else self.__source_context.get_region_of_frame()

    def generate(self,
                 region: Region = None,
                 exclude_row_header: bool = False,
                 exclude_col_header: bool = False,
                 ) -> TableFrame:
        region = self._region_or_region_of_frame(region)
        chunk = self.__source_context.get_chunk(region)

        dict_props = chunk.to_dict(orient="split")
        formatter = _ValueFormatter()

        column_labels = [] if exclude_col_header else self._extract_column_header_labels(dict_props, formatter)
        index_labels = [] if exclude_row_header else self._extract_index_header_labels(dict_props, formatter)
        cell_values = self._extract_cell_values(dict_props, formatter)

        return TableFrame(
            index_labels=index_labels,
            column_labels=column_labels,
            legend=None,
            cells=cell_values,
        )

    @staticmethod
    def _extract_column_header_labels(dict_props: dict, formatter: ValueFormatter) -> List[List[str]]:
        result: List[List[str]] = []

        for col_name in dict_props.get("columns", []):
            if isinstance(col_name, tuple):
                result.append([formatter.format_column(h) for h in col_name])
            else:
                result.append([formatter.format_column(col_name)])

        return result

    @staticmethod
    def _extract_index_header_labels(dict_props: dict, formatter: ValueFormatter) -> List[List[str]]:
        result: List[List[str]] = []

        for index_name in dict_props.get("index", []):
            if isinstance(index_name, tuple):
                result.append([formatter.format_index(h) for h in index_name])
            else:
                result.append([formatter.format_index(index_name)])

        return result

    @staticmethod
    def _extract_cell_values(dict_props: dict, formatter: ValueFormatter) -> List[List[TableFrameCell]]:
        result: List[List[TableFrameCell]] = []

        for row in dict_props.get("data", []):
            result.append([TableFrameCell(value=formatter.format_cell(c)) for c in row])

        return result
