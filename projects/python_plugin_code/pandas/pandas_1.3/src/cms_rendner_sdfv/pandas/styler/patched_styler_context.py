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
from copy import copy
from functools import partial
from typing import List, Optional, Any

from pandas import Index, get_option
from pandas.core.dtypes.common import (
    is_complex,
    is_float,
    is_integer,
)
from pandas.io.formats.style import Styler

from cms_rendner_sdfv.base.table_source import AbstractTableFrameGenerator
from cms_rendner_sdfv.base.types import Region
from cms_rendner_sdfv.pandas.shared.pandas_table_source_context import PandasTableSourceContext
from cms_rendner_sdfv.pandas.shared.types import FilterCriteria
from cms_rendner_sdfv.pandas.shared.visible_frame import VisibleFrame
from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo
from cms_rendner_sdfv.pandas.styler.todos_patcher import TodosPatcher


class StyledChunk:
    def __init__(self, styler: Styler, visible_frame: VisibleFrame, region: Region):
        self.__styler = styler
        self.__visible_frame = visible_frame
        self.region = region

    @property
    def column_labels_hidden(self):
        return self.__styler.hide_columns_

    @property
    def row_labels_hidden(self):
        return self.__styler.hide_index_

    def cell_css_at(self, row: int, col: int):
        return self.__styler.ctx[self.__to_source_frame_cell_coordinates(row, col)]

    def row_labels_at(self, row: int):
        labels = self.__visible_frame.index_at(self.region.first_row + row)
        if not isinstance(labels, tuple):
            labels = [labels]
        return labels

    def col_labels_at(self, col: int):
        labels = self.__visible_frame.column_at(self.region.first_col + col)
        if not isinstance(labels, tuple):
            labels = [labels]
        return labels

    def cell_value_at(self, row: int, col: int):
        v = self.__visible_frame.cell_value_at(
            self.region.first_row + row,
            self.region.first_col + col,
        )
        return self.__styler._display_funcs[self.__to_source_frame_cell_coordinates(row, col)](v)

    def __to_source_frame_cell_coordinates(self, row: int, col: int):
        return self.__visible_frame.to_source_frame_cell_coordinates(
            self.region.first_row + row,
            self.region.first_col + col,
        )


def _fixed_default_formatter(x: Any, precision: int, thousands: bool = False) -> Any:
    if is_float(x) or is_complex(x):
        return f"{x:,.{precision}f}" if thousands else f"{x:.{precision}f}"
    elif is_integer(x):
        return f"{x:,.0f}" if thousands else f"{x:.0f}"
    return x


class PatchedStylerContext(PandasTableSourceContext):
    def __init__(self, styler: Styler, filter_criteria: Optional[FilterCriteria] = None):
        # hidden_rows and hidden_columns can be a list or ndarray -
        # in case of an ndarray the empty check "not styler.hidden_rows"
        # raises a:
        # ValueError: The truth value of an array with more than one element is ambiguous. Use a.any() or a.all()
        self.__has_hidden_rows = len(styler.hidden_rows) > 0
        self.__has_hidden_columns = len(styler.hidden_columns) > 0
        self.__styler = styler
        self.__styler_todos = [StylerTodo.from_tuple(t) for t in styler._todo]
        super().__init__(styler.data, filter_criteria)

    def get_table_frame_generator(self) -> AbstractTableFrameGenerator:
        # local import to resolve cyclic import
        from cms_rendner_sdfv.pandas.styler.table_frame_generator import TableFrameGenerator
        return TableFrameGenerator(self)

    def get_styler_todos(self):
        return self.__styler_todos

    def compute_styled_chunk(self,
                             region: Region,
                             todos: Optional[List[StylerTodo]] = None,
                             ) -> StyledChunk:
        org_styler = self.__styler
        region = self.visible_frame.region.get_bounded_region(region)

        # Create a new styler which refers to the same DataFrame to not pollute original styler
        chunk_styler = org_styler.data.style
        # assign patched todos
        # The apply/map params are patched to not operate outside the chunk bounds.
        chunk_styler._todo = TodosPatcher().patch_todos_for_chunk(
            self.__styler_todos if todos is None else todos,
            org_styler.data,
            # The plugin only renders the visible (non-hidden cols/rows) of the styled DataFrame.
            # Therefore, create chunk from the visible data.
            self.visible_frame.to_frame(region),
        )
        # Compute the styling for the chunk by operating on the original DataFrame.
        # The computed styler contains only entries for the cells of the chunk,
        # this is ensured by the patched todos.
        chunk_styler._compute()

        # copy over some required state props
        #
        # Fix for "_default_formatter" to detect float32, float64, int32 and float64 values
        # Fixed in pandas 1.4: https://github.com/pandas-dev/pandas/pull/46119
        #
        # Fix is required because "df.iat[row, col]" is used instead of "df.itertuples" in the TableFrameGenerator.
        # Styler.to_html() uses "df.itertuples" to convert cell values into html and does not run into this problem.
        # In case of a specific float/int "itertuples" returns a float/int instead of the correct
        # float32, float64, int32 and float64.
        chunk_styler._display_funcs = copy(org_styler._display_funcs)
        def_precision = get_option("display.precision")
        chunk_styler._display_funcs.default_factory = lambda: partial(_fixed_default_formatter, precision=def_precision)

        chunk_styler.hide_index_ = org_styler.hide_index_

        chunk_styler.hide_columns_ = org_styler.hide_columns_

        return StyledChunk(
            styler=chunk_styler,
            visible_frame=self.visible_frame,
            region=region,
        )

    def _get_initial_visible_frame_indexes(self):
        index, columns = super()._get_initial_visible_frame_indexes()

        if self.__has_hidden_columns:
            columns = columns.delete(Index(self.__styler.hidden_columns))
        if self.__has_hidden_rows:
            index = index.delete(Index(self.__styler.hidden_rows))

        return index, columns
