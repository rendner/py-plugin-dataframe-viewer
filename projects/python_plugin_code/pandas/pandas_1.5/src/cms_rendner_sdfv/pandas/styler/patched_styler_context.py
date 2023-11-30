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
from typing import Callable, List, Optional, Tuple

from pandas import DataFrame, Index
from pandas.io.formats.style import Styler

from cms_rendner_sdfv.base.table_source import AbstractTableFrameGenerator, TableFrameValidator
from cms_rendner_sdfv.pandas.shared.pandas_table_source_context import PandasTableSourceContext
from cms_rendner_sdfv.pandas.shared.types import FilterCriteria
from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo
from cms_rendner_sdfv.pandas.styler.todos_patcher import TodosPatcher


class PatchedStylerContext(PandasTableSourceContext):
    def __init__(self, styler: Styler, filter_criteria: Optional[FilterCriteria] = None):
        # hidden_rows and hidden_columns can be a list or ndarray -
        # in case of an ndarray the empty check "not styler.hidden_rows"
        # raises a:
        # ValueError: The truth value of an array with more than one element is ambiguous. Use a.any() or a.all()
        self._has_hidden_rows = len(styler.hidden_rows) > 0
        self._has_hidden_columns = len(styler.hidden_columns) > 0
        self._styler = styler
        self._styler_todos = [StylerTodo.from_tuple(t) for t in styler._todo]
        super().__init__(styler.data, filter_criteria)

    def get_table_frame_generator(self) -> AbstractTableFrameGenerator:
        # local import to resolve cyclic import
        from cms_rendner_sdfv.pandas.styler.table_frame_generator import TableFrameGenerator
        return TableFrameGenerator(self)

    def get_styler(self) -> Styler:
        return self._styler

    def get_styler_todos(self):
        return self._styler_todos

    def get_todo_validator(self, todo: StylerTodo) -> TableFrameValidator:
        from cms_rendner_sdfv.pandas.styler.table_frame_generator import TableFrameGenerator
        return TableFrameValidator(self.visible_frame.region, TableFrameGenerator(self, lambda x: x is todo))

    def create_patched_todos(self,
                             chunk: DataFrame,
                             todos_filter: Optional[Callable[[StylerTodo], bool]] = None,
                             ) -> List[Tuple[Callable, tuple, dict]]:
        filtered_todos = self._styler_todos if todos_filter is None else list(filter(todos_filter, self._styler_todos))
        return TodosPatcher().patch_todos_for_chunk(filtered_todos, self._source_frame, chunk)

    def _get_initial_visible_frame_indexes(self):
        index, columns = super()._get_initial_visible_frame_indexes()

        if self._has_hidden_columns:
            columns = columns.delete(Index(self._styler.hidden_columns))
        if self._has_hidden_rows:
            index = index.delete(Index(self._styler.hidden_rows))

        return index, columns
