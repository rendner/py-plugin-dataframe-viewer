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
from typing import Union, List

from cms_rendner_sdfv.base.table_source import AbstractTableSource
from cms_rendner_sdfv.base.types import Region, TableSourceKind, ChunkDataRequest
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext
from cms_rendner_sdfv.pandas.styler.style_functions_validator import StyleFunctionsValidator
from cms_rendner_sdfv.pandas.styler.todo_patcher import TodoPatcher
from cms_rendner_sdfv.pandas.styler.types import ValidatedChunkData


class PatchedStyler(AbstractTableSource):
    def __init__(self, context: PatchedStylerContext, fingerprint: str):
        super().__init__(TableSourceKind.PATCHED_STYLER, context, fingerprint)
        self.__patchers_to_skip_in_validation: List[TodoPatcher] = []

    def validate_and_compute_chunk_data(self,
                                        region: Region,
                                        request: Union[None, ChunkDataRequest] = None,
                                        ) -> str:
        validator = StyleFunctionsValidator(
            self._context,
            self.__patchers_to_skip_in_validation,
        )
        problems = validator.validate(region)
        result = ValidatedChunkData(
            data=self._context.get_chunk_data_generator().generate(region=region, request=request),
            problems=problems if problems else None,
        )
        self.__patchers_to_skip_in_validation.extend(validator.failed_patchers)
        return self.serialize(result)
