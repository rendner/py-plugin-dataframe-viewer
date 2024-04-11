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
from typing import Optional

from cms_rendner_sdfv.base.types import Region
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext, StyledChunk
from cms_rendner_sdfv.pandas.styler.style_function_name_resolver import StyleFunctionNameResolver
from cms_rendner_sdfv.pandas.styler.todo_patcher import TodoPatcher
from cms_rendner_sdfv.pandas.styler.types import StyleFunctionValidationProblem, StyleFunctionInfo


class StyleFunctionsValidator:
    def __init__(self, ctx: PatchedStylerContext, ignore_list: list[TodoPatcher] = None):
        self.__ctx: PatchedStylerContext = ctx
        self.__ignore_list = ignore_list or []
        self.failed_patchers: list[TodoPatcher] = []

    def validate(self, region: Optional[Region] = None) -> list[StyleFunctionValidationProblem]:
        region = self.__ctx.visible_frame.region.get_bounded_region(region)

        if region.is_empty():
            return []

        patchers = [
            p for p in self.__ctx.get_todo_patcher_list()
            if not p.todo.is_map() and p not in self.__ignore_list
        ]

        validation_result = []
        for patcher in patchers:
            is_equal = False

            try:
                if patcher.todo.apply_args.axis_is_index():
                    # styling is applied to each column
                    is_equal = self.__validate_horizontal_splitted(region, patcher)
                elif patcher.todo.apply_args.axis_is_columns():
                    # styling is applied to each row
                    is_equal = self.__validate_vertical_splitted(region, patcher)
                else:
                    # styling is applied to whole dataframe
                    is_equal = self.__validate_horizontal_splitted(region, patcher)
                    if is_equal:
                        is_equal = self.__validate_vertical_splitted(region, patcher)

                if not is_equal:
                    self.failed_patchers.append(patcher)
                    validation_result.append(
                        StyleFunctionValidationProblem(
                            reason="NOT_EQUAL",
                            message="",
                            func_info=self.__create_style_func_info(patcher),
                        )
                    )

            except Exception as e:
                self.failed_patchers.append(patcher)
                # repr(e) gives the exception and the message string
                # str(e) only the message string
                validation_result.append(
                    StyleFunctionValidationProblem(
                        reason="EXCEPTION",
                        message=str(e),
                        func_info=self.__create_style_func_info(patcher),
                    )
                )

        return validation_result

    def __validate_horizontal_splitted(self, region: Region, patcher: TodoPatcher) -> bool:
        region_to_validate = region.get_bounded_region(dataclasses.replace(region, rows=2))
        styled_region = self.__ctx.compute_styled_chunk(region_to_validate, [patcher])
        for local_chunk in region_to_validate.iterate_local_chunkwise(1, region_to_validate.cols):
            styled_chunk = self.__ctx.compute_styled_chunk(
                local_chunk.translate(region_to_validate.first_row, region_to_validate.first_col),
                [patcher],
            )

            if not self.__has_same_cell_styling(styled_region, styled_chunk, local_chunk):
                return False
        return True

    def __validate_vertical_splitted(self, region: Region, patcher: TodoPatcher) -> bool:
        region_to_validate = region.get_bounded_region(dataclasses.replace(region, cols=2))
        styled_region = self.__ctx.compute_styled_chunk(region_to_validate, [patcher])
        for local_chunk in region_to_validate.iterate_local_chunkwise(region_to_validate.rows, 1):
            styled_chunk = self.__ctx.compute_styled_chunk(
                local_chunk.translate(region_to_validate.first_row, region_to_validate.first_col),
                [patcher],
            )

            if not self.__has_same_cell_styling(styled_region, styled_chunk, local_chunk):
                return False
        return True

    @staticmethod
    def __create_style_func_info(patcher: TodoPatcher) -> StyleFunctionInfo:
        todo = patcher.todo
        return StyleFunctionInfo(
            index=todo.index_in_org_styler,
            qname=StyleFunctionNameResolver.get_style_func_qname(todo),
            resolved_name=StyleFunctionNameResolver.resolve_style_func_name(todo),
            axis='' if todo.is_map() else str(todo.apply_args.axis),
            is_pandas_builtin=todo.is_pandas_style_func(),
            # True - if we have a patcher for a pandas style func
            is_supported=patcher.todo.is_pandas_style_func(),
            is_apply=not todo.is_map(),
            is_chunk_parent_requested=todo.should_provide_chunk_parent(),
        )

    @staticmethod
    def __has_same_cell_styling(styled_region: StyledChunk, styled_chunk: StyledChunk, local_chunk: Region) -> bool:
        for r in range(local_chunk.rows):
            for c in range(local_chunk.cols):
                expected = styled_region.cell_css_at(local_chunk.first_row + r, local_chunk.first_col + c)
                actual = styled_chunk.cell_css_at(r, c)
                if expected != actual:
                    return False
        return True
