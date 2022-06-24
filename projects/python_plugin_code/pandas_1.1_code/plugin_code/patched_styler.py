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
from plugin_code.html_props_validator import HTMLPropsValidator
from plugin_code.style_function_name_resolver import StyleFunctionNameResolver
from plugin_code.style_functions_validator import StyleFunctionsValidator, StyleFunctionValidationProblem, \
    ValidationStrategyType
from plugin_code.styler_todo import StylerTodo
from plugin_code.todos_patcher import TodosPatcher

# == copy after here ==
import numpy as np
from pandas import DataFrame
from pandas.io.formats.style import Styler
from dataclasses import dataclass, asdict
from typing import Optional, List


@dataclass(frozen=True)
class TableStructure:
    rows_count: int
    columns_count: int
    row_levels_count: int
    column_levels_count: int
    hide_row_header: bool
    hide_column_header: bool = False

    def __str__(self):
        return str(asdict(self))


@dataclass(frozen=True)
class StyleFunctionDetails:
    index: int
    qname: str
    resolved_name: str
    axis: str
    is_chunk_parent_requested: bool
    is_apply: bool
    is_pandas_builtin: bool
    is_supported: bool

    def __str__(self):
        return str(asdict(self))


class PatchedStyler:

    def __init__(self, styler: Styler):
        self.__styler: Styler = styler
        self.__visible_data: DataFrame = self.__get_visible_data(styler)
        self.__html_props_generator = HTMLPropsGenerator(self.__get_visible_data(styler), styler)
        self.__style_functions_validator = StyleFunctionsValidator(self.__get_visible_data(styler), styler)

    def create_html_props_validator(self) -> HTMLPropsValidator:
        return HTMLPropsValidator(self.__visible_data, self.__styler)

    def validate_style_functions(self,
                                 first_row: int,
                                 first_col: int,
                                 rows: int,
                                 cols: int,
                                 validation_strategy: Optional[ValidationStrategyType] = None,
                                 ) -> List[StyleFunctionValidationProblem]:
        if validation_strategy is not None:
            self.__style_functions_validator.set_validation_strategy_type(validation_strategy)
        return self.__style_functions_validator.validate(Region(first_row, first_col, rows, cols))

    def render_chunk(self,
                     first_row: int,
                     first_col: int,
                     rows: int,
                     cols: int,
                     exclude_row_header: bool = False,
                     exclude_column_header: bool = False,  # unused in this version
                     ) -> str:
        html_props = self.__html_props_generator.generate_props_for_chunk(
            region=Region(first_row, first_col, rows, cols),
            exclude_row_header=exclude_row_header,
        )
        # use template of original styler
        return self.__styler.template.render(
            **html_props,
            encoding="utf-8",
            sparse_columns=False,
            sparse_index=False,
        )

    def render_unpatched(self) -> str:
        # This method deliberately does not use the "html_props_generator" but the original
        # "Styler::render" method to create the html string.
        #
        # Method is only used in unit tests or to create test data for the plugin
        # therefore it is save to change potential configured values
        self.__styler.uuid = ''
        self.__styler.uuid_len = 0
        self.__styler.cell_ids = False
        # bug in pandas: we have to specify the "uuid" as arg
        return self.__styler.render(
            encoding="utf-8",
            uuid="",
            sparse_columns=False,
            sparse_index=False,
        )

    def get_table_structure(self) -> TableStructure:
        return TableStructure(
            rows_count=len(self.__visible_data.index),
            columns_count=len(self.__visible_data.columns),
            row_levels_count=self.__visible_data.index.nlevels,
            column_levels_count=self.__visible_data.columns.nlevels,
            hide_row_header=self.__styler.hidden_index,
        )

    def get_style_function_details(self) -> List[StyleFunctionDetails]:
        result = []

        for i, todo in enumerate(self.__styler._todo):
            t = StylerTodo.from_tuple(todo)
            result.append(StyleFunctionDetails(
                index=i,
                qname=StyleFunctionNameResolver.get_style_func_qname(t),
                resolved_name=StyleFunctionNameResolver.resolve_style_func_name(t),
                axis='' if t.is_applymap() else str(t.apply_args.axis),
                is_pandas_builtin=t.is_pandas_style_func(),
                is_supported=TodosPatcher.is_style_function_supported(t),
                is_apply=not t.is_applymap(),
                is_chunk_parent_requested=t.should_provide_chunk_parent(),
            ))

        return result

    @staticmethod
    def __get_visible_data(styler: Styler) -> DataFrame:
        if len(styler.hidden_columns) == 0:
            return styler.data
        else:
            visible_columns = np.delete(styler.columns.get_indexer_for(styler.columns), styler.hidden_columns)
            return styler.data.iloc[:, visible_columns]
