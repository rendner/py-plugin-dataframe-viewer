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
from plugin_code.styler_todo import StylerTodo
from plugin_code.todo_patcher import TodoPatcher

# == copy after here ==
import inspect
from functools import partial
from typing import Callable, List, Tuple, Optional

from pandas import DataFrame
from pandas.io.formats.style import Styler


class TodosPatcher:

    # chunk was created by using only non-hidden rows/cols
    def patch_todos_for_chunk(self, source: Styler, chunk: DataFrame) -> List[Tuple[Callable, tuple, dict]]:
        result: List[Tuple[Callable, tuple, dict]] = []

        for t in source._todo:
            todo = StylerTodo.from_tuple(t)

            if self.__is_builtin_style(todo.apply_args.style_func):
                patcher = self.__get_patcher_for_builtin_style(source.data, todo)
            else:
                if todo.is_applymap_call():
                    patcher = ApplyMapPatcher(source.data, todo)
                else:
                    patcher = ApplyPatcher(source.data, todo)

            if patcher is not None:
                result.append(patcher.create_patched_todo(chunk).to_tuple())

        return result

    def __get_patcher_for_builtin_style(self, df: DataFrame, todo: StylerTodo) -> Optional[TodoPatcher]:
        style_func_qname = self.__get_qname(todo.apply_args.style_func)
        if self.__is_builtin_background_gradient(style_func_qname):
            return BackgroundGradientPatcher(df, todo)
        elif self.__is_builtin_highlight_max(style_func_qname, todo.apply_args.style_func):
            return HighlightExtremaPatcher(df, todo, 'max')
        elif self.__is_builtin_highlight_min(style_func_qname, todo.apply_args.style_func):
            return HighlightExtremaPatcher(df, todo, 'min')
        elif self.__is_builtin_highlight_null(style_func_qname):
            return ApplyPatcher(df, todo)
        elif self.__is_builtin_highlight_between(style_func_qname):
            return HighlightBetweenPatcher(df, todo)
        elif self.__is_builtin_set_properties(style_func_qname):
            return ApplyMapPatcher(df, todo)
        return None

    @staticmethod
    def __get_qname(func: Callable) -> str:
        if isinstance(func, partial):
            func = func.func
        return getattr(func, '__qualname__', '')

    @staticmethod
    def __is_builtin_style(style_func_qname: Callable) -> bool:
        if isinstance(style_func_qname, partial):
            style_func_qname = style_func_qname.func
        inspect_result = inspect.getmodule(style_func_qname)
        return False if inspect_result is None else inspect.getmodule(
            style_func_qname).__name__ == 'pandas.io.formats.style'

    @staticmethod
    def __is_builtin_background_gradient(style_func_qname: str) -> bool:
        return style_func_qname == '_background_gradient'

    @staticmethod
    def __is_builtin_highlight_max(style_func_qname: str, style_func: partial) -> bool:
        return style_func_qname == '_highlight_value' and style_func.keywords.get('op', '') == 'max'

    @staticmethod
    def __is_builtin_highlight_min(style_func_qname: str, style_func: partial) -> bool:
        return style_func_qname == '_highlight_value' and style_func.keywords.get('op', '') == 'min'

    @staticmethod
    def __is_builtin_highlight_null(style_func_qname: str) -> bool:
        return style_func_qname.startswith('Styler.highlight_null')

    @staticmethod
    def __is_builtin_highlight_between(style_func_qname: str) -> bool:
        return style_func_qname == '_highlight_between'

    @staticmethod
    def __is_builtin_set_properties(style_func_qname: str) -> bool:
        return style_func_qname.startswith('Styler.set_properties')
