import numpy as np
import pandas as pd
import pytest
from pandas import DataFrame, MultiIndex, Series
from pandas.io.formats.style import Styler

from cms_rendner_sdfv.base.types import Region
from cms_rendner_sdfv.pandas.shared.types import FilterCriteria
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext
from cms_rendner_sdfv.pandas.styler.style_functions_validator import StyleFunctionsValidator
from cms_rendner_sdfv.pandas.styler.types import StyleFunctionValidationProblem, StyleFunctionInfo

np.random.seed(123456)

midx = MultiIndex.from_product([["x", "y"], ["a", "b", "c"]])
mi_df = DataFrame(np.random.randn(6, 6), index=midx, columns=midx)
mi_df.index.names = ["lev0", "lev1"]

df = DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


def _create_validator(style: Styler) -> StyleFunctionsValidator:
    return StyleFunctionsValidator(PatchedStylerContext(style))


@pytest.mark.parametrize(
    "region", [
        Region(1, 2, 1, 2),
        Region(0, 0, len(mi_df.index) // 2, len(mi_df.columns) // 2),
        Region(0, 0, len(mi_df.index), len(mi_df.columns)),
        Region(3, 3, 100, 100)
    ])
def test_background_gradient(region: Region):
    styler = mi_df.style.background_gradient()

    validator = _create_validator(styler)
    result = validator.validate(region)

    assert len(result) == 0
    assert len(validator.failed_patchers) == 0


@pytest.mark.parametrize(
    "region", [
        Region(1, 2, 1, 2),
        Region(0, 0, 0, 0),
        Region(0, 0, 6, 6)
    ])
def test_empty_df_with_styles(region: Region):
    empty_df = DataFrame()
    styler = empty_df.style.highlight_max().highlight_min()

    validator = _create_validator(styler)
    result = validator.validate(region)

    assert len(result) == 0
    assert len(validator.failed_patchers) == 0


@pytest.mark.parametrize(
    "region", [
        Region(1, 2, 1, 2),
        Region(0, 0, len(df.index) // 2, len(df.columns) // 2),
        Region(0, 0, len(df.index), len(df.columns))
    ])
def test_df_without_styles(region: Region):
    styler = df.style

    validator = _create_validator(styler)
    result = validator.validate(region)

    assert len(result) == 0
    assert len(validator.failed_patchers) == 0


def test_does_not_fail_on_exception():
    def raise_exception(_: Series):
        raise Exception("I don't care")

    styler = df.style.apply(raise_exception, axis='index')

    validator = _create_validator(styler)
    result = validator.validate()

    assert len(validator.failed_patchers) == 1
    assert validator.failed_patchers[0].todo.index_in_org_styler == 0
    assert len(result) == 1
    assert result[0] == StyleFunctionValidationProblem(
        reason="EXCEPTION",
        message="I don't care",
        func_info=StyleFunctionInfo(
            index=0,
            qname="test_does_not_fail_on_exception.<locals>.raise_exception",
            resolved_name="raise_exception",
            axis="index",
            is_chunk_parent_requested=False,
            is_apply=True,
            is_pandas_builtin=False,
            is_supported=False,
        )
    )


@pytest.mark.parametrize("axis", ['index', 'columns'])
def test_detect_not_chunk_aware_styling_function(axis: str):
    def my_highlight_max(series: Series):
        is_max = series == series.max()
        return ['background-color: red' if cell else '' for cell in is_max]

    styler = df.style.apply(my_highlight_max, axis=axis)

    validator = _create_validator(styler)
    result = validator.validate()

    assert len(validator.failed_patchers) == 1
    assert validator.failed_patchers[0].todo.index_in_org_styler == 0
    assert len(result) == 1
    assert result[0] == StyleFunctionValidationProblem(
        reason="NOT_EQUAL",
        message="",
        func_info=StyleFunctionInfo(
            index=0,
            qname="test_detect_not_chunk_aware_styling_function.<locals>.my_highlight_max",
            resolved_name="my_highlight_max",
            axis=axis,
            is_chunk_parent_requested=False,
            is_apply=True,
            is_pandas_builtin=False,
            is_supported=False,
        )
    )


def test_detect_not_chunk_aware_and_throwing_styling_functions():
    def my_highlight_max(series: Series):
        is_max = series == series.max()
        return ['background-color: red' if cell else '' for cell in is_max]

    def raise_exception(_: Series):
        raise Exception("I don't care")

    styler = df.style.apply(my_highlight_max, axis=0).highlight_max().apply(raise_exception, axis=1)

    validator = _create_validator(styler)
    result = validator.validate()

    assert len(validator.failed_patchers) == 2
    assert validator.failed_patchers[0].todo.index_in_org_styler == 0
    assert validator.failed_patchers[1].todo.index_in_org_styler == 2
    assert len(result) == 2
    assert result[0] == StyleFunctionValidationProblem(
        reason="NOT_EQUAL",
        message="",
        func_info=StyleFunctionInfo(
            index=0,
            qname="test_detect_not_chunk_aware_and_throwing_styling_functions.<locals>.my_highlight_max",
            resolved_name="my_highlight_max",
            axis='0',
            is_chunk_parent_requested=False,
            is_apply=True,
            is_pandas_builtin=False,
            is_supported=False,
        )
    )
    assert result[1] == StyleFunctionValidationProblem(
        reason="EXCEPTION",
        message="I don't care",
        func_info=StyleFunctionInfo(
            index=2,
            qname="test_detect_not_chunk_aware_and_throwing_styling_functions.<locals>.raise_exception",
            resolved_name="raise_exception",
            axis='1',
            is_chunk_parent_requested=False,
            is_apply=True,
            is_pandas_builtin=False,
            is_supported=False,
        )
    )


@pytest.mark.parametrize("axis", ['index', 'columns'])
def test_no_problem_for_chunk_aware_styling_function_1(axis: str):
    def my_highlight_max(series: Series, chunk_parent=None):
        max = (series if chunk_parent is None else chunk_parent).max()
        return ['background-color: red' if cell == max else '' for cell in series]

    styler = df.style.apply(my_highlight_max, axis=axis)

    validator = _create_validator(styler)
    result = validator.validate()

    assert len(result) == 0
    assert len(validator.failed_patchers) == 0


@pytest.mark.parametrize("axis", ['index', 'columns'])
def test_no_problem_for_chunk_aware_styling_function_2(axis: str):
    def my_highlight_divisible(series: Series, div: int):
        return ['background-color: red' if v % div else '' for v in series]

    styler = df.style.apply(my_highlight_divisible, axis=axis, div=3)

    validator = _create_validator(styler)
    result = validator.validate()

    assert len(result) == 0
    assert len(validator.failed_patchers) == 0


def test_does_not_fail_if_rows_and_cols_are_hidden():
    style_hidden = df.style.background_gradient(axis=0)\
        .hide_index(subset=df.index[2:4])\
        .hide_columns(subset=df.columns[2:4])

    validator = _create_validator(style_hidden)
    result = validator.validate()

    assert len(result) == 0
    assert len(validator.failed_patchers) == 0


def test_does_not_fail_if_rows_and_cols_are_filtered_out():
    filter_frame = DataFrame(index=df.index[1:], columns=df.columns[1:])
    style_hidden = df.style.background_gradient(axis=0)

    validator = StyleFunctionsValidator(
        PatchedStylerContext(style_hidden, FilterCriteria.from_frame(filter_frame)),
    )
    result = validator.validate()

    assert len(result) == 0
    assert len(validator.failed_patchers) == 0


def test_validation_does_not_fail_on_unhashable_types():
    # precondition
    with pytest.raises(TypeError, match="unhashable type: 'list'"):
        pd.unique([[1]])

    df = DataFrame.from_dict({
        "A": [[1] * 4],
    })

    def raise_exception(s: Series):
        return ['background-color: red' if isinstance(x, list) else '' for x in s]

    styler = df.style.apply(raise_exception, axis=1)

    validator = _create_validator(styler)
    result = validator.validate()

    assert len(result) == 0
    assert len(validator.failed_patchers) == 0
