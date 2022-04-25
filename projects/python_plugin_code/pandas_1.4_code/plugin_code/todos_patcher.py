#  Copyright 2022 cms.rendner (Daniel Schmidt)
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
from plugin_code.apply_map_patcher import ApplyMapPatcher
from plugin_code.apply_patcher import ApplyPatcher
from plugin_code.background_gradient_patcher import BackgroundGradientPatcher
from plugin_code.highlight_between_patcher import HighlightBetweenPatcher
from plugin_code.highlight_extrema_patcher import HighlightExtremaPatcher
from plugin_code.style_function_name_resolver import StyleFunctionNameResolver
from plugin_code.styler_todo import StylerTodo
from plugin_code.todo_patcher import TodoPatcher

# == copy after here ==
from typing import Callable, List, Tuple, Optional

from pandas import DataFrame
from pandas.io.formats.style import Styler


class TodosPatcher:

    # chunk was created by using only non-hidden rows/cols
    def patch_todos_for_chunk(self, source: Styler, chunk: DataFrame) -> List[Tuple[Callable, tuple, dict]]:
        result: List[Tuple[Callable, tuple, dict]] = []

        for t in source._todo:
            todo = StylerTodo.from_tuple(t)

            if todo.is_pandas_style_func():
                patcher = self.__get_patcher_for_pandas_style_function(source.data, todo)
            else:
                if todo.is_applymap_call():
                    patcher = ApplyMapPatcher(source.data, todo)
                else:
                    patcher = ApplyPatcher(source.data, todo)

            if patcher is not None:
                result.append(patcher.create_patched_todo(chunk).to_tuple())

        return result

    @staticmethod
    def is_style_function_supported(todo: StylerTodo) -> bool:
        if todo.is_pandas_style_func():
            return TodosPatcher.__get_patcher_for_pandas_style_function(DataFrame(), todo) is not None
        return True

    @staticmethod
    def __get_patcher_for_pandas_style_function(df: DataFrame, todo: StylerTodo) -> Optional[TodoPatcher]:
        name = StyleFunctionNameResolver.get_style_func_name(todo)
        if StyleFunctionNameResolver.is_pandas_text_gradient(name, todo):
            return BackgroundGradientPatcher(df, todo)
        elif StyleFunctionNameResolver.is_pandas_background_gradient(name):
            return BackgroundGradientPatcher(df, todo)
        elif StyleFunctionNameResolver.is_pandas_highlight_max(name, todo):
            return HighlightExtremaPatcher(df, todo, 'max')
        elif StyleFunctionNameResolver.is_pandas_highlight_min(name, todo):
            return HighlightExtremaPatcher(df, todo, 'min')
        elif StyleFunctionNameResolver.is_pandas_highlight_null(name):
            return ApplyPatcher(df, todo)
        elif StyleFunctionNameResolver.is_pandas_highlight_between(name):
            return HighlightBetweenPatcher(df, todo)
        elif StyleFunctionNameResolver.is_pandas_set_properties(name):
            return ApplyMapPatcher(df, todo)
        return None
