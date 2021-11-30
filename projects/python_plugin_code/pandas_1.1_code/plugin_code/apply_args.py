

# == copy after here ==
from typing import Callable, Tuple, Optional
from pandas._typing import Axis
from pandas.io.formats.style import Styler


class ApplyArgs:

    def __init__(self, args: Tuple[Callable[..., Styler], Optional[Axis], Optional[any]]):
        self.__args = args

    def func(self) -> Callable[..., Styler]:
        return self.__args[0]

    def axis(self) -> Optional[Axis]:
        return self.__args[1]

    def subset(self) -> Optional[any]:
        return self.__args[2]
