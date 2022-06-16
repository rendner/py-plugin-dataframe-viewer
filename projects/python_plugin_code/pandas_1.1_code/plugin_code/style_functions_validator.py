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
from plugin_code.html_props_generator import HTMLPropsGenerator, Region
from plugin_code.html_props_validator import HTMLPropsValidationResult, AbstractHTMLPropsValidator

# == copy after here ==
from dataclasses import dataclass, asdict
from enum import Enum
from typing import List, Callable, Tuple
from abc import ABC, abstractmethod
from pandas import DataFrame
from pandas.io.formats.style import Styler


class _StyleFunctionsHTMLPropsValidator(AbstractHTMLPropsValidator):
    def __init__(self, visible_data: DataFrame):
        super().__init__(HTMLPropsGenerator(visible_data, visible_data.style))
        self._visible_data: DataFrame = visible_data
        self._region_to_validate: dict = {}
        self._data_bounds: Region = Region(0, 0, len(visible_data.index), len(visible_data.columns))

    def validate(self,
                 todos: List[Tuple[Callable, tuple, dict]],
                 region: Region,
                 rows_per_chunk: int,
                 cols_per_chunk: int,
                 ) -> HTMLPropsValidationResult:
        sanitized_region = self._compute_sanitized_region(region)

        if sanitized_region.is_empty():
            # special case region has no columns/rows and therefore no important html props
            return HTMLPropsValidationResult('', '', True)

        style = self._visible_data.style
        style._todo = todos
        self._html_props_generator = HTMLPropsGenerator(self._visible_data, style)

        combined_html_props = self._create_combined_html_props(
            region=sanitized_region,
            rows_per_chunk=rows_per_chunk,
            cols_per_chunk=cols_per_chunk,
        )

        expected_html_props = self._html_props_generator.generate_props_for_chunk(
            region=sanitized_region,
            exclude_row_header=False,
        )

        return self._validate_html_props(combined_html_props, expected_html_props)

    def _compute_sanitized_region(self, region: Region) -> Region:
        assert region.is_valid()
        if region.is_empty():
            return region
        elif self._data_bounds.is_empty():
            return self._data_bounds

        first_row = min(region.first_row, self._data_bounds.rows - 1)
        first_col = min(region.first_col, self._data_bounds.cols - 1)
        rows = min(region.rows, self._data_bounds.rows - first_row)
        cols = min(region.cols, self._data_bounds.cols - first_col)
        return Region(first_row, first_col, rows, cols)

    def _create_combined_html_props(self, region: Region, rows_per_chunk: int, cols_per_chunk: int):
        combined_props: dict = {}

        rows_processed = 0
        while rows_processed < region.rows:
            rows = min(rows_per_chunk, region.rows - rows_processed)
            cols_in_row_processed = 0
            while cols_in_row_processed < region.cols:
                cols = min(cols_per_chunk, region.cols - cols_in_row_processed)
                chunk_html_props = self._html_props_generator.generate_props_for_chunk(
                    region=Region(
                        region.first_row + rows_processed,
                        region.first_col + cols_in_row_processed,
                        rows,
                        cols,
                    ),
                    exclude_row_header=False,
                )

                self._append_chunk_html_props(
                    chunk_props=chunk_html_props,
                    target=combined_props,
                    target_row_offset=rows_processed,
                )

                cols_in_row_processed += cols
            rows_processed += rows

        return combined_props


@dataclass(frozen=True)
class StyleFunctionValidationInfo:
    index: int
    reason: str
    message: str = ""

    def __str__(self):
        return str(asdict(self))


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

    def validate(self, region: Region) -> List[StyleFunctionValidationInfo]:

        if len(self.__styler._todo) == 0:
            return []

        rows_per_chunk, cols_per_chunk = self.__validation_strategy.get_chunk_size(region.rows, region.cols)
        html_props_validator = _StyleFunctionsHTMLPropsValidator(self.__visible_data)

        if len(self.__styler._todo) == 1:
            return self.__validate_single_todos(html_props_validator, region, rows_per_chunk, cols_per_chunk)

        try:
            if html_props_validator.validate(self.__styler._todo, region, rows_per_chunk, cols_per_chunk).is_equal:
                return []
        except Exception:
            pass

        return self.__validate_single_todos(html_props_validator, region, rows_per_chunk, cols_per_chunk)

    def __validate_single_todos(self,
                                html_props_validator: _StyleFunctionsHTMLPropsValidator,
                                region: Region,
                                rows_per_chunk: int,
                                cols_per_chunk: int) -> List[StyleFunctionValidationInfo]:
        validation_result = []

        for i, todo in enumerate(self.__styler._todo):
            try:
                result = html_props_validator.validate([todo], region, rows_per_chunk, cols_per_chunk)
                if not result.is_equal:
                    validation_result.append(StyleFunctionValidationInfo(i, "NOT_EQUAL"))
            except Exception as e:
                # repr(e) gives the exception and the message string
                # str(e) only the message string
                validation_result.append(StyleFunctionValidationInfo(i, "EXCEPTION", str(e)))

        return validation_result
