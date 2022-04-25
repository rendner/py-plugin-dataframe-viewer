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

from plugin_code.styler_todo import StylerTodo
from plugin_code.todos_patcher import TodosPatcher


def test_is_supported__highlight_null():
    todo = StylerTodo.from_tuple(DataFrame().style.highlight_null()._todo[0])
    assert TodosPatcher.is_style_function_supported(todo)


def test_is_supported__highlight_min():
    todo = StylerTodo.from_tuple(DataFrame().style.highlight_min()._todo[0])
    assert TodosPatcher.is_style_function_supported(todo)


def test_is_supported__highlight_max():
    todo = StylerTodo.from_tuple(DataFrame().style.highlight_max()._todo[0])
    assert TodosPatcher.is_style_function_supported(todo)


def test_is_supported__background_gradient():
    todo = StylerTodo.from_tuple(DataFrame().style.background_gradient()._todo[0])
    assert TodosPatcher.is_style_function_supported(todo)


def test_is_supported__set_properties():
    todo = StylerTodo.from_tuple(DataFrame().style.set_properties({})._todo[0])
    assert TodosPatcher.is_style_function_supported(todo)


def test_is_supported__text_gradient():
    todo = StylerTodo.from_tuple(DataFrame().style.text_gradient()._todo[0])
    assert TodosPatcher.is_style_function_supported(todo)


def test_is_supported__highlight_between():
    todo = StylerTodo.from_tuple(DataFrame().style.highlight_between()._todo[0])
    assert TodosPatcher.is_style_function_supported(todo)


def test_is_supported__highlight_quantile():
    todo = StylerTodo.from_tuple(DataFrame().style.highlight_quantile()._todo[0])
    assert TodosPatcher.is_style_function_supported(todo)


def test_is_supported__bar():
    todo = StylerTodo.from_tuple(DataFrame().style.bar()._todo[0])
    assert not TodosPatcher.is_style_function_supported(todo)
