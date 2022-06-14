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
from plugin_code.styler_todo import StylerTodo

# == copy after here ==
from functools import partial


class StyleFunctionNameResolver:

    @staticmethod
    def get_style_func_name(todo: StylerTodo) -> str:
        func = todo.apply_args.style_func
        if isinstance(func, partial):
            func = func.func
        return getattr(func, '__qualname__', '')

    @staticmethod
    def get_style_func_display_name(todo: StylerTodo) -> str:
        name = StyleFunctionNameResolver.get_style_func_name(todo)
        if todo.is_pandas_style_func():
            if StyleFunctionNameResolver.is_pandas_background_gradient(name):
                return "background_gradient"
            elif StyleFunctionNameResolver.is_pandas_highlight_max(name, todo):
                return "highlight_max"
            elif StyleFunctionNameResolver.is_pandas_highlight_min(name, todo):
                return "highlight_min"
            elif StyleFunctionNameResolver.is_pandas_highlight_null(name):
                return "highlight_null"
            elif StyleFunctionNameResolver.is_pandas_set_properties(name):
                return "set_properties"
            else:
                return name.rpartition('.')[2]
        else:
            return name.rpartition('.')[2]

    @staticmethod
    def is_pandas_background_gradient(style_func_qname: str) -> bool:
        return style_func_qname == 'Styler._background_gradient'

    @staticmethod
    def is_pandas_highlight_max(style_func_qname: str, todo: StylerTodo) -> bool:
        return style_func_qname.startswith('Styler._highlight_extrema') and todo.style_func_kwargs.get('max_', False)

    @staticmethod
    def is_pandas_highlight_min(style_func_qname: str, todo: StylerTodo) -> bool:
        return style_func_qname.startswith('Styler._highlight_extrema') and not todo.style_func_kwargs.get('max_', False)

    @staticmethod
    def is_pandas_highlight_null(style_func_qname: str) -> bool:
        return style_func_qname.startswith('Styler._highlight_null')

    @staticmethod
    def is_pandas_set_properties(style_func_qname: str) -> bool:
        return style_func_qname.startswith('Styler.set_properties')
