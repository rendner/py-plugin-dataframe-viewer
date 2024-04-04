import numpy as np
import pytest
from pandas import DataFrame, MultiIndex, Series
from pandas.io.formats.style import Styler

from cms_rendner_sdfv.base.types import Region
from cms_rendner_sdfv.pandas.shared.types import FilterCriteria
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext
from cms_rendner_sdfv.pandas.styler.style_functions_validator import StyleFunctionValidationProblem, \
    StyleFunctionsValidator
from cms_rendner_sdfv.pandas.styler.types import ValidationStrategyType

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


def _create_validator(style: Styler, validation_strategy_type: ValidationStrategyType) -> StyleFunctionsValidator:
    return StyleFunctionsValidator(PatchedStylerContext(style), validation_strategy_type)


@pytest.mark.parametrize("validation_strategy_type", [ValidationStrategyType.FAST, ValidationStrategyType.PRECISION])
@pytest.mark.parametrize(
    "region", [
        Region(1, 2, 1, 2),
        Region(0, 0, len(mi_df.index) // 2, len(mi_df.columns) // 2),
        Region(0, 0, len(mi_df.index), len(mi_df.columns)),
        Region(3, 3, 100, 100)
    ])
def test_background_gradient(validation_strategy_type: ValidationStrategyType, region: Region):
    styler = mi_df.style.background_gradient()

    validator = _create_validator(styler, validation_strategy_type)
    result = validator.validate(region)

    if validation_strategy_type is ValidationStrategyType.FAST:
        assert len(result) == 0

        result = validator.validate(region)
        assert len(result) == 0
    else:
        assert len(result) == 0


@pytest.mark.parametrize("validation_strategy_type", [ValidationStrategyType.FAST, ValidationStrategyType.PRECISION])
@pytest.mark.parametrize(
    "region", [
        Region(1, 2, 1, 2),
        Region(0, 0, 0, 0),
        Region(0, 0, 6, 6)
    ])
def test_empty_df_with_styles(validation_strategy_type: ValidationStrategyType, region: Region):
    styler = DataFrame().style.highlight_max().highlight_min()

    validator = _create_validator(styler, validation_strategy_type)
    result = validator.validate(region)

    if validation_strategy_type is ValidationStrategyType.FAST:
        assert len(result) == 0

        result = validator.validate(region)
        assert len(result) == 0
    else:
        assert len(result) == 0


@pytest.mark.parametrize("validation_strategy_type", [ValidationStrategyType.FAST, ValidationStrategyType.PRECISION])
@pytest.mark.parametrize(
    "region", [
        Region(1, 2, 1, 2),
        Region(0, 0, len(df.index) // 2, len(df.columns) // 2),
        Region(0, 0, len(df.index), len(df.columns))
    ])
def test_df_without_styles(validation_strategy_type: ValidationStrategyType, region: Region):
    styler = df.style

    validator = _create_validator(styler, validation_strategy_type)
    result = validator.validate(region)

    if validation_strategy_type is ValidationStrategyType.FAST:
        assert len(result) == 0

        result = validator.validate(region)
        assert len(result) == 0
    else:
        assert len(result) == 0


@pytest.mark.parametrize("validation_strategy_type", [ValidationStrategyType.FAST, ValidationStrategyType.PRECISION])
def test_does_not_fail_on_exception(validation_strategy_type: ValidationStrategyType):
    def raise_exception(_: Series):
        raise Exception("I don't care")

    styler = df.style.apply(raise_exception, axis='index')

    validator = _create_validator(styler, validation_strategy_type)
    result = validator.validate()

    if validation_strategy_type is ValidationStrategyType.FAST:
        assert len(result) == 1
        assert result[0] == StyleFunctionValidationProblem(index=0, reason="EXCEPTION", message="I don't care")

        result = validator.validate()
        assert len(result) == 1
        assert result[0] == StyleFunctionValidationProblem(index=0, reason="EXCEPTION", message="I don't care")
    else:
        assert len(result) == 1
        assert result[0] == StyleFunctionValidationProblem(index=0, reason="EXCEPTION", message="I don't care")


@pytest.mark.parametrize("axis", ['index', 'columns'])
@pytest.mark.parametrize("validation_strategy_type", [ValidationStrategyType.FAST, ValidationStrategyType.PRECISION])
def test_detect_invalid_styling_function(axis: str, validation_strategy_type: ValidationStrategyType):
    def my_highlight_max(series: Series):
        is_max = series == series.max()
        return ['background-color: red' if cell else '' for cell in is_max]

    styler = df.style.apply(my_highlight_max, axis=axis)

    validator = _create_validator(styler, validation_strategy_type)
    result = validator.validate()

    if validation_strategy_type is ValidationStrategyType.FAST:
        if axis == 'index':
            assert len(result) == 0

            result = validator.validate()
            assert len(result) == 1
            assert result[0] == StyleFunctionValidationProblem(index=0, reason="NOT_EQUAL")
        else:
            assert len(result) == 1
            assert result[0] == StyleFunctionValidationProblem(index=0, reason="NOT_EQUAL")

            result = validator.validate()
            assert len(result) == 0
    else:
        assert len(result) == 1
        assert result[0] == StyleFunctionValidationProblem(index=0, reason="NOT_EQUAL")


@pytest.mark.parametrize("validation_strategy_type", [ValidationStrategyType.FAST, ValidationStrategyType.PRECISION])
def test_detect_invalid_styling_functions(validation_strategy_type: ValidationStrategyType):
    def my_highlight_max(series: Series):
        is_max = series == series.max()
        return ['background-color: red' if cell else '' for cell in is_max]

    def raise_exception(_: Series):
        raise Exception("I don't care")

    styler = df.style.apply(my_highlight_max, axis=0).highlight_max().apply(raise_exception, axis=1)

    validator = _create_validator(styler, validation_strategy_type)
    result = validator.validate()

    if validation_strategy_type is ValidationStrategyType.FAST:
        assert len(result) == 1
        assert result[0] == StyleFunctionValidationProblem(index=2, reason="EXCEPTION", message="I don't care")

        result = validator.validate()
        assert len(result) == 2
        assert result[0] == StyleFunctionValidationProblem(index=0, reason="NOT_EQUAL")
        assert result[1] == StyleFunctionValidationProblem(index=2, reason="EXCEPTION", message="I don't care")
    else:
        assert len(result) == 2
        assert result[0] == StyleFunctionValidationProblem(index=0, reason="NOT_EQUAL")
        assert result[1] == StyleFunctionValidationProblem(index=2, reason="EXCEPTION", message="I don't care")


def test_does_not_fail_if_rows_and_cols_are_hidden():
    style_hidden = df.style.background_gradient(axis=0)\
        .hide_columns(subset=df.columns[2:4])

    validator = _create_validator(style_hidden, ValidationStrategyType.PRECISION)
    result = validator.validate()
    assert len(result) == 0


def test_does_not_fail_if_rows_and_cols_are_filtered_out():
    filter_frame = DataFrame(index=df.index[1:], columns=df.columns[1:])
    style_hidden = df.style.background_gradient(axis=0)

    validator = StyleFunctionsValidator(
        PatchedStylerContext(style_hidden, FilterCriteria.from_frame(filter_frame)),
        ValidationStrategyType.PRECISION,
    )
    result = validator.validate()
    assert len(result) == 0
