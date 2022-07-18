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
from plugin_code.custom_json_encoder import CustomJSONEncoder
from plugin_code.html_props_generator import HTMLPropsGenerator
from plugin_code.html_props_table_builder import HTMLPropsTableBuilder, HTMLPropsTable
from plugin_code.html_props_validator import HTMLPropsValidator
from plugin_code.patched_styler_context import PatchedStylerContext, Region
from plugin_code.style_function_name_resolver import StyleFunctionNameResolver
from plugin_code.style_functions_validator import StyleFunctionsValidator, StyleFunctionValidationProblem, \
    ValidationStrategyType
from plugin_code.todos_patcher import TodosPatcher

# == copy after here ==
import json
from pandas.io.formats.style import Styler
from dataclasses import dataclass
from typing import Optional, List, Any


@dataclass(frozen=True)
class TableStructure:
    rows_count: int
    columns_count: int
    row_levels_count: int
    column_levels_count: int
    hide_row_header: bool
    hide_column_header: bool = False


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


class PatchedStyler:

    def __init__(self, styler: Styler):
        self.__context = PatchedStylerContext(styler)
        self.__html_props_generator = HTMLPropsGenerator(self.__context)
        self.__style_functions_validator = StyleFunctionsValidator(self.__context)

    def get_context(self) -> PatchedStylerContext:
        return self.__context

    @staticmethod
    def to_json(data: Any) -> str:
        return json.dumps(data, cls=CustomJSONEncoder)

    def create_html_props_validator(self) -> HTMLPropsValidator:
        return HTMLPropsValidator(self.__context)

    def create_html_props_generator(self) -> HTMLPropsGenerator:
        return HTMLPropsGenerator(self.__context)

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

    def compute_chunk_html_props_table(self,
                                       first_row: int,
                                       first_col: int,
                                       rows: int,
                                       cols: int,
                                       exclude_row_header: bool = False,
                                       exclude_col_header: bool = False,  # unused in this version
                                       ) -> HTMLPropsTable:
        html_props = self.__html_props_generator.compute_chunk_props(
            region=Region(first_row, first_col, rows, cols),
            exclude_row_header=exclude_row_header,
        )

        props_table_builder = HTMLPropsTableBuilder()
        props_table_builder.append_props(
            html_props=html_props,
            target_row_offset=0,
            is_part_of_first_rows_in_chunk=True,
            is_part_of_first_cols_in_chunk=True,
        )
        return props_table_builder.build_table()

    def compute_unpatched_html_props_table(self) -> HTMLPropsTable:
        props_table_builder = HTMLPropsTableBuilder()
        props_table_builder.append_props(
            html_props=self.__html_props_generator.compute_unpatched_props(),
            target_row_offset=0,
            is_part_of_first_rows_in_chunk=True,
            is_part_of_first_cols_in_chunk=True,
        )
        return props_table_builder.build_table()

    def render_chunk(self,
                     first_row: int,
                     first_col: int,
                     rows: int,
                     cols: int,
                     exclude_row_header: bool = False,
                     exclude_column_header: bool = False,  # unused in this version
                     ) -> str:
        html_props = self.__html_props_generator.compute_chunk_props(
            region=Region(first_row, first_col, rows, cols),
            exclude_row_header=exclude_row_header,
        )
        styler = self.__context.get_styler()
        return styler.template.render(
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
        styler = self.__context.get_styler()
        styler.uuid = ''
        styler.uuid_len = 0
        styler.cell_ids = False
        # bug in pandas 1.x: we have to specify the "uuid" as arg
        return styler.render(
            encoding="utf-8",
            uuid="",
            sparse_columns=False,
            sparse_index=False,
        )

    def get_table_structure(self) -> TableStructure:
        visible_data = self.__context.get_visible_data()
        styler = self.__context.get_styler()
        rows_count = len(visible_data.index)
        columns_count = len(visible_data.columns)
        if rows_count == 0 or columns_count == 0:
            rows_count = columns_count = 0
        return TableStructure(
            rows_count=rows_count,
            columns_count=columns_count,
            row_levels_count=visible_data.index.nlevels,
            column_levels_count=visible_data.columns.nlevels,
            hide_row_header=styler.hidden_index,
        )

    def get_style_function_details(self) -> List[StyleFunctionDetails]:
        result = []

        for i, todo in enumerate(self.__context.get_styler_todos()):
            result.append(StyleFunctionDetails(
                index=i,
                qname=StyleFunctionNameResolver.get_style_func_qname(todo),
                resolved_name=StyleFunctionNameResolver.resolve_style_func_name(todo),
                axis='' if todo.is_applymap() else str(todo.apply_args.axis),
                is_pandas_builtin=todo.is_pandas_style_func(),
                is_supported=TodosPatcher.is_style_function_supported(todo),
                is_apply=not todo.is_applymap(),
                is_chunk_parent_requested=todo.should_provide_chunk_parent(),
            ))

        return result
