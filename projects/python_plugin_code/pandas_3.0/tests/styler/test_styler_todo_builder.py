from pandas import DataFrame, Series

from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodoBuilder
from tests.helpers.extract_first_todo import extract_first_todo


def test_to_tuple():
    def my_style_func(series: Series):
        return series

    styler = DataFrame().style.apply(my_style_func, subset=['a'])
    todo = extract_first_todo(styler)

    actual = StylerTodoBuilder(todo).build()
    assert actual.to_tuple() == styler._todo[0]


def test_replace_subset():
    def my_style_func(series: Series):
        return series

    styler = DataFrame().style.apply(my_style_func, subset=['a'])
    todo = extract_first_todo(styler)

    actual = StylerTodoBuilder(todo).with_subset(None).build()
    assert actual.apply_args.subset is None

    actual = StylerTodoBuilder(todo).with_subset(['b']).build()
    assert actual.apply_args.subset == ['b']


def test_replace_style_function():
    def my_style_func(series: Series):
        return series

    def my_other_style_func(series: Series):
        return series

    styler = DataFrame().style.apply(my_style_func)
    todo = extract_first_todo(styler)

    actual = StylerTodoBuilder(todo).with_style_func(my_other_style_func).build()
    assert actual.apply_args.style_func == my_other_style_func


def test_replace_style_function_kwargs():
    def my_style_func(series: Series):
        return series

    styler = DataFrame().style.apply(my_style_func, name="abc")
    todo = extract_first_todo(styler)

    actual = StylerTodoBuilder(todo).with_style_func_kwargs({'age': 12}).build()
    assert actual.style_func_kwargs == {'age': 12}
