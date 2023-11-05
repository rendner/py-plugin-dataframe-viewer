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
from typing import Optional, Union

from cms_rendner_sdfv.base.table_source import AbstractTableSource
from cms_rendner_sdfv.base.types import Region, TableSourceKind
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext
from cms_rendner_sdfv.pandas.styler.style_function_name_resolver import StyleFunctionNameResolver
from cms_rendner_sdfv.pandas.styler.style_functions_validator import StyleFunctionValidationProblem, \
    StyleFunctionsValidator, ValidationStrategyType
from cms_rendner_sdfv.pandas.styler.todos_patcher import TodosPatcher
from cms_rendner_sdfv.pandas.styler.types import StyleFunctionInfo


class PatchedStyler(AbstractTableSource):
    def __init__(self, context: PatchedStylerContext, fingerprint: str):
        super().__init__(TableSourceKind.PATCHED_STYLER, context, fingerprint)

    def validate_style_functions(self,
                                 first_row: int,
                                 first_col: int,
                                 rows: int,
                                 cols: int,
                                 strategy: Union[ValidationStrategyType, str, None] = None,
                                 ) -> list[StyleFunctionValidationProblem]:
        validation_strategy = ValidationStrategyType[strategy] if isinstance(strategy, str) else strategy
        return StyleFunctionsValidator(self._context, validation_strategy)\
            .validate(Region(first_row, first_col, rows, cols))

    def set_sort_criteria(self,
                          by_column_index: Optional[list[int]] = None,
                          ascending: Optional[list[bool]] = None,
                          ):
        self._context.set_sort_criteria(by_column_index, ascending)

    def get_style_function_info(self) -> list[StyleFunctionInfo]:
        result = []

        for i, todo in enumerate(self._context.get_styler_todos()):
            result.append(StyleFunctionInfo(
                index=i,
                qname=StyleFunctionNameResolver.get_style_func_qname(todo),
                resolved_name=StyleFunctionNameResolver.resolve_style_func_name(todo),
                axis='' if todo.is_map() else str(todo.apply_args.axis),
                is_pandas_builtin=todo.is_pandas_style_func(),
                is_supported=TodosPatcher.is_style_function_supported(todo),
                is_apply=not todo.is_map(),
                is_chunk_parent_requested=todo.should_provide_chunk_parent(),
            ))

        return result
