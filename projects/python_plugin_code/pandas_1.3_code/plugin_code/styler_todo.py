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

    def copy_with(self, style_func: Optional[Callable] = None, subset: Optional[Subset] = None):
        return ApplyMapArgs(
            self.style_func if style_func is None else style_func,
            self.subset if subset is None else subset,
        )

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

    def copy_with(self, style_func: Optional[Callable] = None, subset: Optional[Subset] = None):
        return ApplyArgs(
            self.style_func if style_func is None else style_func,
            self.axis,
            self.subset if subset is None else subset,
        )

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

    def copy_with(self,
                  apply_args_subset: Optional[Subset] = None,
                  style_func: Optional[Callable] = None,
                  style_func_kwargs: Optional[dict] = None,
                  ):
        return StylerTodo(
            self.apply_func,
            self.apply_args.copy_with(style_func=style_func, subset=apply_args_subset),
            dict(**self.style_func_kwargs) if style_func_kwargs is None else style_func_kwargs,
        )

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

    def to_tuple(self) -> Tuple[Callable, tuple, dict]:
        return self.apply_func, self.apply_args.to_tuple(), self.style_func_kwargs
