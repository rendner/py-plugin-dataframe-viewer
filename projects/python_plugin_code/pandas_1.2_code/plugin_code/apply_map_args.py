
# == copy after here ==
from typing import Callable, Tuple, Optional
from pandas.io.formats.style import Styler


class ApplyMapArgs:

    def __init__(self, args: Tuple[Callable[..., Styler], Optional[any]]):
        self.__args = args

    def func(self) -> Callable[..., Styler]:
        return self.__args[0]

    def subset(self) -> Optional[any]:
        return self.__args[1]
