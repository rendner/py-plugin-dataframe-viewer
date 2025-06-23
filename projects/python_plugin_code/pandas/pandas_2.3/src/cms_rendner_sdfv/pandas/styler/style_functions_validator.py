#  Copyright 2021-2025 cms.rendner (Daniel Schmidt)
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
from typing import Optional

from cms_rendner_sdfv.base.types import Region
from cms_rendner_sdfv.pandas.styler.chunk_computer import Chunk, ChunkComputer
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext
from cms_rendner_sdfv.pandas.styler.style_function_name_resolver import StyleFunctionNameResolver
from cms_rendner_sdfv.pandas.styler.todo_patcher import TodoPatcher
from cms_rendner_sdfv.pandas.styler.types import StyleFunctionValidationProblem, StyleFunctionInfo


class StyleFunctionsValidator:
    def __init__(self, ctx: PatchedStylerContext, ignore_list: list[TodoPatcher] = None):
        self.__ctx: PatchedStylerContext = ctx
        self.__ignore_list = ignore_list or []
        self.failed_patchers: list[TodoPatcher] = []

    def validate(self, region: Optional[Region] = None) -> list[StyleFunctionValidationProblem]:
        patchers_to_validate = [
            p for p in self.__ctx.get_todo_patcher_list()
            if not p.todo.is_map() and p not in self.__ignore_list
        ]
        if not patchers_to_validate:
            return []

        region = self.__ctx.visible_frame.region.get_bounded_region(region)
        if region.is_empty():
            return []

        chunk_df = self.__ctx.visible_frame.to_frame(region)
        chunk_region = Region.with_frame_shape(chunk_df.shape)

        validation_result = []
        for patcher in patchers_to_validate:
            is_equal = False

            chunk_computer = self.__ctx.create_extractor_for_style_func_validation(chunk_df, patcher)

            try:
                chunk = chunk_computer.compute(chunk_region)

                if patcher.todo.apply_args.axis_is_index():
                    # styling is applied to each column
                    is_equal = self.__validate_horizontal_splitted(chunk_computer, chunk)
                elif patcher.todo.apply_args.axis_is_columns():
                    # styling is applied to each row
                    is_equal = self.__validate_vertical_splitted(chunk_computer, chunk)
                else:
                    # styling is applied to whole dataframe
                    is_equal = self.__validate_horizontal_splitted(chunk_computer, chunk)
                    if is_equal:
                        is_equal = self.__validate_vertical_splitted(chunk_computer, chunk)

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

    def __validate_horizontal_splitted(self, computer: ChunkComputer, chunk: Chunk) -> bool:
        region = chunk.region
        for sub_region in region.iterate_local_chunkwise(self.__half_or_one(region.rows), region.cols):
            if not self.__has_same_cell_styling(chunk, computer.compute(sub_region)):
                return False
        return True

    def __validate_vertical_splitted(self, computer: ChunkComputer, chunk: Chunk) -> bool:
        region = chunk.region
        for sub_region in region.iterate_local_chunkwise(region.rows, self.__half_or_one(region.cols)):
            if not self.__has_same_cell_styling(chunk, computer.compute(sub_region)):
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
    def __has_same_cell_styling(chunk: Chunk, sub_chunk: Chunk) -> bool:
        sub_region = sub_chunk.region
        for r in range(sub_region.rows):
            for c in range(sub_region.cols):
                expected = chunk.cell_value_at(sub_region.first_row + r, sub_region.first_col + c)
                actual = sub_chunk.cell_value_at(r, c)
                if expected != actual:
                    return False
        return True

    @staticmethod
    def __half_or_one(number: int):
        # expect: number > 0
        # upside-down floor division (=ceiling_division)
        return -(number // -2)
