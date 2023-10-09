#  Copyright 2023 cms.rendner (Daniel Schmidt)
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
from plugin_code.map_patcher import MapPatcher
from plugin_code.apply_patcher import ApplyPatcher
from plugin_code.background_gradient_patcher import BackgroundGradientPatcher
from plugin_code.highlight_between_patcher import HighlightBetweenPatcher
from plugin_code.highlight_extrema_patcher import HighlightExtremaPatcher
from plugin_code.style_function_name_resolver import StyleFunctionNameResolver
from plugin_code.styler_todo import StylerTodo
from plugin_code.todo_patcher import TodoPatcher

# == copy after here ==
from typing import Callable, Optional

from pandas import DataFrame


class TodosPatcher:

    def patch_todos_for_chunk(self,
                              todos: list[StylerTodo],
                              org_frame: DataFrame,
                              chunk: DataFrame,
                              ) -> list[tuple[Callable, tuple, dict]]:
        result: list[tuple[Callable, tuple, dict]] = []

        for t in todos:

            if t.is_pandas_style_func():
                patcher = self.__get_patcher_for_pandas_style_function(t)
            else:
                if t.is_map():
                    patcher = MapPatcher(t)
                else:
                    patcher = ApplyPatcher(t)

            if patcher is not None:
                result.append(patcher.create_patched_todo(org_frame, chunk).to_tuple())

        return result

    @staticmethod
    def is_style_function_supported(todo: StylerTodo) -> bool:
        if todo.is_pandas_style_func():
            return TodosPatcher.__get_patcher_for_pandas_style_function(todo) is not None
        return True

    @staticmethod
    def __get_patcher_for_pandas_style_function(todo: StylerTodo) -> Optional[TodoPatcher]:
        qname = StyleFunctionNameResolver.get_style_func_qname(todo)
        if StyleFunctionNameResolver.is_pandas_text_gradient(qname, todo):
            return BackgroundGradientPatcher(todo)
        elif StyleFunctionNameResolver.is_pandas_background_gradient(qname):
            return BackgroundGradientPatcher(todo)
        elif StyleFunctionNameResolver.is_pandas_highlight_max(qname, todo):
            return HighlightExtremaPatcher(todo, 'max')
        elif StyleFunctionNameResolver.is_pandas_highlight_min(qname, todo):
            return HighlightExtremaPatcher(todo, 'min')
        elif StyleFunctionNameResolver.is_pandas_highlight_null(qname):
            return ApplyPatcher(todo)
        elif StyleFunctionNameResolver.is_pandas_highlight_between(qname):
            return HighlightBetweenPatcher(todo)
        elif StyleFunctionNameResolver.is_pandas_set_properties(qname):
            return MapPatcher(todo)
        return None
