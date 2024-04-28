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

from pandas import Index, DataFrame
from pandas.io.formats.style import Styler

from cms_rendner_sdfv.base.table_source import AbstractTableFrameGenerator
from cms_rendner_sdfv.base.types import Region
from cms_rendner_sdfv.pandas.shared.pandas_table_source_context import PandasTableSourceContext
from cms_rendner_sdfv.pandas.shared.types import FilterCriteria
from cms_rendner_sdfv.pandas.shared.visible_frame import VisibleFrame
from cms_rendner_sdfv.pandas.styler.apply_map_patcher import ApplyMapPatcher
from cms_rendner_sdfv.pandas.styler.apply_patcher import ApplyPatcher
from cms_rendner_sdfv.pandas.styler.background_gradient_patcher import BackgroundGradientPatcher
from cms_rendner_sdfv.pandas.styler.highlight_between_patcher import HighlightBetweenPatcher
from cms_rendner_sdfv.pandas.styler.highlight_extrema_patcher import HighlightMaxPatcher, HighlightMinPatcher
from cms_rendner_sdfv.pandas.styler.style_function_name_resolver import StyleFunctionNameResolver
from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo
from cms_rendner_sdfv.pandas.styler.todo_patcher import TodoPatcher


class StyledChunk:
    def __init__(self, styler: Styler, visible_frame: VisibleFrame, region: Region):
        self.__styler = styler
        self.__visible_frame = visible_frame
        self.region = region

    @property
    def column_labels_hidden(self):
        return self.__styler.hide_column_names

    @property
    def row_labels_hidden(self):
        return self.__styler.hide_index_names

    @property
    def hidden_row_level_map(self) -> List[bool]:
        return self.__styler.hide_index_

    @property
    def hidden_col_level_map(self) -> List[bool]:
        return self.__styler.hide_columns_

    def cell_css_at(self, row: int, col: int):
        return self.__styler.ctx[(row, col)]

    def row_labels_at(self, row: int):
        labels = self.__visible_frame.index_at(self.region.first_row + row)
        if not isinstance(labels, tuple):
            labels = [labels]
        org_row = self.__to_source_frame_cell_coordinates(row, 0)[0]
        return [
            self.__styler._display_funcs_index[(org_row, lvl)](lbl)
            for lvl, lbl in enumerate(labels)
            if not self.__styler.hide_index_[lvl]
        ]

    def col_labels_at(self, col: int):
        labels = self.__visible_frame.column_at(self.region.first_col + col)
        if not isinstance(labels, tuple):
            labels = [labels]
        org_col = self.__to_source_frame_cell_coordinates(0, col)[1]
        return [
            self.__styler._display_funcs_columns[(lvl, org_col)](lbl)
            for lvl, lbl in enumerate(labels)
            if not self.__styler.hide_columns_[lvl]
        ]

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


class StyledChunkComputer:
    def __init__(self,
                 visible_frame: VisibleFrame,
                 org_styler: Styler,
                 todo_patcher_list: List[TodoPatcher],
                 ):
        self.__visible_frame: VisibleFrame = visible_frame
        self.__org_styler: Styler = org_styler
        self.__todo_patcher_list: List[TodoPatcher] = todo_patcher_list

    def compute(self, region: Region) -> StyledChunk:
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
        chunk_styler._display_funcs = self.__org_styler._display_funcs
        chunk_styler._display_funcs_index = self.__org_styler._display_funcs_index
        chunk_styler._display_funcs_columns = self.__org_styler._display_funcs_columns

        chunk_styler.hide_index_ = self.__org_styler.hide_index_
        chunk_styler.hide_index_names = self.__org_styler.hide_index_names

        chunk_styler.hide_columns_ = self.__org_styler.hide_columns_
        chunk_styler.hide_column_names = self.__org_styler.hide_column_names

        return StyledChunk(
            styler=chunk_styler,
            visible_frame=self.__visible_frame,
            region=region,
        )


class PatchedStylerContext(PandasTableSourceContext):
    def __init__(self, styler: Styler, filter_criteria: Optional[FilterCriteria] = None):
        # hidden_rows and hidden_columns can be a list or ndarray -
        # in case of an ndarray the empty check "not styler.hidden_rows"
        # raises a:
        # ValueError: The truth value of an array with more than one element is ambiguous. Use a.any() or a.all()
        self.__has_hidden_rows = len(styler.hidden_rows) > 0
        self.__has_hidden_columns = len(styler.hidden_columns) > 0
        self.__styler = styler
        self.__todo_patcher_list: List[TodoPatcher] = self.__create_patchers(styler)
        super().__init__(styler.data, filter_criteria)

    def create_styled_chunk_computer_for_validation(self, chunk: DataFrame, patcher: TodoPatcher) -> 'StyledChunkComputer':
        return StyledChunkComputer(
            visible_frame=VisibleFrame(chunk),
            org_styler=self.__styler,
            todo_patcher_list=[patcher.patcher_for_style_func_validation(chunk)]
        )

    def get_table_frame_generator(self) -> AbstractTableFrameGenerator:
        # local import to resolve cyclic import
        from cms_rendner_sdfv.pandas.styler.table_frame_generator import TableFrameGenerator
        return TableFrameGenerator(self)

    def get_todo_patcher_list(self) -> List[TodoPatcher]:
        return self.__todo_patcher_list

    def compute_styled_chunk(self, region: Region) -> StyledChunk:
        return StyledChunkComputer(
            visible_frame=self.visible_frame,
            org_styler=self.__styler,
            todo_patcher_list=self.__todo_patcher_list,
        ).compute(region)

    def _get_initial_visible_frame_indexes(self):
        index, columns = super()._get_initial_visible_frame_indexes()

        if self.__has_hidden_columns:
            columns = columns.delete(Index(self.__styler.hidden_columns))
        if self.__has_hidden_rows:
            index = index.delete(Index(self.__styler.hidden_rows))

        return index, columns

    def __create_patchers(self, styler: Styler) -> List[TodoPatcher]:
        result: List[TodoPatcher] = []

        org_frame = styler.data
        for idx, t in enumerate(styler._todo):
            st = StylerTodo.from_tuple(idx, t)
            if st.is_pandas_style_func():
                patcher = self.__get_patcher_for_supported_pandas_style_functions(org_frame, st)
            else:
                patcher = ApplyMapPatcher(org_frame, st) if st.is_applymap() else ApplyPatcher(org_frame, st)

            if patcher is not None:
                result.append(patcher)

        return result

    @staticmethod
    def __get_patcher_for_supported_pandas_style_functions(org_frame: DataFrame, todo: StylerTodo) -> Optional[TodoPatcher]:
        qname = StyleFunctionNameResolver.get_style_func_qname(todo)
        if StyleFunctionNameResolver.is_pandas_text_gradient(qname, todo):
            return BackgroundGradientPatcher(org_frame, todo)
        elif StyleFunctionNameResolver.is_pandas_background_gradient(qname):
            return BackgroundGradientPatcher(org_frame, todo)
        elif StyleFunctionNameResolver.is_pandas_highlight_max(qname, todo):
            return HighlightMaxPatcher(org_frame, todo)
        elif StyleFunctionNameResolver.is_pandas_highlight_min(qname, todo):
            return HighlightMinPatcher(org_frame, todo)
        elif StyleFunctionNameResolver.is_pandas_highlight_null(qname):
            return ApplyPatcher(org_frame, todo)
        elif StyleFunctionNameResolver.is_pandas_highlight_between(qname):
            return HighlightBetweenPatcher(org_frame, todo)
        elif StyleFunctionNameResolver.is_pandas_set_properties(qname):
            return ApplyMapPatcher(org_frame, todo)
        return None
