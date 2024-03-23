#  Copyright 2021-2024 cms.rendner (Daniel Schmidt)
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
import dataclasses
from abc import ABC, abstractmethod
from typing import Optional, Union, List, Tuple

from cms_rendner_sdfv.base.types import Region
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext, StyledChunk
from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo
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
        rows_per_chunk = max(1, self._ceiling_division(rows_in_region, 2))
        cols_per_chunk = max(1, self._ceiling_division(columns_in_region, 2))
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
    def __init__(self, ctx: PatchedStylerContext, strategy_type: Optional[ValidationStrategyType] = None):
        self.__ctx: PatchedStylerContext = ctx
        self.__apply_todos_count: int = self.__count_apply_todos(ctx.get_styler_todos())
        self.__validation_strategy: _AbstractValidationStrategy = self.__create_validation_strategy(strategy_type)

    def validate(self, region: Region = None) -> List[StyleFunctionValidationProblem]:
        if self.__apply_todos_count == 0:
            return []

        region = self.__ctx.visible_frame.region.get_bounded_region(region)

        if region.is_empty():
            return []

        rows_per_chunk, cols_per_chunk = self.__validation_strategy.get_chunk_size(region.rows, region.cols)

        if self.__apply_todos_count > 1:
            apply_todos = list(filter(lambda x: not x.is_applymap(), self.__ctx.get_styler_todos()))
            validation_problem = self.__validate_region(
                region=region,
                rows_per_chunk=rows_per_chunk,
                cols_per_chunk=cols_per_chunk,
                apply_todos=apply_todos,
            )

            if validation_problem is None:
                return []

        return self.__validate_todos_separately(region, rows_per_chunk, cols_per_chunk)

    def __validate_region(self,
                          region: Region,
                          rows_per_chunk: int,
                          cols_per_chunk: int,
                          apply_todos: List[StylerTodo],
                          ) -> Union[None, StyleFunctionValidationProblem]:
        try:
            styled_region = self.__ctx.compute_styled_chunk(region, apply_todos)

            for local_chunk in region.iterate_local_chunkwise(rows_per_chunk, cols_per_chunk):

                styled_chunk = self.__ctx.compute_styled_chunk(
                    local_chunk.translate(region.first_row, region.first_col),
                    apply_todos,
                )

                if not self.__has_same_cell_styling(styled_region, styled_chunk, local_chunk):
                    return StyleFunctionValidationProblem(-1, "NOT_EQUAL")
        except Exception as e:
            # repr(e) gives the exception and the message string
            # str(e) only the message string
            return StyleFunctionValidationProblem(-1, "EXCEPTION", str(e))

        return None

    def __validate_todos_separately(self,
                                    region: Region,
                                    rows_per_chunk: int,
                                    cols_per_chunk: int,
                                    ) -> List[StyleFunctionValidationProblem]:
        validation_result = []

        for i, todo in enumerate(self.__ctx.get_styler_todos()):
            if todo.is_applymap():
                continue

            validation_problem = self.__validate_region(
                region=region,
                rows_per_chunk=rows_per_chunk,
                cols_per_chunk=cols_per_chunk,
                apply_todos=[todo],
            )

            if validation_problem is not None:
                validation_result.append(dataclasses.replace(validation_problem, index=i))

        return validation_result

    @staticmethod
    def __has_same_cell_styling(styled_region: StyledChunk, styled_chunk: StyledChunk, local_chunk: Region) -> bool:
        for r in range(local_chunk.rows):
            for c in range(local_chunk.cols):
                expected = styled_region.cell_css_at(local_chunk.first_row + r, local_chunk.first_col + c)
                actual = styled_chunk.cell_css_at(r, c)
                if expected != actual:
                    return False
        return True

    @staticmethod
    def __count_apply_todos(todos: List[StylerTodo]) -> int:
        return 0 if not todos else len([not t.is_applymap() for t in todos])

    @staticmethod
    def __create_validation_strategy(strategy_type: Optional[ValidationStrategyType] = None):
        if strategy_type is ValidationStrategyType.PRECISION:
            return _PrecisionValidationStrategy()
        else:
            return _FastValidationStrategy()
