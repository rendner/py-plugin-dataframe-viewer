from pandas import DataFrame

from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo
from cms_rendner_sdfv.pandas.styler.todos_patcher import TodosPatcher


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


def test_is_supported__bar():
    todo = StylerTodo.from_tuple(DataFrame().style.bar()._todo[0])
    assert not TodosPatcher.is_style_function_supported(todo)
