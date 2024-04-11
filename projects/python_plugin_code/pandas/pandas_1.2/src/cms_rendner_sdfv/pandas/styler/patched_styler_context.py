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
from typing import List, Optional

from pandas import Index
from pandas.io.formats.style import Styler

from cms_rendner_sdfv.base.table_source import AbstractTableFrameGenerator
from cms_rendner_sdfv.base.types import Region
from cms_rendner_sdfv.pandas.shared.pandas_table_source_context import PandasTableSourceContext
from cms_rendner_sdfv.pandas.shared.types import FilterCriteria
from cms_rendner_sdfv.pandas.shared.visible_frame import VisibleFrame
from cms_rendner_sdfv.pandas.styler.apply_map_patcher import ApplyMapPatcher
from cms_rendner_sdfv.pandas.styler.apply_patcher import ApplyPatcher
from cms_rendner_sdfv.pandas.styler.background_gradient_patcher import BackgroundGradientPatcher
from cms_rendner_sdfv.pandas.styler.highlight_extrema_patcher import HighlightExtremaPatcher
from cms_rendner_sdfv.pandas.styler.style_function_name_resolver import StyleFunctionNameResolver
from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo
from cms_rendner_sdfv.pandas.styler.todo_patcher import TodoPatcher


class StyledChunk:
    def __init__(self, styler: Styler, visible_frame: VisibleFrame, region: Region):
        self.__styler = styler
        self.__visible_frame = visible_frame
        self.region = region

    @property
    def row_labels_hidden(self):
        return self.__styler.hidden_index

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


class PatchedStylerContext(PandasTableSourceContext):
    def __init__(self, styler: Styler, filter_criteria: Optional[FilterCriteria] = None):
        # hidden_columns can be a list or ndarray -
        # in case of an ndarray the empty check "not styler.hidden_columns"
        # raises a:
        # ValueError: The truth value of an array with more than one element is ambiguous. Use a.any() or a.all()
        self.__has_hidden_columns = len(styler.hidden_columns) > 0
        self.__styler = styler
        self.__todo_patcher_list: List[TodoPatcher] = self.__create_patchers([
            StylerTodo.from_tuple(idx, t)
            for idx, t in enumerate(styler._todo)
        ])
        super().__init__(styler.data, filter_criteria)

    def get_table_frame_generator(self) -> AbstractTableFrameGenerator:
        # local import to resolve cyclic import
        from cms_rendner_sdfv.pandas.styler.table_frame_generator import TableFrameGenerator
        return TableFrameGenerator(self)

    def get_todo_patcher_list(self) -> List[TodoPatcher]:
        return self.__todo_patcher_list

    def compute_styled_chunk(self,
                             region: Region,
                             patcher_list: Optional[List[TodoPatcher]] = None,
                             ) -> StyledChunk:
        org_styler = self.__styler
        region = self.visible_frame.region.get_bounded_region(region)

        # Create a new styler which refers to the same DataFrame to not pollute original styler
        copy = org_styler.data.style
        # assign patched todos
        # The apply/map params are patched to not operate outside the chunk bounds.
        chunk_df = self.visible_frame.to_frame(region)
        copy._todo = [
            # The plugin only renders the visible (non-hidden cols/rows) of the styled DataFrame.
            # Therefore, create chunk from the visible data.
            p.create_patched_todo(org_styler.data, chunk_df).to_tuple()
            for p in (patcher_list or self.__todo_patcher_list)
        ]
        # Compute the styling for the chunk by operating on the original DataFrame.
        # The computed styler contains only entries for the cells of the chunk,
        # this is ensured by the patched todos.
        copy._compute()

        # copy over some required state props
        copy._display_funcs = org_styler._display_funcs

        copy.hidden_index = org_styler.hidden_index

        return StyledChunk(
            styler=copy,
            visible_frame=self.visible_frame,
            region=region,
        )

    def _get_initial_visible_frame_indexes(self):
        index, columns = super()._get_initial_visible_frame_indexes()

        if self.__has_hidden_columns:
            columns = columns.delete(Index(self.__styler.hidden_columns))

        return index, columns

    def __create_patchers(self, todos: List[StylerTodo]) -> List[TodoPatcher]:
        result: List[TodoPatcher] = []

        for t in todos:
            if t.is_pandas_style_func():
                patcher = self.__get_patcher_for_supported_pandas_style_functions(t)
            else:
                patcher = ApplyMapPatcher(t) if t.is_applymap() else ApplyPatcher(t)

            if patcher is not None:
                result.append(patcher)

        return result

    @staticmethod
    def __get_patcher_for_supported_pandas_style_functions(todo: StylerTodo) -> Optional[TodoPatcher]:
        qname = StyleFunctionNameResolver.get_style_func_qname(todo)
        if StyleFunctionNameResolver.is_pandas_background_gradient(qname):
            return BackgroundGradientPatcher(todo)
        elif StyleFunctionNameResolver.is_pandas_highlight_min(qname, todo):
            return HighlightExtremaPatcher(todo)
        elif StyleFunctionNameResolver.is_pandas_highlight_max(qname, todo):
            return HighlightExtremaPatcher(todo)
        elif StyleFunctionNameResolver.is_pandas_highlight_null(qname):
            return ApplyMapPatcher(todo)
        elif StyleFunctionNameResolver.is_pandas_set_properties(qname):
            return ApplyMapPatcher(todo)
        return None
