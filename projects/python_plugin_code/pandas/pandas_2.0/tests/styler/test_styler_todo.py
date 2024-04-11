from typing import Any

from pandas import DataFrame, Series

from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo
from tests.helpers.extract_first_todo import extract_first_todo


def test_is_applymap_tuple_with_applymap():
    def style_func(scalar: Any):
        return scalar

    styler = DataFrame().style.applymap(style_func)
    assert StylerTodo.is_applymap_tuple(styler._todo[0])


def test_is_applymap_tuple_with_apply():
    def style_func(series: Series):
        return series

    styler = DataFrame().style.apply(style_func)
    assert StylerTodo.is_applymap_tuple(styler._todo[0]) is False


def test_apply_parsing():
    def style_func(series: Series):
        return series

    styler = DataFrame().style.apply(style_func, axis='index', subset=['a', 'b'])
    actual = extract_first_todo(styler)

    assert actual.is_applymap() is False
    assert actual.apply_args.style_func == style_func
    assert actual.apply_args.axis == 'index'
    assert actual.apply_args.subset == ['a', 'b']


def test_apply_kwargs():
    def style_func(series: Series):
        return series

    styler = DataFrame().style.apply(style_func, name='abc')
    actual = extract_first_todo(styler)

    assert actual.style_func_kwargs == {'name': 'abc'}


def test_applymap_parsing():
    def style_func(scalar: Any):
        return scalar

    styler = DataFrame().style.applymap(style_func, subset=['a', 'b'])
    actual = extract_first_todo(styler)

    assert actual.is_applymap()
    assert actual.apply_args.style_func == style_func
    assert actual.apply_args.subset == ['a', 'b']


def test_applymap_kwargs():
    def style_func(scalar: Any):
        return scalar

    styler = DataFrame().style.applymap(style_func, name='abc')
    actual = extract_first_todo(styler)

    assert actual.style_func_kwargs == {'name': 'abc'}


def test_chunk_parent_for_function():
    def style_func(series: Series, chunk_parent: Any = None):
        return series

    styler = DataFrame().style.apply(style_func)
    actual = extract_first_todo(styler)

    assert actual.should_provide_chunk_parent()


def test_no_chunk_parent_for_function():
    def style_func(series: Series, chunk: Any = None):
        return series

    styler = DataFrame().style.apply(style_func)
    actual = extract_first_todo(styler)

    assert actual.should_provide_chunk_parent() is False


def test_chunk_parent_for_lambda():
    styler = DataFrame().style.apply(lambda x, chunk_parent: x)
    actual = extract_first_todo(styler)

    assert actual.should_provide_chunk_parent()


def test_no_chunk_parent_for_lambda():
    styler = DataFrame().style.apply(lambda x, chunk: x)
    actual = extract_first_todo(styler)

    assert actual.should_provide_chunk_parent() is False


def test_is_pandas_style_func():
    def my_style_func(series: Series):
        return series

    styler = DataFrame().style.apply(my_style_func)
    actual = extract_first_todo(styler)
    assert actual.is_pandas_style_func() is False

    styler = DataFrame().style.highlight_max()
    actual = extract_first_todo(styler)
    assert actual.is_pandas_style_func()


def test_to_tuple():
    def my_style_func(series: Series):
        return series

    styler = DataFrame().style.apply(my_style_func)
    actual = extract_first_todo(styler)
    assert actual.to_tuple() == styler._todo[0]
