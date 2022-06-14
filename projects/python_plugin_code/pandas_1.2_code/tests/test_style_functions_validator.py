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
import numpy as np
import pytest
from pandas import MultiIndex, DataFrame, Series
from pandas.io.formats.style import Styler

from plugin_code.html_props_generator import Region
from plugin_code.patched_styler import PatchedStyler
from plugin_code.style_functions_validator import StyleFunctionValidationProblem, StyleFunctionsValidator, \
    ValidationStrategyType

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
    validator = PatchedStyler(style)._PatchedStyler__style_functions_validator
    validator.set_validation_strategy_type(validation_strategy_type)
    return validator


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

    region = Region(0, 0, len(df.index), len(df.columns))
    validator = _create_validator(styler, validation_strategy_type)
    result = validator.validate(region)

    if validation_strategy_type is ValidationStrategyType.FAST:
        assert len(result) == 1
        assert result[0] == StyleFunctionValidationProblem(index=0, reason="EXCEPTION", message="I don't care")

        result = validator.validate(region)
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

    region = Region(0, 0, len(df.index), len(df.columns))
    validator = _create_validator(styler, validation_strategy_type)
    result = validator.validate(region)

    if validation_strategy_type is ValidationStrategyType.FAST:
        if axis == 'index':
            assert len(result) == 0

            result = validator.validate(region)
            assert len(result) == 1
            assert result[0] == StyleFunctionValidationProblem(index=0, reason="NOT_EQUAL")
        else:
            assert len(result) == 1
            assert result[0] == StyleFunctionValidationProblem(index=0, reason="NOT_EQUAL")

            result = validator.validate(region)
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

    region = Region(0, 0, len(df.index), len(df.columns))
    validator = _create_validator(styler, validation_strategy_type)
    result = validator.validate(region)

    if validation_strategy_type is ValidationStrategyType.FAST:
        assert len(result) == 1
        assert result[0] == StyleFunctionValidationProblem(index=2, reason="EXCEPTION", message="I don't care")

        result = validator.validate(region)
        assert len(result) == 2
        assert result[0] == StyleFunctionValidationProblem(index=0, reason="NOT_EQUAL")
        assert result[1] == StyleFunctionValidationProblem(index=2, reason="EXCEPTION", message="I don't care")
    else:
        assert len(result) == 2
        assert result[0] == StyleFunctionValidationProblem(index=0, reason="NOT_EQUAL")
        assert result[1] == StyleFunctionValidationProblem(index=2, reason="EXCEPTION", message="I don't care")
