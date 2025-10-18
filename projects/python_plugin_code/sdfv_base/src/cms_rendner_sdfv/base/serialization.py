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
import json
import functools
from dataclasses import asdict, is_dataclass, dataclass
from enum import Enum
from typing import Any, TypeVar, Callable, Union

from cms_rendner_sdfv.base.exceptions import TableSourceException

T = TypeVar('T')


class _CustomJSONEncoder(json.JSONEncoder):
    def default(self, obj: Any):
        if is_dataclass(obj):
            return asdict(obj)
        if isinstance(obj, Enum):
            return obj.name
        if hasattr(obj, 'as_dict') and callable(obj.as_dict):
            return obj.as_dict()
        return str(obj)


def to_json(data: Any, **kwargs) -> str:
    return json.dumps(data, **kwargs, cls=_CustomJSONEncoder)


@dataclass(frozen=True)
class ResultJsonString:
    value: str

    @classmethod
    def from_value(cls, result: Any):
        return cls(to_json(result))

    def __str__(self) -> str:
        return self.value

    __repr__ = __str__


@dataclass(frozen=True)
class ExceptionJsonString:
    value: str

    @classmethod
    def from_value(cls, exc: Exception):
        return cls(to_json(TableSourceException.from_exception(exc)))

    def __str__(self) -> str:
        return self.value

    __repr__ = __str__


def serialize_result_or_exception_to_json(
        func: Callable[..., T],
) -> Callable[..., Union[None, ResultJsonString, ExceptionJsonString]]:
    @functools.wraps(func)
    def wrapper(*args, **kwargs) -> Union[None, ResultJsonString, ExceptionJsonString]:
        try:
            result = func(*args, **kwargs)
            if result is None:
                return None
            return ResultJsonString.from_value(result)
        except Exception as exc:
            return ExceptionJsonString.from_value(exc)

    return wrapper
