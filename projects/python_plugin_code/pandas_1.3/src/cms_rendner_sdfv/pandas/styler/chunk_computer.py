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
from copy import copy
from functools import partial
from typing import List, Any

from pandas import get_option
from pandas.io.formats.style import Styler
from pandas.core.dtypes.common import (
    is_complex,
    is_float,
    is_integer,
)

from cms_rendner_sdfv.base.types import Region, Cell
from cms_rendner_sdfv.pandas.shared.meta_computer import MetaComputer
from cms_rendner_sdfv.pandas.shared.value_formatter import ValueFormatter
from cms_rendner_sdfv.pandas.shared.visible_frame import VisibleFrame
from cms_rendner_sdfv.pandas.styler.todo_patcher import TodoPatcher


class Chunk:
    def __init__(self,
                 styler: Styler,
                 visible_frame: VisibleFrame,
                 region: Region,
                 meta_computer: MetaComputer,
                 formatter: ValueFormatter,
                 ):
        self.__styler = styler
        self.__visible_frame = visible_frame
        self.__region = region
        self.__meta_computer = meta_computer
        self.__formatter = formatter
        self.has_row_headers: bool = not self.__styler.hide_index_

    @property
    def region(self) -> Region:
        return self.__region

    def cell_value_at(self, row: int, col: int) -> Cell:
        raw_value = self.__visible_frame.cell_value_at(
            self.__region.first_row + row,
            self.__region.first_col + col,
        )
        css = self.__styler.ctx[(row, col)]
        css = None if not css or css is None else dict(css)

        org_row, org_col = self.__to_source_frame_cell_coordinates(row, col)
        meta = self.__meta_computer.compute_cell_meta(col=org_col, value=raw_value, css=css)
        display_value = self.__styler._display_funcs[(org_row, org_col)](raw_value)

        return Cell(value=self.__formatter.format_cell(display_value), meta=meta)

    def row_labels_at(self, row: int) -> List[Any]:
        labels = [] if self.__styler.hide_index_ else self.__visible_frame.row_labels_at(self.region.first_row + row)
        return [self.__formatter.format_index(lbl) for lbl in labels]

    def __to_source_frame_cell_coordinates(self, row: int, col: int):
        return self.__visible_frame.to_source_frame_cell_coordinates(
            self.__region.first_row + row,
            self.__region.first_col + col,
        )


def _fixed_default_formatter(x: Any, precision: int, thousands: bool = False) -> Any:
    if is_float(x) or is_complex(x):
        return f"{x:,.{precision}f}" if thousands else f"{x:.{precision}f}"
    elif is_integer(x):
        return f"{x:,.0f}" if thousands else f"{x:.0f}"
    return x


class ChunkComputer:
    def __init__(self,
                 visible_frame: VisibleFrame,
                 org_styler: Styler,
                 todo_patcher_list: List[TodoPatcher],
                 meta_computer: MetaComputer,
                 formatter: ValueFormatter,
                 ):
        self.__visible_frame: VisibleFrame = visible_frame
        self.__org_styler: Styler = org_styler
        self.__todo_patcher_list: List[TodoPatcher] = todo_patcher_list
        self.__meta_computer = meta_computer
        self.__formatter = formatter

        def_precision = get_option("display.precision")
        self.__fixed_default_formatter = lambda: partial(_fixed_default_formatter, precision=def_precision)

    def compute(self, region: Region) -> Chunk:
        # The plugin only renders the visible (non-hidden cols/rows) of the styled DataFrame.
        # Therefore, create chunk from the visible data.
        region = self.__visible_frame.region.get_bounded_region(region)
        chunk_df = self.__visible_frame.to_frame(region)

        # Create a styler from the chunk DataFrame.
        # The calculated css is stored in "chunk_styler.ctx" by using a tuple of (rowIndex, columnIndex) coordinates.
        # (see pandas Styler._update_ctx)
        chunk_styler = chunk_df.style

        # assign patched todos
        # The apply/map params are patched to not operate outside the chunk bounds.
        chunk_styler._todo = [
            p.create_patched_todo(chunk_df).to_tuple()
            for p in self.__todo_patcher_list
        ]
        # Compute the styling for the chunk.
        chunk_styler._compute()

        # copy over some required state props
        #
        # Fix for "_default_formatter" to detect float32, float64, int32 and float64 values
        # Fixed in pandas 1.4: https://github.com/pandas-dev/pandas/pull/46119
        #
        # Fix is required because "df.iat[row, col]" is used instead of "df.itertuples" in the VisibleFrame.
        # Styler.to_html() uses "df.itertuples" to convert cell values into html and does not run into this problem.
        # In case of a specific float/int "itertuples" returns a float/int instead of the correct
        # float32, float64, int32 and float64.
        chunk_styler._display_funcs = copy(self.__org_styler._display_funcs)
        chunk_styler._display_funcs.default_factory = self.__fixed_default_formatter

        chunk_styler.hide_index_ = self.__org_styler.hide_index_
        chunk_styler.hide_columns_ = self.__org_styler.hide_columns_

        return Chunk(
            styler=chunk_styler,
            visible_frame=self.__visible_frame,
            region=region,
            formatter=self.__formatter,
            meta_computer=self.__meta_computer,
        )
