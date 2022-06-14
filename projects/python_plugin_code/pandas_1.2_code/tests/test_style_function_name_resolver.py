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
from pandas import DataFrame
from pandas.io.formats.style import Styler

from plugin_code.style_function_name_resolver import StyleFunctionNameResolver
from plugin_code.styler_todo import StylerTodo


def _decode_first_todo(styler: Styler) -> StylerTodo:
    return StylerTodo.from_tuple(styler._todo[0])


def _resolve_style_func_display_name(styler: Styler) -> str:
    return StyleFunctionNameResolver.resolve_style_func_name(_decode_first_todo(styler))


def _get_style_func_qname(todo: StylerTodo) -> str:
    return StyleFunctionNameResolver.get_style_func_qname(todo)


def test_pandas_background_gradient_display_name():
    actual = _resolve_style_func_display_name(DataFrame().style.background_gradient())
    expected = "background_gradient"
    assert actual == expected


def test_pandas_highlight_max_display_name():
    actual = _resolve_style_func_display_name(DataFrame().style.highlight_max())
    expected = "highlight_max"
    assert actual == expected


def test_pandas_highlight_min_display_name():
    actual = _resolve_style_func_display_name(DataFrame().style.highlight_min())
    expected = "highlight_min"
    assert actual == expected


def test_pandas_highlight_null_display_name():
    actual = _resolve_style_func_display_name(DataFrame().style.highlight_null())
    expected = "highlight_null"
    assert actual == expected


def test_pandas_set_properties_display_name():
    actual = _resolve_style_func_display_name(DataFrame().style.set_properties())
    expected = "set_properties"
    assert actual == expected


def test_is_pandas_background_gradient():
    todo = _decode_first_todo(DataFrame().style.background_gradient())
    qname = _get_style_func_qname(todo)
    assert StyleFunctionNameResolver.is_pandas_background_gradient(qname)


def test_is_pandas_highlight_max():
    todo = _decode_first_todo(DataFrame().style.highlight_max())
    qname = _get_style_func_qname(todo)
    assert StyleFunctionNameResolver.is_pandas_highlight_max(qname, todo)


def test_is_pandas_highlight_min():
    todo = _decode_first_todo(DataFrame().style.highlight_min())
    qname = _get_style_func_qname(todo)
    assert StyleFunctionNameResolver.is_pandas_highlight_min(qname, todo)


def test_is_pandas_highlight_null():
    todo = _decode_first_todo(DataFrame().style.highlight_null())
    qname = _get_style_func_qname(todo)
    assert StyleFunctionNameResolver.is_pandas_highlight_null(qname)


def test_is_pandas_set_properties():
    todo = _decode_first_todo(DataFrame().style.set_properties())
    qname = _get_style_func_qname(todo)
    assert StyleFunctionNameResolver.is_pandas_set_properties(qname)
