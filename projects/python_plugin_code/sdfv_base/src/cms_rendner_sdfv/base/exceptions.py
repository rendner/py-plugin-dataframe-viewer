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
from enum import Enum
from typing import Union


class TableSourceErrorKind(Enum):
    UNKNOWN_ERROR = 0
    EVAL_FAILED = 1
    UNSUPPORTED_TABLE_SOURCE_TYPE = 2
    UNSUPPORTED_DATA_SOURCE_TYPE = 3
    UNSUPPORTED_FILTER_FRAME_TYPE = 4


class TableSourceException(Exception):
    def __init__(self, error_kind: TableSourceErrorKind, info: str = '', cause: Union[None, Exception] = None):
        super().__init__(info)
        self.error_kind = error_kind
        if cause:
            self.__cause__ = cause

    @classmethod
    def from_exception(cls, ex: Exception):
        if not isinstance(ex, TableSourceException):
            return cls(
                error_kind=TableSourceErrorKind.UNKNOWN_ERROR,
                cause=ex,
            )
        return ex

    def as_dict(self):
        return {
            "info": str(self),
            "error_kind": self.error_kind.name,
            "cause": repr(self.__cause__),
        }