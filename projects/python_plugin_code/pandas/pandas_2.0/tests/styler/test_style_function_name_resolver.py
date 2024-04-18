from pandas import DataFrame
from pandas.io.formats.style import Styler

from cms_rendner_sdfv.pandas.styler.style_function_name_resolver import StyleFunctionNameResolver
from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo
from tests.helpers.extract_first_todo import extract_first_todo


def _resolve_style_func_display_name(styler: Styler) -> str:
    return StyleFunctionNameResolver.resolve_style_func_name(extract_first_todo(styler))


def _get_style_func_qname(todo: StylerTodo) -> str:
    return StyleFunctionNameResolver.get_style_func_qname(todo)


def test_pandas_background_gradient_display_name():
    actual = _resolve_style_func_display_name(DataFrame().style.background_gradient())
    expected = "background_gradient"
    assert actual == expected


def test_pandas_highlight_between_display_name():
    actual = _resolve_style_func_display_name(DataFrame().style.highlight_between())
    expected = "highlight_between or highlight_quantile"
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


def test_pandas_highlight_quantile_display_name():
    actual = _resolve_style_func_display_name(DataFrame().style.highlight_quantile())
    expected = "highlight_between or highlight_quantile"
    assert actual == expected


def test_pandas_set_properties_display_name():
    actual = _resolve_style_func_display_name(DataFrame().style.set_properties())
    expected = "set_properties"
    assert actual == expected


def test_pandas_text_gradient_display_name():
    actual = _resolve_style_func_display_name(DataFrame().style.text_gradient())
    expected = "text_gradient"
    assert actual == expected


def test_is_pandas_background_gradient():
    todo = extract_first_todo(DataFrame().style.background_gradient())
    qname = _get_style_func_qname(todo)
    assert StyleFunctionNameResolver.is_pandas_background_gradient(qname)


def test_is_pandas_highlight_between():
    todo = extract_first_todo(DataFrame().style.highlight_between())
    qname = _get_style_func_qname(todo)
    assert StyleFunctionNameResolver.is_pandas_highlight_between(qname)

    todo = extract_first_todo(DataFrame().style.highlight_quantile())
    qname = _get_style_func_qname(todo)
    assert StyleFunctionNameResolver.is_pandas_highlight_between(qname)


def test_is_pandas_highlight_max():
    todo = extract_first_todo(DataFrame().style.highlight_max())
    qname = _get_style_func_qname(todo)
    assert StyleFunctionNameResolver.is_pandas_highlight_max(qname, todo)


def test_is_pandas_highlight_min():
    todo = extract_first_todo(DataFrame().style.highlight_min())
    qname = _get_style_func_qname(todo)
    assert StyleFunctionNameResolver.is_pandas_highlight_min(qname, todo)


def test_is_pandas_highlight_null():
    todo = extract_first_todo(DataFrame().style.highlight_null())
    qname = _get_style_func_qname(todo)
    assert StyleFunctionNameResolver.is_pandas_highlight_null(qname)


def test_is_pandas_set_properties():
    todo = extract_first_todo(DataFrame().style.set_properties())
    qname = _get_style_func_qname(todo)
    assert StyleFunctionNameResolver.is_pandas_set_properties(qname)


def test_is_pandas_text_gradient():
    todo = extract_first_todo(DataFrame().style.text_gradient())
    qname = _get_style_func_qname(todo)
    assert StyleFunctionNameResolver.is_pandas_text_gradient(qname, todo)
