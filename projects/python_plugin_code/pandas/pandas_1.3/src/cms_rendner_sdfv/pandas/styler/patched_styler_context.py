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
from typing import List, Optional

from pandas import Index, DataFrame
from pandas.io.formats.style import Styler

from cms_rendner_sdfv.base.types import TableStructureColumn, TableStructureColumnInfo, \
    TableStructureLegend
from cms_rendner_sdfv.pandas.shared.meta_computer import MetaComputer
from cms_rendner_sdfv.pandas.shared.pandas_table_source_context import PandasTableSourceContext
from cms_rendner_sdfv.pandas.shared.types import FilterCriteria
from cms_rendner_sdfv.pandas.shared.visible_frame import VisibleFrame
from cms_rendner_sdfv.pandas.styler.apply_map_patcher import ApplyMapPatcher
from cms_rendner_sdfv.pandas.styler.apply_patcher import ApplyPatcher
from cms_rendner_sdfv.pandas.styler.background_gradient_patcher import BackgroundGradientPatcher
from cms_rendner_sdfv.pandas.styler.chunk_computer import ChunkComputer
from cms_rendner_sdfv.pandas.styler.chunk_data_generator import ChunkDataGenerator
from cms_rendner_sdfv.pandas.styler.highlight_between_patcher import HighlightBetweenPatcher
from cms_rendner_sdfv.pandas.styler.highlight_extrema_patcher import HighlightMaxPatcher, HighlightMinPatcher
from cms_rendner_sdfv.pandas.styler.style_function_name_resolver import StyleFunctionNameResolver
from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo
from cms_rendner_sdfv.pandas.styler.todo_patcher import TodoPatcher


class PatchedStylerContext(PandasTableSourceContext):
    def __init__(self, styler: Styler, filter_criteria: Optional[FilterCriteria] = None):
        # hidden_rows and hidden_columns can be a list or ndarray -
        # in case of an ndarray the empty check "not styler.hidden_rows"
        # raises a:
        # ValueError: The truth value of an array with more than one element is ambiguous. Use a.any() or a.all()
        self.__has_hidden_rows: bool = len(styler.hidden_rows) > 0
        self.__has_hidden_columns: bool = len(styler.hidden_columns) > 0
        self.__styler: Styler = styler
        self.__todo_patcher_list: List[TodoPatcher] = self.__create_patchers(styler)
        super().__init__(styler.data, filter_criteria)

    def unlink(self):
        super().unlink()
        self.__styler = None
        [x.unlink() for x in self.__todo_patcher_list]
        self.__todo_patcher_list = None

    def create_extractor_for_style_func_validation(
            self,
            chunk: DataFrame,
            patcher: TodoPatcher,
    ) -> ChunkComputer:
        return ChunkComputer(
            visible_frame=VisibleFrame(chunk),
            org_styler=self.__styler,
            todo_patcher_list=[patcher.patcher_for_style_func_validation(chunk)],
            formatter=self._formatter,
            meta_computer=MetaComputer(chunk),
        )

    def get_chunk_data_generator(self):
        return ChunkDataGenerator(
            self._visible_frame.region,
            ChunkComputer(
                visible_frame=self._visible_frame,
                org_styler=self.__styler,
                todo_patcher_list=self.__todo_patcher_list,
                formatter=self._formatter,
                meta_computer=self._meta_computer,
            ),
        )

    def get_todo_patcher_list(self) -> List[TodoPatcher]:
        return self.__todo_patcher_list

    def _get_frame_column_info(self) -> TableStructureColumnInfo:
        frame = self.__styler.data

        ts_columns = []
        dtypes = frame.dtypes
        nlevels = frame.columns.nlevels
        for col in self._visible_frame.get_column_indices():
            col_label = frame.columns[col]
            labels = [col_label] if nlevels == 1 else col_label
            labels = [self._formatter.format_column(lbl) for lbl in labels]
            col_dtype = dtypes[col_label]
            ts_columns.append(
                TableStructureColumn(
                    dtype=str(col_dtype),
                    labels=labels,
                    id=col,
                    text_align=self._get_column_text_align(col_dtype),
                )
            )

        index_legend = [] if self.__styler.hide_index_ else [
            self._formatter.format_index(lbl)
            for lbl in self._visible_frame.index_names
            if lbl is not None
        ]

        column_legend = [] if self.__styler.hide_columns_ else [
            self._formatter.format_index(lbl)
            for lbl in self._visible_frame.column_names
            if lbl is not None
        ]

        legend = TableStructureLegend(
            index=index_legend,
            column=column_legend,
        ) if index_legend or column_legend else None

        return TableStructureColumnInfo(columns=ts_columns, legend=legend)

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
