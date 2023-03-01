#  Copyright 2023 cms.rendner (Daniel Schmidt)
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
from plugin_code.html_props_generator import HTMLPropsGenerator
from plugin_code.html_props_validator import HTMLPropsValidator
from plugin_code.patched_styler_context import PatchedStylerContext, Region
from plugin_code.styler_todo import StylerTodo

# == copy after here ==
from dataclasses import dataclass
from enum import Enum
from typing import List, Tuple, Optional
from abc import ABC, abstractmethod


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
    def __init__(self, styler_context: PatchedStylerContext, strategy_type: Optional[ValidationStrategyType] = None):
        self.__styler_context: PatchedStylerContext = styler_context
        self.__apply_todos_count: int = self.__count_apply_todos(styler_context.get_styler_todos())
        self.__validation_strategy: _AbstractValidationStrategy = self.__create_validation_strategy(strategy_type)

    def validate(self, region: Region) -> List[StyleFunctionValidationProblem]:

        if self.__apply_todos_count == 0:
            return []

        rows_per_chunk, cols_per_chunk = self.__validation_strategy.get_chunk_size(region.rows, region.cols)

        if self.__apply_todos_count == 1:
            return self.__validate_todos_separately(region, rows_per_chunk, cols_per_chunk)

        try:
            validator = HTMLPropsValidator(self.__styler_context)
            if validator.validate_chunk_region(region, rows_per_chunk, cols_per_chunk).is_equal:
                return []
        except Exception:
            pass

        return self.__validate_todos_separately(region, rows_per_chunk, cols_per_chunk)

    def __validate_todos_separately(self,
                                    region: Region,
                                    rows_per_chunk: int,
                                    cols_per_chunk: int,
                                    ) -> List[StyleFunctionValidationProblem]:
        validation_result = []

        ctx = self.__styler_context
        for i, todo in enumerate(ctx.get_styler_todos()):
            try:
                if todo.is_applymap():
                    continue
                validator = HTMLPropsValidator(ctx, HTMLPropsGenerator(ctx, lambda x: x is todo))
                result = validator.validate_chunk_region(region, rows_per_chunk, cols_per_chunk)
                if not result.is_equal:
                    validation_result.append(StyleFunctionValidationProblem(i, "NOT_EQUAL"))
            except Exception as e:
                # repr(e) gives the exception and the message string
                # str(e) only the message string
                validation_result.append(StyleFunctionValidationProblem(i, "EXCEPTION", str(e)))

        return validation_result

    @staticmethod
    def __count_apply_todos(todos: List[StylerTodo]) -> int:
        if len(todos) == 0:
            return 0
        return len([not t.is_applymap() for t in todos])

    @staticmethod
    def __create_validation_strategy(strategy_type: Optional[ValidationStrategyType] = None):
        if strategy_type is ValidationStrategyType.PRECISION:
            return _PrecisionValidationStrategy()
        else:
            return _FastValidationStrategy()
