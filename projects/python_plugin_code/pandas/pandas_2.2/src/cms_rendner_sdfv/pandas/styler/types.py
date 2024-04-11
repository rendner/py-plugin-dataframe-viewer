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
from dataclasses import dataclass
from typing import Optional

from cms_rendner_sdfv.base.types import TableFrame


@dataclass(frozen=True)
class StyleFunctionInfo:
    index: int
    qname: str
    resolved_name: str
    axis: str
    is_chunk_parent_requested: bool
    is_apply: bool
    is_pandas_builtin: bool
    is_supported: bool


@dataclass(frozen=True)
class StyleFunctionValidationProblem:
    reason: str
    message: str
    func_info: StyleFunctionInfo


@dataclass(frozen=True)
class ValidatedTableFrame:
    frame: Optional[TableFrame] = None
    problems: Optional[list[StyleFunctionValidationProblem]] = None
