#  Copyright 2021-2023 cms.rendner (Daniel Schmidt)
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
from abc import ABC, abstractmethod
from typing import List, Optional, Tuple

from cms_rendner_sdfv.base.types import Region
from cms_rendner_sdfv.pandas.shared.table_frame_validator import TableFrameValidator
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext
from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo
from cms_rendner_sdfv.pandas.styler.table_frame_generator import TableFrameGenerator
from cms_rendner_sdfv.pandas.styler.types import StyleFunctionValidationProblem, ValidationStrategyType


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

    def validate(self, region: Region = None) -> List[StyleFunctionValidationProblem]:
        if self.__apply_todos_count == 0:
            return []

        if region is None:
            region = self.__styler_context.get_region_of_frame()

        rows_per_chunk, cols_per_chunk = self.__validation_strategy.get_chunk_size(region.rows, region.cols)

        if self.__apply_todos_count == 1:
            return self.__validate_todos_separately(region, rows_per_chunk, cols_per_chunk)

        try:
            validator = TableFrameValidator(self.__styler_context)
            if validator.validate(rows_per_chunk, cols_per_chunk, region).is_equal:
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
                validator = TableFrameValidator(ctx, TableFrameGenerator(ctx, lambda x: x is todo))
                result = validator.validate(rows_per_chunk, cols_per_chunk, region)
                if not result.is_equal:
                    validation_result.append(StyleFunctionValidationProblem(i, "NOT_EQUAL"))
            except Exception as e:
                # repr(e) gives the exception and the message string
                # str(e) only the message string
                validation_result.append(StyleFunctionValidationProblem(i, "EXCEPTION", str(e)))

        return validation_result

    @staticmethod
    def __count_apply_todos(todos: List[StylerTodo]) -> int:
        return 0 if not todos else len([not t.is_applymap() for t in todos])

    @staticmethod
    def __create_validation_strategy(strategy_type: Optional[ValidationStrategyType] = None):
        if strategy_type is ValidationStrategyType.PRECISION:
            return _PrecisionValidationStrategy()
        else:
            return _FastValidationStrategy()
