from plugin_code.apply_args import ApplyArgs
from plugin_code.apply_map_args import ApplyMapArgs

# == copy after here ==
from typing import Tuple, Callable, cast, Optional
from pandas._typing import Axis
from pandas.io.formats.style import Styler


class ExportedStyle:
    def __init__(self, args: Tuple[Callable, tuple, dict]):
        self.__args = args

    def apply_func(self) -> Callable:
        return self.__args[0]

    def apply_args(self) -> tuple:
        return self.__args[1]

    def apply_args_func(self) -> Callable:
        return self.__args[1][0]

    def create_apply_args(self) -> ApplyArgs:
        return ApplyArgs(cast(Tuple[Callable[..., Styler], Optional[Axis], Optional[any]], self.__args[1]))

    def create_apply_map_args(self) -> ApplyMapArgs:
        return ApplyMapArgs(cast(Tuple[Callable[..., Styler], Optional[any]], self.__args[1]))

    def apply_kwargs(self) -> dict:
        return self.__args[2]
