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
from plugin_code.html_props_generator import Region
from plugin_code.html_props_validator import HTMLPropsValidator
from plugin_code.styler_todo import StylerTodo

# == copy after here ==
from dataclasses import dataclass, asdict
from enum import Enum
from typing import List, Tuple
from abc import ABC, abstractmethod
from pandas import DataFrame
from pandas.io.formats.style import Styler


@dataclass(frozen=True)
class StyleFunctionValidationProblem:
    index: int
    reason: str
    message: str = ""


class ValidationStrategyType(Enum):
    FAST = "fast"
    PRECISION = "precision"


class _AbstractValidationStrategy(ABC):
    def __init__(self, strategy_type: ValidationStrategyType):
        self._strategy_type: ValidationStrategyType = strategy_type

    @property
    def strategy_type(self):
        return self._strategy_type

    @abstractmethod
    def get_chunk_size(self, rows_in_region: int, columns_in_region: int) -> Tuple[int, int]:
        pass

    @staticmethod
    def _ceiling_division(n, d):
        return -(n // -d)


class _PrecisionValidationStrategy(_AbstractValidationStrategy):
    def __init__(self):
        super().__init__(ValidationStrategyType.PRECISION)

    def get_chunk_size(self, rows_in_region: int, columns_in_region: int) -> Tuple[int, int]:
        cols_per_chunk = max(1, self._ceiling_division(rows_in_region, 2))
        rows_per_chunk = max(1, self._ceiling_division(columns_in_region, 2))
        return rows_per_chunk, cols_per_chunk


class _FastValidationStrategy(_AbstractValidationStrategy):
    def __init__(self):
        super().__init__(ValidationStrategyType.FAST)
        self.__split_vertical = True

    def get_chunk_size(self, rows_in_region: int, columns_in_region: int) -> Tuple[int, int]:
        rows_per_chunk = rows_in_region
        cols_per_chunk = columns_in_region

        if self.__split_vertical:
            cols_per_chunk = max(1, self._ceiling_division(cols_per_chunk, 2))
        else:
            rows_per_chunk = max(1, self._ceiling_division(rows_per_chunk, 2))

        self.__split_vertical = not self.__split_vertical
        return rows_per_chunk, cols_per_chunk


class StyleFunctionsValidator:
    def __init__(self, visible_data: DataFrame, styler: Styler):
        self.__visible_data: DataFrame = visible_data
        self.__styler: Styler = styler
        self.__validation_strategy: _AbstractValidationStrategy = _FastValidationStrategy()

    def set_validation_strategy_type(self, strategy_type: ValidationStrategyType):
        if self.__validation_strategy.strategy_type is strategy_type:
            return
        if strategy_type is ValidationStrategyType.FAST:
            self.__validation_strategy = _FastValidationStrategy()
        else:
            self.__validation_strategy = _PrecisionValidationStrategy()

    def validate(self, region: Region) -> List[StyleFunctionValidationProblem]:

        if not self.__has_apply_calls():
            return []

        rows_per_chunk, cols_per_chunk = self.__validation_strategy.get_chunk_size(region.rows, region.cols)

        if len(self.__styler._todo) == 1:
            return self.__validate_single_todos(region, rows_per_chunk, cols_per_chunk)

        try:
            validator = HTMLPropsValidator(self.__visible_data, self.__styler)
            if validator.validate_region(region, rows_per_chunk, cols_per_chunk).is_equal:
                return []
        except Exception:
            pass

        return self.__validate_single_todos(region, rows_per_chunk, cols_per_chunk)

    def __validate_single_todos(self,
                                region: Region,
                                rows_per_chunk: int,
                                cols_per_chunk: int,
                                ) -> List[StyleFunctionValidationProblem]:
        validation_result = []

        org_todo = self.__styler._todo
        try:
            for i, todo in enumerate(org_todo):
                try:
                    if StylerTodo.is_applymap_tuple(todo):
                        continue
                    self.__styler._todo = [todo]
                    validator = HTMLPropsValidator(self.__visible_data, self.__styler)
                    result = validator.validate_region(region, rows_per_chunk, cols_per_chunk)
                    if not result.is_equal:
                        validation_result.append(StyleFunctionValidationProblem(i, "NOT_EQUAL"))
                except Exception as e:
                    # repr(e) gives the exception and the message string
                    # str(e) only the message string
                    validation_result.append(StyleFunctionValidationProblem(i, "EXCEPTION", str(e)))
        finally:
            self.__styler._todo = org_todo

        return validation_result

    def __has_apply_calls(self) -> bool:
        todos = self.__styler._todo
        if len(todos) == 0:
            return False
        return any(not StylerTodo.is_applymap_tuple(t) for t in todos)
