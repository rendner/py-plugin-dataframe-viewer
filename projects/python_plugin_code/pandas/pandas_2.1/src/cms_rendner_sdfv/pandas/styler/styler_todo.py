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
import inspect
from dataclasses import dataclass
from functools import partial
from typing import Callable, Optional, Tuple, Union

from pandas._typing import Axis
from pandas.io.formats.style_render import Subset


@dataclass(frozen=True)
class MapArgs:
    style_func: Callable
    subset: Optional[Subset]

    @classmethod
    def from_tuple(cls, args: Tuple[Callable, Optional[Subset]]):
        return cls(args[0], args[1])

    @staticmethod
    def copy_with(style_func: Callable, subset: Optional[Subset]):
        return MapArgs(style_func, subset)

    def to_tuple(self) -> Tuple[Callable, Optional[Subset]]:
        return self.style_func, self.subset


@dataclass(frozen=True)
class ApplyArgs:
    style_func: Callable
    axis: Optional[Axis]
    subset: Optional[Subset]

    @classmethod
    def from_tuple(cls, args: Tuple[Callable, Optional[Axis], Optional[Subset]]):
        return cls(args[0], args[1], args[2])

    def copy_with(self, style_func: Callable, subset: Optional[Subset]):
        return ApplyArgs(style_func, self.axis, subset)

    def to_tuple(self) -> Tuple[Callable, Optional[Axis], Optional[Subset]]:
        return self.style_func, self.axis, self.subset

    def axis_is_index(self) -> bool:
        return self.axis == 'index' or self.axis == 0

    def axis_is_columns(self) -> bool:
        return self.axis == 'columns' or self.axis == 1


@dataclass(frozen=True)
class StylerTodo:
    index_in_org_styler: int
    apply_func: Callable
    apply_args: Union[ApplyArgs, MapArgs]
    style_func_kwargs: dict

    @classmethod
    def from_tuple(cls, index_in_org_styler: int, todo: Tuple[Callable, tuple, dict]):
        return cls(index_in_org_styler, todo[0], cls._to_apply_args(todo), todo[2])

    @staticmethod
    def _to_apply_args(todo: Tuple[Callable, tuple, dict]):
        if StylerTodo.is_map_tuple(todo):
            return MapArgs.from_tuple(todo[1])
        else:
            return ApplyArgs.from_tuple(todo[1])

    @classmethod
    def is_map_tuple(cls, todo: Tuple[Callable, tuple, dict]):
        return cls.__is_map_func(todo[0])

    def is_map(self) -> bool:
        return self.__is_map_func(self.apply_func)

    @staticmethod
    def __is_map_func(func: Callable) -> bool:
        return getattr(func, '__qualname__', '').startswith('Styler.map')

    def is_pandas_style_func(self) -> bool:
        func = self.apply_args.style_func
        if isinstance(func, partial):
            func = func.func
        inspect_result = inspect.getmodule(func)
        return False if inspect_result is None else inspect.getmodule(func).__name__ == 'pandas.io.formats.style'

    def should_provide_chunk_parent(self):
        sig = inspect.signature(self.apply_args.style_func)
        for param in sig.parameters.values():
            if param.name == "chunk_parent" or param.kind == inspect.Parameter.VAR_KEYWORD:
                return True
        return False

    def to_tuple(self) -> Tuple[Callable, tuple, dict]:
        return self.apply_func, self.apply_args.to_tuple(), self.style_func_kwargs


class StylerTodoBuilder:

    def __init__(self, source: StylerTodo):
        self.source: StylerTodo = source
        self.values: dict = {}

    def with_subset(self, subset: Optional[Subset]):
        self.values["subset"] = subset
        return self

    def with_style_func(self, style_func: Callable):
        self.values["style_func"] = style_func
        return self

    def with_style_func_kwargs(self, style_func_kwargs: dict):
        self.values["style_func_kwargs"] = style_func_kwargs
        return self

    def build(self) -> StylerTodo:
        return StylerTodo(
            self.source.index_in_org_styler,
            self.source.apply_func,
            self.source.apply_args.copy_with(
                style_func=self.values.get("style_func", self.source.apply_args.style_func),
                subset=self.values.get("subset", self.source.apply_args.subset),
            ),
            self.values.get("style_func_kwargs", self.source.style_func_kwargs),
        )
