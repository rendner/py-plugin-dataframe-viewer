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

# == copy after here ==
import inspect
from functools import partial
from typing import Tuple, Callable, Optional, Union
from pandas._typing import Axis
from pandas.io.formats.style_render import Subset


class ApplyMapArgs:

    def __init__(self, style_func: Callable, subset: Optional[Subset]):
        self._style_func: Callable = style_func
        self._subset: Optional[Subset] = subset

    @property
    def style_func(self):
        return self._style_func

    @property
    def subset(self):
        return self._subset

    @classmethod
    def from_tuple(cls, args: Tuple[Callable, Optional[Subset]]):
        return cls(args[0], args[1])

    @staticmethod
    def copy_with(style_func: Callable, subset: Optional[Subset]):
        return ApplyMapArgs(style_func, subset)

    def to_tuple(self) -> Tuple[Callable, Optional[Subset]]:
        return self.style_func, self.subset


class ApplyArgs:

    def __init__(self, style_func: Callable, axis: Optional[Axis], subset: Optional[Subset]):
        self._style_func: Callable = style_func
        self._axis: Optional[Axis] = axis
        self._subset: Optional[Subset] = subset

    @property
    def style_func(self):
        return self._style_func

    @property
    def axis(self):
        return self._axis

    @property
    def subset(self):
        return self._subset

    @classmethod
    def from_tuple(cls, args: Tuple[Callable, Optional[Axis], Optional[Subset]]):
        return cls(args[0], args[1], args[2])

    def copy_with(self, style_func: Callable, subset: Optional[Subset]):
        return ApplyArgs(style_func, self.axis, subset)

    def to_tuple(self) -> Tuple[Callable, Optional[Axis], Optional[Subset]]:
        return self.style_func, self.axis, self.subset


class StylerTodo:
    def __init__(self, apply_func: Callable, apply_args: Union[ApplyArgs, ApplyMapArgs], style_func_kwargs: dict):
        self._apply_func: Callable = apply_func
        self._apply_args: Union[ApplyArgs, ApplyMapArgs] = apply_args
        self._style_func_kwargs: dict = style_func_kwargs

    @property
    def apply_func(self):
        return self._apply_func

    @property
    def apply_args(self):
        return self._apply_args

    @property
    def style_func_kwargs(self):
        return self._style_func_kwargs

    @classmethod
    def from_tuple(cls, todo: Tuple[Callable, tuple, dict]):
        return cls(todo[0], cls._to_apply_args(todo), todo[2])

    def builder(self):
        return StylerTodoBuilder(self)

    @staticmethod
    def _to_apply_args(todo: Tuple[Callable, tuple, dict]):
        if getattr(todo[0], '__qualname__', '').startswith('Styler.applymap'):
            return ApplyMapArgs.from_tuple(todo[1])
        else:
            return ApplyArgs.from_tuple(todo[1])

    def is_applymap_call(self) -> bool:
        return isinstance(self.apply_args, ApplyMapArgs)

    def is_apply_call(self) -> bool:
        return not self.is_applymap_call()

    def is_builtin_style_func(self) -> bool:
        func = self.apply_args.style_func
        if isinstance(func, partial):
            func = func.func
        inspect_result = inspect.getmodule(func)
        return False if inspect_result is None else inspect.getmodule(func).__name__ == 'pandas.io.formats.style'

    def get_style_func_name(self) -> str:
        func = self.apply_args.style_func
        if isinstance(func, partial):
            func = func.func
        return getattr(func, '__qualname__', '')

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
            self.source.apply_func,
            self.source.apply_args.copy_with(
                style_func=self.values.get("style_func", self.source.apply_args.style_func),
                subset=self.values.get("subset", self.source.apply_args.subset),
            ),
            self.values.get("style_func_kwargs", self.source.style_func_kwargs),
        )
